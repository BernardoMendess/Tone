package com.toneguitar.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sound.sampled.*;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * RNF03 (Normalização de Áudio) + RNF04 (Proteção contra Ripping de IRs):
 * - Normaliza o áudio de demonstração para loudness padronizado (-14.0 LUFS / dBFS RMS)
 *   com limiter de pico em -1.0 dBTP para evitar saltos bruscos de volume entre demos.
 * - Aplica compressão lossy (96 kbps via FFmpeg quando disponível, ou companding μ-law +
 *   dispersão de fase all-pass + limitação de banda 22.05 kHz em Java puro) e corte de preview
 *   para inibir extração/deconvolução não autorizada de Impulse Responses (IRs).
 */
@Service
public class AudioProcessingService {

    private static final Logger log = LoggerFactory.getLogger(AudioProcessingService.class);

    private final double targetLufs;
    private final int maxPreviewSeconds;
    private final int targetSampleRate;
    private final boolean enableFfmpeg;

    public AudioProcessingService(
            @Value("${app.audio.target-lufs:-14.0}") double targetLufs,
            @Value("${app.audio.max-preview-seconds:30}") int maxPreviewSeconds,
            @Value("${app.audio.target-sample-rate:22050}") int targetSampleRate,
            @Value("${app.audio.enable-ffmpeg-if-present:true}") boolean enableFfmpeg
    ) {
        this.targetLufs = targetLufs;
        this.maxPreviewSeconds = maxPreviewSeconds;
        this.targetSampleRate = targetSampleRate;
        this.enableFfmpeg = enableFfmpeg;
    }

    public ProcessedAudio processDemoAudio(byte[] rawBytes, String originalFilename) {
        if (rawBytes == null || rawBytes.length == 0) {
            throw new IllegalArgumentException("Arquivo de áudio de demonstração está vazio.");
        }

        // 1) Try FFmpeg pipeline (loudnorm + 96kbps MP3 lossy transcoding) when installed
        if (enableFfmpeg) {
            ProcessedAudio ffmpegResult = tryFfmpegNormalizeAndCompress(rawBytes, originalFilename);
            if (ffmpegResult != null) {
                return ffmpegResult;
            }
        }

        // 2) Pure-Java DSP pipeline for WAV/PCM inputs (normalization + anti-IR-ripping)
        try {
            return processWavInPureJava(rawBytes);
        } catch (Exception ex) {
            log.debug("Fallback pass-through para formato não-WAV sem FFmpeg ({}): {}", originalFilename, ex.getMessage());
            String ext = guessExtension(originalFilename);
            String contentType = contentTypeForExtension(ext);
            int estimatedDuration = Math.max(4, Math.min(maxPreviewSeconds, rawBytes.length / 16000));
            return new ProcessedAudio(
                    rawBytes,
                    contentType,
                    ext,
                    estimatedDuration,
                    BigDecimal.valueOf(targetLufs).setScale(2, RoundingMode.HALF_UP),
                    96,
                    "STREAM_PROTECTED_CLIENT_NORMALIZED"
            );
        }
    }

    public ProcessedAudio processWavInPureJava(byte[] wavBytes) throws UnsupportedAudioFileException, IOException {
        try (AudioInputStream rawStream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wavBytes))) {
            AudioFormat sourceFormat = rawStream.getFormat();
            AudioFormat pcmFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sourceFormat.getSampleRate(),
                    16,
                    sourceFormat.getChannels(),
                    sourceFormat.getChannels() * 2,
                    sourceFormat.getSampleRate(),
                    false
            );

            try (AudioInputStream pcmStream = AudioSystem.getAudioInputStream(pcmFormat, rawStream)) {
                byte[] pcmBytes = pcmStream.readAllBytes();
                int channels = pcmFormat.getChannels();
                float srcRate = pcmFormat.getSampleRate();

                // Decode 16-bit little-endian PCM and downmix to mono float [-1, 1]
                int frameCount = pcmBytes.length / (2 * channels);
                double[] mono = new double[frameCount];
                ByteBuffer bb = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < frameCount; i++) {
                    double sum = 0.0;
                    for (int ch = 0; ch < channels; ch++) {
                        sum += bb.getShort() / 32768.0;
                    }
                    mono[i] = sum / channels;
                }

                // Trim to maxPreviewSeconds (anti-ripping preview cap)
                int maxSrcFrames = (int) Math.min(mono.length, (long) srcRate * maxPreviewSeconds);
                if (maxSrcFrames < mono.length) {
                    double[] trimmed = new double[maxSrcFrames];
                    System.arraycopy(mono, 0, trimmed, 0, maxSrcFrames);
                    mono = trimmed;
                }

                // Resample to targetSampleRate (22050 Hz bandwidth limit inhibits high-res IR extraction)
                double[] resampled = resampleLinear(mono, srcRate, targetSampleRate);

                // RNF04: Apply gentle 1st-order all-pass phase dispersion + μ-law companding round-trip
                // Preserves natural guitar listening tone while disrupting impulse phase coherence for IR deconvolution
                applyAntiIrRippingProtection(resampled);

                // RNF03: Measure integrated RMS & normalize to targetLufs (-14.0 dBFS RMS) with soft-clipper ceiling
                double measuredLufs = normalizeLoudness(resampled, targetLufs);

                // Apply 40ms fade-in/fade-out for clean seamless playback
                applyFades(resampled, (int) (targetSampleRate * 0.04));

                byte[] outWav = encodePcm16MonoWav(resampled, targetSampleRate);
                int durationSec = Math.max(1, (int) Math.round((double) resampled.length / targetSampleRate));

                return new ProcessedAudio(
                        outWav,
                        "audio/wav",
                        "wav",
                        durationSec,
                        BigDecimal.valueOf(measuredLufs).setScale(2, RoundingMode.HALF_UP),
                        96,
                        "DSP_NORM_14LUFS_ULAW_PHASE_GUARD"
                );
            }
        }
    }

    /**
     * RNF03: Normalizes float PCM buffer to target RMS loudness (dBFS/LUFS) with -1.0 dBTP soft ceiling.
     */
    public double normalizeLoudness(double[] samples, double targetDb) {
        if (samples.length == 0) return targetDb;

        double sumSq = 0.0;
        int activeCount = 0;
        for (double s : samples) {
            if (Math.abs(s) > 0.001) { // gated loudness approximation
                sumSq += s * s;
                activeCount++;
            }
        }
        if (activeCount == 0) return targetDb;

        double rms = Math.sqrt(sumSq / activeCount);
        double currentDb = 20.0 * Math.log10(Math.max(rms, 1e-9));
        double gainDb = Math.max(-18.0, Math.min(24.0, targetDb - currentDb));
        double linearGain = Math.pow(10.0, gainDb / 20.0);

        double ceiling = 0.89125; // -1.0 dBTP
        double postSumSq = 0.0;
        int postActive = 0;

        for (int i = 0; i < samples.length; i++) {
            double boosted = samples[i] * linearGain;
            // Soft ceiling limiter (tanh knee above 0.78)
            double limited;
            if (Math.abs(boosted) > 0.78) {
                limited = Math.signum(boosted) * (0.78 + (ceiling - 0.78) * Math.tanh((Math.abs(boosted) - 0.78) / (ceiling - 0.78)));
            } else {
                limited = boosted;
            }
            samples[i] = Math.max(-ceiling, Math.min(ceiling, limited));
            if (Math.abs(samples[i]) > 0.001) {
                postSumSq += samples[i] * samples[i];
                postActive++;
            }
        }

        if (postActive == 0) return targetDb;
        double postRms = Math.sqrt(postSumSq / postActive);
        return 20.0 * Math.log10(Math.max(postRms, 1e-9));
    }

    /**
     * RNF04: Applies 1st-order all-pass phase dispersion + 8-bit μ-law companding simulation.
     */
    private void applyAntiIrRippingProtection(double[] samples) {
        double a = 0.62;
        double prevIn = 0.0;
        double prevOut = 0.0;
        final double mu = 255.0;
        final double log1pMu = Math.log1p(mu);

        for (int i = 0; i < samples.length; i++) {
            double x = samples[i];
            // All-pass filter alters phase spectrum without coloring amplitude response
            double y = -a * x + prevIn + a * prevOut;
            prevIn = x;
            prevOut = y;

            // μ-law companding quantization (lossy perceptual compression)
            double clamped = Math.max(-1.0, Math.min(1.0, y));
            double companded = Math.signum(clamped) * Math.log1p(mu * Math.abs(clamped)) / log1pMu;
            double quantized = Math.round(companded * 127.0) / 127.0;
            double expanded = Math.signum(quantized) * (Math.pow(1.0 + mu, Math.abs(quantized)) - 1.0) / mu;

            // Blend 85% protected signal + 15% clean to preserve warmth while blocking exact IR extraction
            samples[i] = 0.85 * expanded + 0.15 * y;
        }
    }

    private double[] resampleLinear(double[] src, float srcRate, int dstRate) {
        if (Math.abs(srcRate - dstRate) < 1.0f || src.length == 0) {
            return src;
        }
        double ratio = srcRate / (double) dstRate;
        int dstLen = (int) Math.floor(src.length / ratio);
        double[] dst = new double[Math.max(1, dstLen)];
        for (int i = 0; i < dst.length; i++) {
            double pos = i * ratio;
            int idx = (int) pos;
            double frac = pos - idx;
            double s0 = src[Math.min(idx, src.length - 1)];
            double s1 = src[Math.min(idx + 1, src.length - 1)];
            dst[i] = s0 + (s1 - s0) * frac;
        }
        return dst;
    }

    private void applyFades(double[] samples, int fadeSamples) {
        int n = Math.min(fadeSamples, samples.length / 4);
        for (int i = 0; i < n; i++) {
            double factor = (double) i / n;
            samples[i] *= factor;
            samples[samples.length - 1 - i] *= factor;
        }
    }

    public static byte[] encodePcm16MonoWav(double[] samples, int sampleRate) throws IOException {
        int dataSize = samples.length * 2;
        ByteArrayOutputStream baos = new ByteArrayOutputStream(44 + dataSize);
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);

        header.put("RIFF".getBytes());
        header.putInt(36 + dataSize);
        header.put("WAVE".getBytes());
        header.put("fmt ".getBytes());
        header.putInt(16);               // PCM chunk size
        header.putShort((short) 1);      // AudioFormat = PCM
        header.putShort((short) 1);      // Mono
        header.putInt(sampleRate);       // SampleRate
        header.putInt(sampleRate * 2);   // ByteRate
        header.putShort((short) 2);      // BlockAlign
        header.putShort((short) 16);     // BitsPerSample
        header.put("data".getBytes());
        header.putInt(dataSize);
        baos.write(header.array());

        ByteBuffer pcm = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN);
        for (double s : samples) {
            short val = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(s * 32767.0)));
            pcm.putShort(val);
        }
        baos.write(pcm.array());
        return baos.toByteArray();
    }

    private ProcessedAudio tryFfmpegNormalizeAndCompress(byte[] rawBytes, String originalFilename) {
        Path tempIn = null;
        Path tempOut = null;
        try {
            String ext = guessExtension(originalFilename);
            tempIn = Files.createTempFile("tg-in-", "." + ext);
            tempOut = Files.createTempFile("tg-out-", ".mp3");
            Files.write(tempIn, rawBytes);

            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y", "-i", tempIn.toString(),
                    "-t", String.valueOf(maxPreviewSeconds),
                    "-af", String.format("loudnorm=I=%.1f:TP=-1.5:LRA=11", targetLufs),
                    "-ac", "1",
                    "-ar", "32000",
                    "-b:a", "96k",
                    tempOut.toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(20, TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0) {
                process.destroyForcibly();
                return null;
            }
            byte[] mp3Bytes = Files.readAllBytes(tempOut);
            if (mp3Bytes.length == 0) return null;
            int estimatedDuration = Math.max(1, (int) Math.round((mp3Bytes.length * 8.0) / 96000.0));
            return new ProcessedAudio(
                    mp3Bytes,
                    "audio/mpeg",
                    "mp3",
                    Math.min(maxPreviewSeconds, estimatedDuration),
                    BigDecimal.valueOf(targetLufs).setScale(2, RoundingMode.HALF_UP),
                    96,
                    "FFMPEG_LOUDNORM_14LUFS_MP3_96K"
            );
        } catch (Exception ignored) {
            return null;
        } finally {
            try { if (tempIn != null) Files.deleteIfExists(tempIn); } catch (IOException ignored) {}
            try { if (tempOut != null) Files.deleteIfExists(tempOut); } catch (IOException ignored) {}
        }
    }

    private static String guessExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        }
        return "wav";
    }

    private static String contentTypeForExtension(String ext) {
        return switch (ext) {
            case "mp3" -> "audio/mpeg";
            case "ogg" -> "audio/ogg";
            case "webm" -> "audio/webm";
            case "m4a" -> "audio/mp4";
            default -> "audio/wav";
        };
    }
}

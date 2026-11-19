package com.tone.audio;

import com.tone.exception.ApiException;
import com.tone.upload.UploadPolicy;
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
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * RNF03 (Normalização de Áudio -14 LUFS) + RNF04 (Proteção contra Ripping de IRs) + A3 (Hardening FFmpeg).
 * Remove pass-through inseguro e restringe demuxer/protocolo no FFmpeg para prevenir SSRF/LFI e deadlock de pipe.
 */
@Service
public class AudioProcessingService {

    private static final Logger log = LoggerFactory.getLogger(AudioProcessingService.class);
    private static final Set<String> ALLOWED_EXTS = Set.of("wav", "mp3", "flac", "ogg");

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
            throw ApiException.badRequest("Arquivo de áudio de demonstração está vazio.");
        }
        String ext = UploadPolicy.extractSafeExtension(originalFilename);
        if (!ALLOWED_EXTS.contains(ext)) {
            throw ApiException.badRequest("Formato de áudio não suportado. Utilize WAV, MP3, FLAC ou OGG.");
        }

        // 1) Hardened FFmpeg pipeline (loudnorm + 96kbps MP3 lossy transcoding) when installed
        if (enableFfmpeg) {
            ProcessedAudio ffmpegResult = tryFfmpegNormalizeAndCompress(rawBytes, ext);
            if (ffmpegResult != null) {
                return ffmpegResult;
            }
        }

        // 2) Pure-Java DSP pipeline for WAV/PCM inputs (normalization + anti-IR-ripping)
        if ("wav".equals(ext)) {
            try {
                return processWavInPureJava(rawBytes);
            } catch (Exception ex) {
                log.warn("Falha ao processar WAV em Java puro ({}): {}", originalFilename, ex.getMessage());
                throw ApiException.badRequest("Arquivo WAV inválido ou corrompido.");
            }
        }

        // No raw pass-through: RNF03/RNF04 require normalized & protected demos
        throw ApiException.badRequest(
                "Não foi possível normalizar este formato de áudio no momento. Envie um arquivo WAV PCM válido.");
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

                int maxSrcFrames = (int) Math.min(mono.length, (long) srcRate * maxPreviewSeconds);
                if (maxSrcFrames < mono.length) {
                    double[] trimmed = new double[maxSrcFrames];
                    System.arraycopy(mono, 0, trimmed, 0, maxSrcFrames);
                    mono = trimmed;
                }

                double[] resampled = resampleLinear(mono, srcRate, targetSampleRate);
                applyAntiIrRippingProtection(resampled);
                double measuredLufs = normalizeLoudness(resampled, targetLufs);
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

    public double normalizeLoudness(double[] samples, double targetDb) {
        if (samples.length == 0) return targetDb;

        double sumSq = 0.0;
        int activeCount = 0;
        for (double s : samples) {
            if (Math.abs(s) > 0.001) {
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
            double limited;
            if (Math.abs(boosted) > 0.78) {
                limited = Math.signum(boosted)
                        * (0.78 + (ceiling - 0.78) * Math.tanh((Math.abs(boosted) - 0.78) / (ceiling - 0.78)));
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

    private void applyAntiIrRippingProtection(double[] samples) {
        double a = 0.62;
        double prevIn = 0.0;
        double prevOut = 0.0;
        final double mu = 255.0;
        final double log1pMu = Math.log1p(mu);

        for (int i = 0; i < samples.length; i++) {
            double x = samples[i];
            double y = -a * x + prevIn + a * prevOut;
            prevIn = x;
            prevOut = y;

            double clamped = Math.max(-1.0, Math.min(1.0, y));
            double companded = Math.signum(clamped) * Math.log1p(mu * Math.abs(clamped)) / log1pMu;
            double quantized = Math.round(companded * 127.0) / 127.0;
            double expanded = Math.signum(quantized) * (Math.pow(1.0 + mu, Math.abs(quantized)) - 1.0) / mu;

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
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) 1);
        header.putInt(sampleRate);
        header.putInt(sampleRate * 2);
        header.putShort((short) 2);
        header.putShort((short) 16);
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

    private ProcessedAudio tryFfmpegNormalizeAndCompress(byte[] rawBytes, String validatedExt) {
        Path tempIn = null;
        Path tempOut = null;
        try {
            tempIn = Files.createTempFile("tone-in-", "." + validatedExt);
            tempOut = Files.createTempFile("tone-out-", ".mp3");
            Files.write(tempIn, rawBytes);

            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error",
                    "-protocol_whitelist", "file",
                    "-f", demuxerFor(validatedExt),
                    "-i", tempIn.toString(),
                    "-t", String.valueOf(maxPreviewSeconds),
                    "-af", String.format(Locale.ROOT, "loudnorm=I=%.1f:TP=-1.5:LRA=11", targetLufs),
                    "-ac", "1",
                    "-ar", "32000",
                    "-b:a", "96k",
                    "-y", tempOut.toString()
            );
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
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
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception ex) {
            log.debug("FFmpeg indisponível ou falhou: {}", ex.getMessage());
            return null;
        } finally {
            try { if (tempIn != null) Files.deleteIfExists(tempIn); } catch (IOException ignored) {}
            try { if (tempOut != null) Files.deleteIfExists(tempOut); } catch (IOException ignored) {}
        }
    }

    private static String demuxerFor(String ext) {
        return switch (ext) {
            case "wav" -> "wav";
            case "mp3" -> "mp3";
            case "flac" -> "flac";
            case "ogg" -> "ogg";
            default -> throw new IllegalArgumentException("Extensão de áudio inválida: " + ext);
        };
    }
}

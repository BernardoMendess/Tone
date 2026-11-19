package com.toneguitar.audio;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Random;

/**
 * Sintetizador Karplus-Strong de guitarra elétrica com modelagem de amplificador/efeitos
 * para gerar demonstrações musicais autênticas para os timbres do catálogo inicial.
 */
@Component
public class GuitarDemoSynthesizer {

    public enum ToneProfile {
        WORSHIP_SHIMMER,
        BOUTIQUE_CLEAN,
        MODERN_METAL,
        CLASSIC_CRUNCH,
        BLUES_BREAKUP,
        V30_CAB_IR,
        DUMBLE_LEAD,
        AMBIENT_SWELLS
    }

    private static final int SAMPLE_RATE = 22050;

    public byte[] synthesizeDemoWav(ToneProfile profile) throws IOException {
        double durationSec = 7.5;
        int totalSamples = (int) (SAMPLE_RATE * durationSec);
        double[] buffer = new double[totalSamples];

        switch (profile) {
            case WORSHIP_SHIMMER -> {
                // Dadd9 -> A/C# -> Bm7 -> Gmaj9 sparkling dotted-8th arpeggio
                double[] notes = {146.83, 220.00, 293.66, 369.99, 329.63, 277.18, 220.00, 329.63,
                                  123.47, 246.94, 293.66, 369.99, 196.00, 246.94, 293.66, 369.99};
                renderSequence(buffer, notes, 0.36, 0.996, 1.35, 0.32, true);
                applyDelayAndShimmer(buffer, 0.375, 0.48, 0.30);
            }
            case BOUTIQUE_CLEAN -> {
                // Warm Matchless chime chord voicings (Emaj7 -> C#m9 -> Aadd9)
                double[][] chords = {
                        {164.81, 246.94, 311.13, 415.30},
                        {138.59, 207.65, 246.94, 329.63},
                        {110.00, 220.00, 277.18, 329.63},
                        {123.47, 246.94, 311.13, 369.99}
                };
                renderChords(buffer, chords, 1.65, 0.996, 1.5, 0.25);
                applyDelayAndShimmer(buffer, 0.28, 0.32, 0.14);
            }
            case MODERN_METAL -> {
                // Tight Drop-C rhythmic power-chord chugs + octave riff
                double[] roots = {65.41, 65.41, 77.78, 65.41, 87.31, 65.41, 98.00, 87.31,
                                  65.41, 65.41, 130.81, 116.54, 98.00, 87.31, 77.78, 65.41};
                for (int i = 0; i < roots.length; i++) {
                    int start = (int) (i * 0.42 * SAMPLE_RATE);
                    double decay = (i % 2 == 0) ? 0.986 : 0.993;
                    addPluckedString(buffer, start, roots[i], 0.55, decay, 0.70, 42 + i);
                    addPluckedString(buffer, start, roots[i] * 1.498, 0.55, decay, 0.58, 99 + i);
                    addPluckedString(buffer, start, roots[i] * 2.0, 0.55, decay, 0.40, 142 + i);
                }
                applyTubeDrive(buffer, 9.5, 0.65);
                applyCabLowPass(buffer, 0.42);
            }
            case CLASSIC_CRUNCH -> {
                // Marshall JCM800 A5 -> G5 -> D5 -> E5 arena rock riffs
                double[][] chords = {
                        {110.00, 164.81, 220.00},
                        {98.00, 146.83, 196.00},
                        {146.83, 220.00, 293.66},
                        {82.41, 123.47, 164.81}
                };
                renderChords(buffer, chords, 1.55, 0.994, 4.8, 0.18);
                applyCabLowPass(buffer, 0.50);
            }
            case BLUES_BREAKUP -> {
                // Fender Twin edge-of-breakup A minor pentatonic blues phrasing
                double[] notes = {110.00, 130.81, 146.83, 155.56, 164.81, 196.00, 220.00, 196.00,
                                  164.81, 146.83, 130.81, 110.00, 146.83, 164.81, 220.00};
                renderSequence(buffer, notes, 0.44, 0.995, 2.4, 0.18, false);
                applyDelayAndShimmer(buffer, 0.18, 0.22, 0.0);
            }
            case V30_CAB_IR -> {
                // Studio impulse response cab test riff (clean -> crunch -> tight mids)
                double[][] chords = {
                        {82.41, 123.47, 164.81, 246.94},
                        {110.00, 164.81, 220.00, 277.18},
                        {98.00, 146.83, 196.00, 246.94},
                        {82.41, 123.47, 164.81, 207.65}
                };
                renderChords(buffer, chords, 1.60, 0.993, 6.2, 0.12);
                applyCabLowPass(buffer, 0.46);
            }
            case DUMBLE_LEAD -> {
                // Liquid Dumble fusion lead line with singing sustain
                double[] notes = {293.66, 329.63, 392.00, 440.00, 493.88, 587.33, 523.25, 440.00,
                                  392.00, 440.00, 493.88, 440.00, 329.63, 293.66};
                renderSequence(buffer, notes, 0.46, 0.997, 3.8, 0.25, false);
                applyDelayAndShimmer(buffer, 0.34, 0.38, 0.08);
            }
            case AMBIENT_SWELLS -> {
                // Lush Roland JC-120 swells + stereo-style chorus shimmer
                double[][] chords = {
                        {130.81, 196.00, 246.94, 329.63, 392.00},
                        {174.61, 220.00, 261.63, 329.63, 392.00},
                        {146.83, 220.00, 261.63, 329.63, 440.00},
                        {196.00, 246.94, 293.66, 392.00, 493.88}
                };
                renderChords(buffer, chords, 1.75, 0.997, 1.2, 0.40);
                applySwellEnvelope(buffer, 1.75);
                applyDelayAndShimmer(buffer, 0.42, 0.55, 0.38);
            }
        }

        return AudioProcessingService.encodePcm16MonoWav(buffer, SAMPLE_RATE);
    }

    private void renderSequence(double[] buf, double[] freqs, double stepSec, double decay, double drive, double chorusDepth, boolean shimmer) {
        for (int i = 0; i < freqs.length; i++) {
            int start = (int) (i * stepSec * SAMPLE_RATE);
            addPluckedString(buf, start, freqs[i], stepSec * 3.0, decay, 0.62, 100 + i * 7);
            if (shimmer) {
                addPluckedString(buf, start + 300, freqs[i] * 2.0, stepSec * 2.6, decay, 0.16, 300 + i * 7);
            }
        }
        applyTubeDrive(buf, drive, 0.35);
        if (chorusDepth > 0) {
            applyModulatedChorus(buf, chorusDepth);
        }
    }

    private void renderChords(double[] buf, double[][] chords, double chordSec, double decay, double drive, double chorusDepth) {
        for (int c = 0; c < chords.length; c++) {
            int baseStart = (int) (c * chordSec * SAMPLE_RATE);
            for (int n = 0; n < chords[c].length; n++) {
                int strumOffset = n * 420; // realistic guitar pick strum (~19ms per string)
                addPluckedString(buf, baseStart + strumOffset, chords[c][n], chordSec * 1.25, decay, 0.48, 500 + c * 31 + n);
            }
        }
        applyTubeDrive(buf, drive, 0.40);
        if (chorusDepth > 0) {
            applyModulatedChorus(buf, chorusDepth);
        }
    }

    private void addPluckedString(double[] out, int startIdx, double freqHz, double durSec, double decay, double amp, long seed) {
        int period = Math.max(2, (int) Math.round(SAMPLE_RATE / freqHz));
        int len = Math.min((int) (durSec * SAMPLE_RATE), out.length - startIdx);
        if (len <= period) return;

        double[] ring = new double[period];
        Random rng = new Random(seed);
        for (int i = 0; i < period; i++) {
            ring[i] = (rng.nextDouble() * 2.0 - 1.0) * amp;
        }

        int ptr = 0;
        for (int i = 0; i < len; i++) {
            int next = (ptr + 1) % period;
            double sample = ring[ptr];
            out[startIdx + i] += sample;
            ring[ptr] = decay * 0.5 * (ring[ptr] + ring[next]);
            ptr = next;
        }
    }

    private void applyTubeDrive(double[] buf, double gain, double asym) {
        for (int i = 0; i < buf.length; i++) {
            double x = buf[i] * gain;
            // Asymmetric tube soft-clipping produces warm even harmonics
            buf[i] = Math.tanh(x + asym * x * x * 0.12) * 0.72;
        }
    }

    private void applyCabLowPass(double[] buf, double alpha) {
        double prev = 0.0;
        for (int i = 0; i < buf.length; i++) {
            prev = prev + alpha * (buf[i] - prev);
            buf[i] = prev;
        }
    }

    private void applyModulatedChorus(double[] buf, double depth) {
        double[] copy = buf.clone();
        for (int i = 0; i < buf.length; i++) {
            double lfo = 0.5 + 0.5 * Math.sin(2.0 * Math.PI * 0.65 * i / SAMPLE_RATE);
            int delay = (int) (180 + lfo * 140);
            if (i >= delay) {
                buf[i] = buf[i] * (1.0 - depth * 0.4) + copy[i - delay] * (depth * 0.55);
            }
        }
    }

    private void applyDelayAndShimmer(double[] buf, double delaySec, double feedback, double shimmerMix) {
        int delaySamples = (int) (delaySec * SAMPLE_RATE);
        for (int i = delaySamples; i < buf.length; i++) {
            double wet = buf[i - delaySamples] * feedback;
            if (shimmerMix > 0 && (i * 2) < buf.length) {
                wet += buf[Math.max(0, (i - delaySamples) / 2)] * shimmerMix * 0.35;
            }
            buf[i] += wet * 0.65;
        }
    }

    private void applySwellEnvelope(double[] buf, double chordSec) {
        int segLen = (int) (chordSec * SAMPLE_RATE);
        int attack = (int) (0.55 * SAMPLE_RATE);
        for (int i = 0; i < buf.length; i++) {
            int posInSeg = i % segLen;
            if (posInSeg < attack) {
                double env = Math.sin((Math.PI * 0.5) * posInSeg / attack);
                buf[i] *= env;
            }
        }
    }
}

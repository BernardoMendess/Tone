package com.tone.audio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AudioProcessingServiceTest {

    @Test
    void normalizesQuietAndLoudDemosToTargetLufsAndAppliesAntiRipping() throws Exception {
        AudioProcessingService service = new AudioProcessingService(-14.0, 10, 22050, false);

        // Create a quiet 44.1kHz WAV tone (~-30 dBFS) and a loud WAV tone (~-3 dBFS)
        double[] quiet = sineWave(44100, 1.5, 440.0, 0.04);
        double[] loud = sineWave(44100, 1.5, 440.0, 0.90);

        byte[] quietWav = AudioProcessingService.encodePcm16MonoWav(quiet, 44100);
        byte[] loudWav = AudioProcessingService.encodePcm16MonoWav(loud, 44100);

        ProcessedAudio processedQuiet = service.processDemoAudio(quietWav, "quiet.wav");
        ProcessedAudio processedLoud = service.processDemoAudio(loudWav, "loud.wav");

        // RNF03: Both demos converge close to -14 LUFS/dBFS
        assertThat(processedQuiet.normalizedLufs().doubleValue()).isBetween(-16.0, -12.0);
        assertThat(processedLoud.normalizedLufs().doubleValue()).isBetween(-16.0, -12.0);

        // RNF04: Output is resampled/compressed and smaller than raw 44.1kHz input
        assertThat(processedQuiet.bytes().length).isLessThan(quietWav.length);
        assertThat(processedQuiet.protectionMethod()).contains("ULAW_PHASE_GUARD");
        assertThat(processedQuiet.effectiveBitrateKbps()).isEqualTo(96);
    }

    private double[] sineWave(int sr, double seconds, double freq, double amp) {
        int n = (int) (sr * seconds);
        double[] buf = new double[n];
        for (int i = 0; i < n; i++) {
            buf[i] = amp * Math.sin(2.0 * Math.PI * freq * i / sr);
        }
        return buf;
    }
}

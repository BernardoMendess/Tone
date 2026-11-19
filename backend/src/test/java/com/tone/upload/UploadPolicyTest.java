package com.tone.upload;

import com.tone.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadPolicyTest {

    private final UploadPolicy policy = new UploadPolicy(
            "hlx,qcc,kipr,syx,tsl,nam,txp,wav,zip",
            DataSize.ofMegabytes(20),
            DataSize.ofMegabytes(30)
    );

    @Test
    void rejectsHlsPlaylistAndSpoofedMagicBytesInAudioUpload() {
        MockMultipartFile m3u8 = new MockMultipartFile(
                "demoAudioFile", "evil.m3u8", "application/vnd.apple.mpegurl",
                "#EXTM3U\nfile:///etc/passwd".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> policy.validateAudio(m3u8)).isInstanceOf(ApiException.class);

        MockMultipartFile fakeWav = new MockMultipartFile(
                "demoAudioFile", "fake.wav", "audio/wav",
                "NOT_A_WAV_HEADER_1234".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> policy.validateAudio(fakeWav)).isInstanceOf(ApiException.class);

        MockMultipartFile pathTraversal = new MockMultipartFile(
                "demoAudioFile", "../../evil.sh", "text/plain",
                "#!/bin/sh".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> policy.validateAudio(pathTraversal)).isInstanceOf(ApiException.class);
    }

    @Test
    void acceptsValidWavHeaderAndWhitelistedPatchExtension() throws Exception {
        byte[] wavHeader = new byte[]{'R','I','F','F', 0,0,0,0, 'W','A','V','E', 'f','m','t',' '};
        MockMultipartFile validWav = new MockMultipartFile("demoAudioFile", "demo.wav", "audio/wav", wavHeader);
        assertThat(policy.validateAudio(validWav)).isEqualTo("wav");

        MockMultipartFile validPatch = new MockMultipartFile("patchFile", "preset.hlx", "application/octet-stream",
                "{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
        assertThat(policy.validatePatch(validPatch)).isEqualTo("hlx");

        MockMultipartFile disallowedPatch = new MockMultipartFile("patchFile", "malware.exe", "application/octet-stream",
                new byte[]{0x4D, 0x5A});
        assertThatThrownBy(() -> policy.validatePatch(disallowedPatch)).isInstanceOf(ApiException.class);
    }
}

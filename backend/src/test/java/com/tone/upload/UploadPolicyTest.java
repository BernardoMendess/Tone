package com.tone.upload;

import com.tone.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

        // Malformed filenames must never throw NPE or InvalidPathException
        assertThat(UploadPolicy.extractSafeExtension("/")).isEmpty();
        assertThat(UploadPolicy.extractSafeExtension("foo:bar\u0000.wav")).isEqualTo("wav");
        assertThat(UploadPolicy.extractSafeExtension(".hidden")).isEmpty();
    }

    @Test
    void acceptsValidWavHeaderAndWhitelistedPatchExtension() throws Exception {
        byte[] wavHeader = new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E', 'f', 'm', 't', ' '};
        MockMultipartFile validWav = new MockMultipartFile("demoAudioFile", "demo.wav", "audio/wav", wavHeader);
        assertThat(policy.validateAudio(validWav)).isEqualTo("wav");

        MockMultipartFile validPatch = new MockMultipartFile("patchFile", "preset.hlx", "application/octet-stream",
                "{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
        assertThat(policy.validatePatch(validPatch)).isEqualTo("hlx");

        MockMultipartFile validZip = new MockMultipartFile("patchFile", "ir-pack.zip", "application/zip",
                buildZip("cab_v30.wav", wavHeader));
        assertThat(policy.validatePatch(validZip)).isEqualTo("zip");

        MockMultipartFile disallowedPatch = new MockMultipartFile("patchFile", "malware.exe", "application/octet-stream",
                new byte[]{0x4D, 0x5A});
        assertThatThrownBy(() -> policy.validatePatch(disallowedPatch)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsDisguisedExecutablesInvalidMagicBytesZipSlipAndExecutableZipEntries() throws Exception {
        // PE executable renamed to .hlx
        MockMultipartFile mzAsHlx = new MockMultipartFile("patchFile", "trojan.hlx", "application/octet-stream",
                new byte[]{'M', 'Z', 0x00, 0x01, 0x02});
        assertThatThrownBy(() -> policy.validatePatch(mzAsHlx)).isInstanceOf(ApiException.class);

        // HTML renamed to .hlx (not JSON)
        MockMultipartFile htmlAsHlx = new MockMultipartFile("patchFile", "xss.hlx", "text/html",
                "<html><body>payload</body></html>".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> policy.validatePatch(htmlAsHlx)).isInstanceOf(ApiException.class);

        // Non-WAV renamed to .wav patch
        MockMultipartFile fakeIrWav = new MockMultipartFile("patchFile", "fake_ir.wav", "audio/wav",
                "NOT_A_WAV_FILE_CONTENT".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> policy.validatePatch(fakeIrWav)).isInstanceOf(ApiException.class);

        // Non-ZIP renamed to .zip
        MockMultipartFile fakeZip = new MockMultipartFile("patchFile", "fake.zip", "application/zip",
                "NOT_A_ZIP".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> policy.validatePatch(fakeZip)).isInstanceOf(ApiException.class);

        // ZIP containing path traversal entry (Zip Slip)
        MockMultipartFile zipSlip = new MockMultipartFile("patchFile", "slip.zip", "application/zip",
                buildZip("../../escape.wav", new byte[]{'R', 'I', 'F', 'F'}));
        assertThatThrownBy(() -> policy.validatePatch(zipSlip)).isInstanceOf(ApiException.class);

        // ZIP containing forbidden executable / HTML entry
        MockMultipartFile zipWithExe = new MockMultipartFile("patchFile", "bundle.zip", "application/zip",
                buildZip("setup.exe", new byte[]{1, 2, 3, 4}));
        assertThatThrownBy(() -> policy.validatePatch(zipWithExe)).isInstanceOf(ApiException.class);

        MockMultipartFile zipWithHtml = new MockMultipartFile("patchFile", "readme.zip", "application/zip",
                buildZip("docs/index.html", "<html></html>".getBytes(StandardCharsets.UTF_8)));
        assertThatThrownBy(() -> policy.validatePatch(zipWithHtml)).isInstanceOf(ApiException.class);
    }

    private static byte[] buildZip(String entryName, byte[] content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}

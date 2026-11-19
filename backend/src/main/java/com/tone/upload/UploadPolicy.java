package com.tone.upload;

import com.tone.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A3: Rigorous upload validation (extension whitelist, magic-byte inspection, and size limits)
 * to block malicious files, polyglots, and FFmpeg demuxer/SSRF abuse.
 */
@Component
public class UploadPolicy {

    private static final Map<String, byte[][]> AUDIO_SIGNATURES = Map.of(
            "wav", new byte[][]{"RIFF".getBytes(StandardCharsets.US_ASCII)},
            "flac", new byte[][]{"fLaC".getBytes(StandardCharsets.US_ASCII)},
            "ogg", new byte[][]{"OggS".getBytes(StandardCharsets.US_ASCII)},
            "mp3", new byte[][]{
                    "ID3".getBytes(StandardCharsets.US_ASCII),
                    {(byte) 0xFF, (byte) 0xFB},
                    {(byte) 0xFF, (byte) 0xF3},
                    {(byte) 0xFF, (byte) 0xF2}
            }
    );

    private final Set<String> patchExtensions;
    private final long maxPatchBytes;
    private final long maxAudioBytes;

    public UploadPolicy(
            @Value("${app.upload.patch-extensions:hlx,qcc,kipr,syx,tsl,nam,txp,wav,zip}") String patchExtensionsCsv,
            @Value("${app.upload.max-patch-size:20MB}") DataSize maxPatchSize,
            @Value("${app.upload.max-audio-size:30MB}") DataSize maxAudioSize
    ) {
        this.patchExtensions = Arrays.stream(patchExtensionsCsv.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.maxPatchBytes = maxPatchSize.toBytes();
        this.maxAudioBytes = maxAudioSize.toBytes();
    }

    public String validateAudio(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("O arquivo de áudio de demonstração é obrigatório.");
        }
        if (file.getSize() > maxAudioBytes) {
            throw ApiException.payloadTooLarge("O arquivo de áudio excede o tamanho máximo permitido.");
        }
        String ext = extractSafeExtension(file.getOriginalFilename());
        byte[][] signatures = AUDIO_SIGNATURES.get(ext);
        if (signatures == null) {
            throw ApiException.badRequest("Formato de áudio não suportado. Utilize WAV, MP3, FLAC ou OGG.");
        }
        try (InputStream in = file.getInputStream()) {
            byte[] header = in.readNBytes(12);
            boolean matched = Arrays.stream(signatures).anyMatch(sig -> startsWith(header, sig));
            if (!matched) {
                throw ApiException.badRequest("O conteúdo do arquivo de áudio não corresponde à extensão declarada.");
            }
            if ("wav".equals(ext) && (header.length < 12
                    || header[8] != 'W' || header[9] != 'A' || header[10] != 'V' || header[11] != 'E')) {
                throw ApiException.badRequest("Arquivo WAV inválido (assinatura WAVE ausente).");
            }
        }
        return ext;
    }

    public String validatePatch(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("O arquivo binário do patch/IR é obrigatório.");
        }
        if (file.getSize() > maxPatchBytes) {
            throw ApiException.payloadTooLarge("O arquivo do patch excede o tamanho máximo permitido.");
        }
        String ext = extractSafeExtension(file.getOriginalFilename());
        if (!patchExtensions.contains(ext)) {
            throw ApiException.badRequest("Extensão de arquivo de patch não permitida: ." + ext);
        }
        return ext;
    }

    public static String extractSafeExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        String clean = filename.replace('\\', '/');
        String base = Paths.get(clean).getFileName().toString();
        int dot = base.lastIndexOf('.');
        if (dot < 0 || dot == base.length() - 1) {
            return "";
        }
        String ext = base.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.matches("[a-z0-9]{1,8}") ? ext : "";
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }
}

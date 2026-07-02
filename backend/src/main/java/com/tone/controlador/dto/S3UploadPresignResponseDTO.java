package com.tone.controlador.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class S3UploadPresignResponseDTO {
    private String uploadUrl;
    private String fotoPerfilUrl;
}

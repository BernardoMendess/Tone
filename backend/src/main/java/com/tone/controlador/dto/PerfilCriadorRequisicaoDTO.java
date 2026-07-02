package com.tone.controlador.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerfilCriadorRequisicaoDTO {

    @Size(max = 1000, message = "A biografia deve ter no máximo 1000 caracteres")
    private String biografia;

    private String fotoPerfilUrl;

    private String instagramUrl;

    private String youtubeUrl;

    private String soundcloudUrl;

    private String sitePessoalUrl;
}

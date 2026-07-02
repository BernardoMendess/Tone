package com.tone.controlador.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerfilCriadorRespostaDTO {
    private Long id;
    private Long usuarioId;
    private String nomeCompleto;
    private String biografia;
    private String fotoPerfilUrl;
    private String instagramUrl;
    private String youtubeUrl;
    private String soundcloudUrl;
    private String sitePessoalUrl;
}

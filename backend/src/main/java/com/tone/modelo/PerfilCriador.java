package com.tone.modelo;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

@Entity
@Table(name = "perfil_criador")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerfilCriador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @Size(max = 1000, message = "A biografia deve ter no máximo 1000 caracteres")
    @Column(name = "biografia", columnDefinition = "TEXT")
    private String biografia;

    @Column(name = "foto_perfil_url", length = 512)
    private String fotoPerfilUrl;

    @Column(name = "instagram_url")
    private String instagramUrl;

    @Column(name = "youtube_url")
    private String youtubeUrl;

    @Column(name = "soundcloud_url")
    private String soundcloudUrl;

    @Column(name = "site_pessoal_url")
    private String sitePessoalUrl;
}

package com.tone.servico;

import com.tone.controlador.dto.PerfilCriadorRequisicaoDTO;
import com.tone.controlador.dto.PerfilCriadorRespostaDTO;
import com.tone.controlador.dto.S3UploadPresignResponseDTO;
import com.tone.modelo.PerfilCriador;
import com.tone.modelo.Usuario;
import com.tone.modelo.enums.TipoPerfil;
import com.tone.repositorio.PerfilCriadorRepository;
import com.tone.repositorio.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.UUID;

@Service
public class PerfilCriadorService {

    private final PerfilCriadorRepository perfilRepository;
    private final UsuarioRepository usuarioRepository;
    private final S3Service s3Service;

    public PerfilCriadorService(
            PerfilCriadorRepository perfilRepository,
            UsuarioRepository usuarioRepository,
            S3Service s3Service
    ) {
        this.perfilRepository = perfilRepository;
        this.usuarioRepository = usuarioRepository;
        this.s3Service = s3Service;
    }

    @Transactional
    public PerfilCriadorRespostaDTO obterOuCriarPerfil(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        PerfilCriador perfil = perfilRepository.findByUsuarioId(usuarioId)
                .orElseGet(() -> {
                    PerfilCriador novoPerfil = PerfilCriador.builder()
                            .usuario(usuario)
                            .build();
                    return perfilRepository.save(novoPerfil);
                });

        return mapearParaResposta(perfil);
    }

    @Transactional(readOnly = true)
    public PerfilCriadorRespostaDTO obterPerfilPublico(Long usuarioId) {
        PerfilCriador perfil = perfilRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new RuntimeException("Perfil do criador não encontrado"));

        if (perfil.getUsuario().getTipoPerfil() != TipoPerfil.CRIADOR && perfil.getUsuario().getTipoPerfil() != TipoPerfil.ADMIN) {
            throw new RuntimeException("Este usuário não é um criador ou administrador");
        }

        return mapearParaResposta(perfil);
    }

    @Transactional
    public PerfilCriadorRespostaDTO atualizarPerfil(Long usuarioId, PerfilCriadorRequisicaoDTO dto) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        if (usuario.getTipoPerfil() != TipoPerfil.CRIADOR && usuario.getTipoPerfil() != TipoPerfil.ADMIN) {
            throw new RuntimeException("Apenas criadores ou administradores podem gerenciar perfil");
        }

        PerfilCriador perfil = perfilRepository.findByUsuarioId(usuarioId)
                .orElseGet(() -> PerfilCriador.builder().usuario(usuario).build());

        // Validar redes sociais
        validarUrl(dto.getInstagramUrl(), "Instagram");
        validarUrl(dto.getYoutubeUrl(), "YouTube");
        validarUrl(dto.getSoundcloudUrl(), "SoundCloud");
        validarUrl(dto.getSitePessoalUrl(), "Site Pessoal");

        // Atualizar campos
        perfil.setBiografia(dto.getBiografia());
        
        if (dto.getFotoPerfilUrl() != null) {
            if (dto.getFotoPerfilUrl().trim().isEmpty()) {
                perfil.setFotoPerfilUrl(null);
            } else if (!dto.getFotoPerfilUrl().startsWith("http")) {
                perfil.setFotoPerfilUrl(dto.getFotoPerfilUrl());
            }
        }

        perfil.setInstagramUrl(dto.getInstagramUrl());
        perfil.setYoutubeUrl(dto.getYoutubeUrl());
        perfil.setSoundcloudUrl(dto.getSoundcloudUrl());
        perfil.setSitePessoalUrl(dto.getSitePessoalUrl());

        PerfilCriador perfilSalvo = perfilRepository.save(perfil);
        return mapearParaResposta(perfilSalvo);
    }

    @Transactional
    public S3UploadPresignResponseDTO gerarUrlUploadFotoPerfil(Long usuarioId, String mimeType) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        if (usuario.getTipoPerfil() != TipoPerfil.CRIADOR && usuario.getTipoPerfil() != TipoPerfil.ADMIN) {
            throw new RuntimeException("Apenas criadores ou administradores podem fazer upload de foto de perfil");
        }

        // Validar tipos de arquivos permitidos
        if (!mimeType.equalsIgnoreCase("image/jpeg") &&
            !mimeType.equalsIgnoreCase("image/jpg") &&
            !mimeType.equalsIgnoreCase("image/png") &&
            !mimeType.equalsIgnoreCase("image/webp")) {
            throw new IllegalArgumentException("Apenas imagens nos formatos JPEG, PNG ou WEBP são permitidas");
        }

        // Definir a chave do objeto no S3 (ex: fotos-perfil/{uuid}_perfil.extensão)
        String extensao = mimeType.substring(mimeType.lastIndexOf("/") + 1);
        if (extensao.equalsIgnoreCase("jpeg")) {
            extensao = "jpg";
        }
        String objectKey = "fotos-perfil/" + UUID.randomUUID().toString() + "_perfil." + extensao;

        // Gerar a URL pré-assinada de upload (válida por 5 minutos)
        String uploadUrl = s3Service.gerarUrlUpload(objectKey, mimeType, 300);

        return S3UploadPresignResponseDTO.builder()
                .uploadUrl(uploadUrl)
                .fotoPerfilUrl(objectKey)
                .build();
    }

    private void validarUrl(String url, String nomeCampo) {
        if (url != null && !url.trim().isEmpty()) {
            try {
                URI uri = new URI(url.trim());
                if (uri.getScheme() == null || (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https"))) {
                    throw new IllegalArgumentException("O campo " + nomeCampo + " deve ser uma URL válida iniciando com http:// ou https://");
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("O campo " + nomeCampo + " deve ser uma URL válida");
            }
        }
    }

    private PerfilCriadorRespostaDTO mapearParaResposta(PerfilCriador perfil) {
        String urlFoto = null;
        if (perfil.getFotoPerfilUrl() != null && !perfil.getFotoPerfilUrl().trim().isEmpty()) {
            try {
                // Link assinado por 1 hora (3600 segundos) para exibição da imagem
                urlFoto = s3Service.gerarUrlDownload(perfil.getFotoPerfilUrl(), 3600);
            } catch (Exception e) {
                // Em caso de falha de assinatura, mantém nulo ou loga sem quebrar a API
                urlFoto = null;
            }
        }

        return PerfilCriadorRespostaDTO.builder()
                .id(perfil.getId())
                .usuarioId(perfil.getUsuario().getId())
                .nomeCompleto(perfil.getUsuario().getNomeCompleto())
                .biografia(perfil.getBiografia())
                .fotoPerfilUrl(urlFoto)
                .instagramUrl(perfil.getInstagramUrl())
                .youtubeUrl(perfil.getYoutubeUrl())
                .soundcloudUrl(perfil.getSoundcloudUrl())
                .sitePessoalUrl(perfil.getSitePessoalUrl())
                .build();
    }
}

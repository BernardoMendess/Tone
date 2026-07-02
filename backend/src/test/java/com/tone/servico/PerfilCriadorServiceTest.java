package com.tone.servico;

import com.tone.controlador.dto.PerfilCriadorRequisicaoDTO;
import com.tone.controlador.dto.PerfilCriadorRespostaDTO;
import com.tone.controlador.dto.S3UploadPresignResponseDTO;
import com.tone.modelo.PerfilCriador;
import com.tone.modelo.Usuario;
import com.tone.modelo.enums.TipoPerfil;
import com.tone.repositorio.PerfilCriadorRepository;
import com.tone.repositorio.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PerfilCriadorService — Testes Unitários")
class PerfilCriadorServiceTest {

    @Mock private PerfilCriadorRepository perfilRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private S3Service s3Service;

    @InjectMocks
    private PerfilCriadorService perfilService;

    private Usuario usuarioCriador;
    private Usuario usuarioComprador;
    private PerfilCriador perfilCriador;

    @BeforeEach
    void setUp() {
        usuarioCriador = new Usuario();
        usuarioCriador.setId(1L);
        usuarioCriador.setNomeCompleto("John Petrucci");
        usuarioCriador.setEmail("john@dreamtheater.com");
        usuarioCriador.setTipoPerfil(TipoPerfil.CRIADOR);

        usuarioComprador = new Usuario();
        usuarioComprador.setId(2L);
        usuarioComprador.setNomeCompleto("João Silva");
        usuarioComprador.setEmail("joao@example.com");
        usuarioComprador.setTipoPerfil(TipoPerfil.COMPRADOR);

        perfilCriador = PerfilCriador.builder()
                .id(1L)
                .usuario(usuarioCriador)
                .biografia("Guitarrista do Dream Theater")
                .fotoPerfilUrl("fotos-perfil/john_perfil.jpg")
                .instagramUrl("https://instagram.com/jpetrucci")
                .youtubeUrl("https://youtube.com/c/jpetrucci")
                .build();
    }

    @Test
    @DisplayName("obterOuCriarPerfil: Usuário existe e já tem perfil deve retornar o perfil cadastrado")
    void obterPerfilExistente() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuarioCriador));
        when(perfilRepository.findByUsuarioId(1L)).thenReturn(Optional.of(perfilCriador));

        PerfilCriadorRespostaDTO resposta = perfilService.obterOuCriarPerfil(1L);

        assertNotNull(resposta);
        assertEquals("John Petrucci", resposta.getNomeCompleto());
        assertEquals("Guitarrista do Dream Theater", resposta.getBiografia());
        verify(perfilRepository, never()).save(any());
    }

    @Test
    @DisplayName("obterOuCriarPerfil: Usuário existe e não tem perfil deve criar um perfil vazio e salvar")
    void obterPerfilInexistenteCriaNovo() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuarioCriador));
        when(perfilRepository.findByUsuarioId(1L)).thenReturn(Optional.empty());
        when(perfilRepository.save(any(PerfilCriador.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PerfilCriadorRespostaDTO resposta = perfilService.obterOuCriarPerfil(1L);

        assertNotNull(resposta);
        assertNull(resposta.getBiografia());
        assertNull(resposta.getFotoPerfilUrl());
        verify(perfilRepository).save(any(PerfilCriador.class));
    }

    @Test
    @DisplayName("obterPerfilPublico: Deve retornar perfil de criador cadastrado")
    void obterPerfilPublicoSucesso() {
        when(perfilRepository.findByUsuarioId(1L)).thenReturn(Optional.of(perfilCriador));

        PerfilCriadorRespostaDTO resposta = perfilService.obterPerfilPublico(1L);

        assertNotNull(resposta);
        assertEquals("John Petrucci", resposta.getNomeCompleto());
        assertEquals("Guitarrista do Dream Theater", resposta.getBiografia());
    }

    @Test
    @DisplayName("obterPerfilPublico: Deve lançar erro se o perfil pertencer a um comprador comum")
    void obterPerfilPublicoDeCompradorErro() {
        PerfilCriador perfilComprador = PerfilCriador.builder()
                .id(2L)
                .usuario(usuarioComprador)
                .build();
        when(perfilRepository.findByUsuarioId(2L)).thenReturn(Optional.of(perfilComprador));

        assertThrows(RuntimeException.class, () -> perfilService.obterPerfilPublico(2L));
    }

    @Test
    @DisplayName("atualizarPerfil: Deve atualizar biografia e redes sociais com dados válidos")
    void atualizarPerfilSucesso() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuarioCriador));
        when(perfilRepository.findByUsuarioId(1L)).thenReturn(Optional.of(perfilCriador));
        when(perfilRepository.save(any(PerfilCriador.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PerfilCriadorRequisicaoDTO dto = PerfilCriadorRequisicaoDTO.builder()
                .biografia("Nova biografia")
                .fotoPerfilUrl("fotos-perfil/nova.png")
                .instagramUrl("https://instagram.com/new")
                .youtubeUrl("https://youtube.com/new")
                .build();

        PerfilCriadorRespostaDTO resposta = perfilService.atualizarPerfil(1L, dto);

        assertNotNull(resposta);
        assertEquals("Nova biografia", resposta.getBiografia());
        assertEquals("fotos-perfil/nova.png", resposta.getFotoPerfilUrl());
        assertEquals("https://instagram.com/new", resposta.getInstagramUrl());
        assertEquals("https://youtube.com/new", resposta.getYoutubeUrl());
        verify(perfilRepository).save(any(PerfilCriador.class));
    }

    @Test
    @DisplayName("atualizarPerfil: Deve lançar erro se um comprador comum tentar atualizar perfil")
    void atualizarPerfilCompradorErro() {
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuarioComprador));

        PerfilCriadorRequisicaoDTO dto = PerfilCriadorRequisicaoDTO.builder()
                .biografia("Tento atualizar")
                .build();

        assertThrows(RuntimeException.class, () -> perfilService.atualizarPerfil(2L, dto));
        verify(perfilRepository, never()).save(any());
    }

    @Test
    @DisplayName("atualizarPerfil: Deve lançar erro se a URL do Instagram for inválida")
    void atualizarPerfilUrlInvalida() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuarioCriador));
        when(perfilRepository.findByUsuarioId(1L)).thenReturn(Optional.of(perfilCriador));

        PerfilCriadorRequisicaoDTO dto = PerfilCriadorRequisicaoDTO.builder()
                .biografia("Bio")
                .instagramUrl("instagram.com/invalido") // Sem http/https
                .build();

        assertThrows(IllegalArgumentException.class, () -> perfilService.atualizarPerfil(1L, dto));
        verify(perfilRepository, never()).save(any());
    }

    @Test
    @DisplayName("gerarUrlUploadFotoPerfil: Deve gerar URL de upload para tipo de imagem permitido")
    void gerarUrlUploadSucesso() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuarioCriador));
        when(s3Service.gerarUrlUpload(anyString(), anyString(), anyInt())).thenReturn("http://s3.presigned.url");

        S3UploadPresignResponseDTO resposta = perfilService.gerarUrlUploadFotoPerfil(1L, "image/png");

        assertNotNull(resposta);
        assertEquals("http://s3.presigned.url", resposta.getUploadUrl());
        assertTrue(resposta.getFotoPerfilUrl().startsWith("fotos-perfil/"));
        assertTrue(resposta.getFotoPerfilUrl().endsWith(".png"));
    }

    @Test
    @DisplayName("gerarUrlUploadFotoPerfil: Deve lançar erro se o tipo MIME for inválido")
    void gerarUrlUploadMimeInvalido() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuarioCriador));

        assertThrows(IllegalArgumentException.class,
                () -> perfilService.gerarUrlUploadFotoPerfil(1L, "application/pdf"));
    }
}

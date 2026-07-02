package com.tone.controlador;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tone.controlador.dto.PerfilCriadorRequisicaoDTO;
import com.tone.controlador.dto.PerfilCriadorRespostaDTO;
import com.tone.controlador.dto.S3UploadPresignResponseDTO;
import com.tone.modelo.Usuario;
import com.tone.modelo.enums.TipoPerfil;
import com.tone.servico.PerfilCriadorService;
import com.tone.servico.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PerfilCriadorController.class)
@DisplayName("PerfilCriadorController — Testes de API (Web Layer)")
class PerfilCriadorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PerfilCriadorService perfilService;

    @MockBean
    private UsuarioService usuarioService;

    private Usuario usuarioCriador;
    private PerfilCriadorRespostaDTO perfilResposta;

    @BeforeEach
    void setUp() {
        usuarioCriador = new Usuario();
        usuarioCriador.setId(1L);
        usuarioCriador.setNomeCompleto("John Petrucci");
        usuarioCriador.setEmail("john@dreamtheater.com");
        usuarioCriador.setTipoPerfil(TipoPerfil.CRIADOR);

        perfilResposta = PerfilCriadorRespostaDTO.builder()
                .id(10L)
                .usuarioId(1L)
                .nomeCompleto("John Petrucci")
                .biografia("Guitarrista do Dream Theater")
                .fotoPerfilUrl("fotos-perfil/john.jpg")
                .instagramUrl("https://instagram.com/jpetrucci")
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/perfis/{usuarioId}: deve retornar 200 sem necessidade de autenticação")
    void obterPerfilPublico_deveRetornar200() throws Exception {
        when(perfilService.obterPerfilPublico(1L)).thenReturn(perfilResposta);

        mockMvc.perform(get("/api/v1/perfis/{usuarioId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeCompleto").value("John Petrucci"))
                .andExpect(jsonPath("$.biografia").value("Guitarrista do Dream Theater"))
                .andExpect(jsonPath("$.fotoPerfilUrl").value("fotos-perfil/john.jpg"))
                .andExpect(jsonPath("$.instagramUrl").value("https://instagram.com/jpetrucci"));
    }

    @Test
    @WithMockUser(username = "john@dreamtheater.com", roles = "CRIADOR")
    @DisplayName("GET /api/v1/perfis/me: deve retornar perfil do criador logado")
    void obterMeuPerfil_deveRetornar200() throws Exception {
        when(usuarioService.buscarPorEmail("john@dreamtheater.com")).thenReturn(usuarioCriador);
        when(perfilService.obterOuCriarPerfil(1L)).thenReturn(perfilResposta);

        mockMvc.perform(get("/api/v1/perfis/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeCompleto").value("John Petrucci"))
                .andExpect(jsonPath("$.usuarioId").value(1L));
    }

    @Test
    @WithMockUser(username = "john@dreamtheater.com", roles = "CRIADOR")
    @DisplayName("PUT /api/v1/perfis/me: deve atualizar dados e retornar 200")
    void atualizarMeuPerfil_deveRetornar200() throws Exception {
        PerfilCriadorRequisicaoDTO requisicao = PerfilCriadorRequisicaoDTO.builder()
                .biografia("Nova biografia editada")
                .fotoPerfilUrl("fotos-perfil/editada.png")
                .instagramUrl("https://instagram.com/new")
                .build();

        PerfilCriadorRespostaDTO respostaAtualizada = PerfilCriadorRespostaDTO.builder()
                .id(10L)
                .usuarioId(1L)
                .nomeCompleto("John Petrucci")
                .biografia("Nova biografia editada")
                .fotoPerfilUrl("fotos-perfil/editada.png")
                .instagramUrl("https://instagram.com/new")
                .build();

        when(usuarioService.buscarPorEmail("john@dreamtheater.com")).thenReturn(usuarioCriador);
        when(perfilService.atualizarPerfil(eq(1L), any(PerfilCriadorRequisicaoDTO.class))).thenReturn(respostaAtualizada);

        mockMvc.perform(put("/api/v1/perfis/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requisicao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biografia").value("Nova biografia editada"))
                .andExpect(jsonPath("$.fotoPerfilUrl").value("fotos-perfil/editada.png"))
                .andExpect(jsonPath("$.instagramUrl").value("https://instagram.com/new"));
    }

    @Test
    @WithMockUser(username = "john@dreamtheater.com", roles = "CRIADOR")
    @DisplayName("POST /api/v1/perfis/upload-imagem: deve gerar URL pré-assinada do S3")
    void solicitarUrlUpload_deveRetornar200() throws Exception {
        Map<String, String> payload = new HashMap<>();
        payload.put("mimeType", "image/png");

        S3UploadPresignResponseDTO responsePresign = S3UploadPresignResponseDTO.builder()
                .uploadUrl("https://s3.amazonaws.com/upload-presigned-url-mock")
                .fotoPerfilUrl("fotos-perfil/uuid_foto.png")
                .build();

        when(usuarioService.buscarPorEmail("john@dreamtheater.com")).thenReturn(usuarioCriador);
        when(perfilService.gerarUrlUploadFotoPerfil(eq(1L), eq("image/png"))).thenReturn(responsePresign);

        mockMvc.perform(post("/api/v1/perfis/upload-imagem")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value("https://s3.amazonaws.com/upload-presigned-url-mock"))
                .andExpect(jsonPath("$.fotoPerfilUrl").value("fotos-perfil/uuid_foto.png"));
    }

    @Test
    @DisplayName("GET /api/v1/perfis/me: deve retornar 401/403 se usuário não estiver autenticado")
    void obterMeuPerfil_naoAutenticado_deveRetornarErro() throws Exception {
        mockMvc.perform(get("/api/v1/perfis/me"))
                .andExpect(status().isUnauthorized()); // ou isForbidden dependendo do setup de seguranca
    }
}

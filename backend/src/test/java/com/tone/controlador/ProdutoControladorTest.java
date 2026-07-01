package com.tone.controlador;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tone.controlador.dto.ProdutoRequest;
import com.tone.controlador.dto.ProdutoResponse;
import com.tone.controlador.dto.UploadUrlRequest;
import com.tone.controlador.dto.UploadUrlResponse;
import com.tone.controlador.excecao.ProdutoNaoEncontradoException;
import com.tone.modelo.Produto;
import com.tone.modelo.enums.GeneroMusical;
import com.tone.modelo.enums.StatusProduto;
import com.tone.servico.ProdutoServico;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProdutoControlador.class)
@DisplayName("ProdutoControlador — Testes de Integração (Web Layer)")
class ProdutoControladorTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProdutoServico produtoServico;

    // =========================================================
    // POST /api/v1/produtos/upload-url
    // =========================================================

    @Test
    @WithMockUser(username = "joao@tone.com")
    @DisplayName("POST /upload-url: deve retornar 200 com Presigned URL")
    void solicitarUrlUpload_deveRetornar200() throws Exception {
        UploadUrlRequest request = new UploadUrlRequest();
        request.setNomeArquivo("pack_rock.zip");
        request.setTamanhoBytes(512000L);
        request.setMimeType("application/zip");

        UploadUrlResponse response = new UploadUrlResponse(
                "https://s3.amazonaws.com/tone-bucket/timbres/joao@tone.com/uuid_pack_rock.zip?X-Amz-Signature=...",
                "timbres/joao@tone.com/uuid_pack_rock.zip",
                900
        );

        when(produtoServico.gerarUrlUpload(any(UploadUrlRequest.class), eq("joao@tone.com")))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/produtos/upload-url")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").isNotEmpty())
                .andExpect(jsonPath("$.arquivoUri").value("timbres/joao@tone.com/uuid_pack_rock.zip"))
                .andExpect(jsonPath("$.expiraEmSegundos").value(900));
    }

    @Test
    @WithMockUser(username = "joao@tone.com")
    @DisplayName("POST /upload-url: deve retornar 400 se mimeType inválido")
    void solicitarUrlUpload_deveRetornar400_seMimeTypeInvalido() throws Exception {
        UploadUrlRequest request = new UploadUrlRequest();
        request.setNomeArquivo("foto.jpg");
        request.setTamanhoBytes(512000L);
        request.setMimeType("image/jpeg"); // tipo não permitido

        mockMvc.perform(post("/api/v1/produtos/upload-url")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "joao@tone.com")
    @DisplayName("POST /upload-url: deve retornar 400 se tamanho excede 1MB")
    void solicitarUrlUpload_deveRetornar400_seArquivoMuitoGrande() throws Exception {
        UploadUrlRequest request = new UploadUrlRequest();
        request.setNomeArquivo("pack_rock.zip");
        request.setTamanhoBytes(2097152L); // 2MB — excede limite
        request.setMimeType("application/zip");

        mockMvc.perform(post("/api/v1/produtos/upload-url")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================
    // POST /api/v1/produtos
    // =========================================================

    @Test
    @WithMockUser(username = "joao@tone.com")
    @DisplayName("POST /produtos: deve retornar 201 com produto cadastrado")
    void cadastrarProduto_deveRetornar201() throws Exception {
        ProdutoRequest request = new ProdutoRequest();
        request.setNome("Pack Rock 80s");
        request.setDescricao("Timbre clássico de guitarra");
        request.setPreco(new BigDecimal("49.90"));
        request.setArquivoUri("timbres/joao@tone.com/uuid_arquivo.zip");
        request.setTags(Set.of(GeneroMusical.ROCK));

        UUID idGerado = UUID.randomUUID();
        Produto produtoSalvo = new Produto();
        produtoSalvo.setNome("Pack Rock 80s");
        produtoSalvo.setStatus(StatusProduto.PUBLICADO);

        when(produtoServico.criarProduto(any(ProdutoRequest.class), eq("joao@tone.com")))
                .thenReturn(produtoSalvo);

        mockMvc.perform(post("/api/v1/produtos")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PUBLICADO"))
                .andExpect(jsonPath("$.mensagem").value("Produto cadastrado com sucesso."));
    }

    @Test
    @WithMockUser(username = "joao@tone.com")
    @DisplayName("POST /produtos: deve retornar 400 se nome estiver em branco")
    void cadastrarProduto_deveRetornar400_seNomeEmBranco() throws Exception {
        ProdutoRequest request = new ProdutoRequest();
        request.setNome(""); // inválido
        request.setDescricao("Descrição válida");
        request.setPreco(new BigDecimal("49.90"));
        request.setArquivoUri("timbres/joao@tone.com/uuid.zip");

        mockMvc.perform(post("/api/v1/produtos")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================
    // GET /api/v1/produtos/{id}/download
    // =========================================================

    @Test
    @WithMockUser(username = "joao@tone.com")
    @DisplayName("GET /{id}/download: deve retornar 200 com Presigned URL de download")
    void gerarUrlDownload_deveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        String urlDownload = "https://s3.amazonaws.com/tone-bucket/timbres/joao@tone.com/uuid.zip?X-Amz-Expires=300";

        when(produtoServico.gerarUrlDownload(id)).thenReturn(urlDownload);

        mockMvc.perform(get("/api/v1/produtos/{id}/download", id))
                .andExpect(status().isOk())
                .andExpect(content().string(urlDownload));
    }

    @Test
    @WithMockUser(username = "joao@tone.com")
    @DisplayName("GET /{id}/download: deve retornar 404 se produto não existir")
    void gerarUrlDownload_deveRetornar404_seProdutoNaoExistir() throws Exception {
        UUID idInexistente = UUID.randomUUID();

        when(produtoServico.gerarUrlDownload(idInexistente))
                .thenThrow(new ProdutoNaoEncontradoException(idInexistente.toString()));

        mockMvc.perform(get("/api/v1/produtos/{id}/download", idInexistente))
                .andExpect(status().isNotFound());
    }
}

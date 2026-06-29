package com.tone.servico;

import com.tone.controlador.dto.ProdutoRequest;
import com.tone.controlador.dto.UploadUrlRequest;
import com.tone.controlador.dto.UploadUrlResponse;
import com.tone.controlador.excecao.CriadorNaoEncontradoException;
import com.tone.controlador.excecao.ProdutoNaoEncontradoException;
import com.tone.modelo.Produto;
import com.tone.modelo.Usuario;
import com.tone.modelo.enums.GeneroMusical;
import com.tone.modelo.enums.StatusProduto;
import com.tone.repositorio.ProdutoRepositorio;
import com.tone.repositorio.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProdutoServico — Testes Unitários")
class ProdutoServicoTest {

    @Mock
    private ProdutoRepositorio produtoRepositorio;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private S3Service s3Service;

    @InjectMocks
    private ProdutoServico produtoServico;

    private Usuario criadorFake;
    private Produto produtoFake;

    @BeforeEach
    void configurar() {
        criadorFake = new Usuario();
        criadorFake.setNomeCompleto("João Criador");
        criadorFake.setEmail("joao@tone.com");

        produtoFake = new Produto();
        produtoFake.setNome("Pack Rock 80s");
        produtoFake.setDescricao("Timbre clássico de guitarra");
        produtoFake.setPreco(new BigDecimal("49.90"));
        produtoFake.setArquivoUri("timbres/joao@tone.com/uuid_arquivo.zip");
        produtoFake.setStatus(StatusProduto.PUBLICADO);
        produtoFake.setCriador(criadorFake);
    }

    // =========================================================
    // gerarUrlUpload
    // =========================================================

    @Test
    @DisplayName("gerarUrlUpload: deve retornar UploadUrlResponse com URL e chave do objeto")
    void gerarUrlUpload_deveRetornarResponse() {
        UploadUrlRequest request = new UploadUrlRequest();
        request.setNomeArquivo("pack_rock.zip");
        request.setTamanhoBytes(512000L);
        request.setMimeType("application/zip");

        when(s3Service.gerarUrlUpload(anyString(), eq("application/zip"), eq(900)))
                .thenReturn("https://s3.amazonaws.com/tone-bucket/timbres/joao@tone.com/uuid_pack_rock.zip?X-Amz-Signature=...");

        UploadUrlResponse response = produtoServico.gerarUrlUpload(request, "joao@tone.com");

        assertThat(response).isNotNull();
        assertThat(response.uploadUrl()).startsWith("https://s3.amazonaws.com");
        assertThat(response.arquivoUri()).startsWith("timbres/joao@tone.com/");
        assertThat(response.arquivoUri()).endsWith("_pack_rock.zip");
        assertThat(response.expiraEmSegundos()).isEqualTo(900);

        verify(s3Service, times(1)).gerarUrlUpload(anyString(), eq("application/zip"), eq(900));
    }

    // =========================================================
    // criarProduto
    // =========================================================

    @Test
    @DisplayName("criarProduto: deve salvar e retornar produto com status PUBLICADO")
    void criarProduto_deveSalvarComStatusPublicado() {
        ProdutoRequest request = new ProdutoRequest();
        request.setNome("Pack Rock 80s");
        request.setDescricao("Timbre clássico de guitarra");
        request.setPreco(new BigDecimal("49.90"));
        request.setArquivoUri("timbres/joao@tone.com/uuid_arquivo.zip");
        request.setTags(Set.of(GeneroMusical.ROCK));

        when(usuarioRepository.encontrarPorEmail("joao@tone.com")).thenReturn(Optional.of(criadorFake));
        when(produtoRepositorio.save(any(Produto.class))).thenReturn(produtoFake);

        Produto resultado = produtoServico.criarProduto(request, "joao@tone.com");

        assertThat(resultado).isNotNull();
        assertThat(resultado.getStatus()).isEqualTo(StatusProduto.PUBLICADO);
        assertThat(resultado.getCriador().getEmail()).isEqualTo("joao@tone.com");

        verify(produtoRepositorio, times(1)).save(any(Produto.class));
    }

    @Test
    @DisplayName("criarProduto: deve lançar CriadorNaoEncontradoException se e-mail não existir")
    void criarProduto_deveLancarExcecao_seCriadorNaoExistir() {
        ProdutoRequest request = new ProdutoRequest();
        request.setNome("Pack");
        request.setDescricao("Desc");
        request.setPreco(BigDecimal.TEN);
        request.setArquivoUri("timbres/x.zip");

        when(usuarioRepository.encontrarPorEmail("naoexiste@tone.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoServico.criarProduto(request, "naoexiste@tone.com"))
                .isInstanceOf(CriadorNaoEncontradoException.class)
                .hasMessageContaining("naoexiste@tone.com");

        verify(produtoRepositorio, never()).save(any());
    }

    // =========================================================
    // obterProduto
    // =========================================================

    @Test
    @DisplayName("obterProduto: deve retornar produto quando ID existe")
    void obterProduto_deveRetornarProduto() {
        UUID id = UUID.randomUUID();
        when(produtoRepositorio.findById(id)).thenReturn(Optional.of(produtoFake));

        Produto resultado = produtoServico.obterProduto(id);

        assertThat(resultado).isNotNull();
        assertThat(resultado.getNome()).isEqualTo("Pack Rock 80s");
    }

    @Test
    @DisplayName("obterProduto: deve lançar ProdutoNaoEncontradoException se ID não existir")
    void obterProduto_deveLancarExcecao_seNaoExistir() {
        UUID idInexistente = UUID.randomUUID();
        when(produtoRepositorio.findById(idInexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoServico.obterProduto(idInexistente))
                .isInstanceOf(ProdutoNaoEncontradoException.class)
                .hasMessageContaining(idInexistente.toString());
    }

    // =========================================================
    // gerarUrlDownload
    // =========================================================

    @Test
    @DisplayName("gerarUrlDownload: deve retornar URL temporária de 5 minutos")
    void gerarUrlDownload_deveRetornarUrlTemporaria() {
        UUID id = UUID.randomUUID();
        produtoFake.setArquivoUri("timbres/joao@tone.com/uuid_arquivo.zip");

        when(produtoRepositorio.findById(id)).thenReturn(Optional.of(produtoFake));
        when(s3Service.gerarUrlDownload("timbres/joao@tone.com/uuid_arquivo.zip", 300))
                .thenReturn("https://s3.amazonaws.com/tone-bucket/timbres/joao@tone.com/uuid_arquivo.zip?X-Amz-Expires=300&...");

        String url = produtoServico.gerarUrlDownload(id);

        assertThat(url).isNotBlank();
        assertThat(url).startsWith("https://s3.amazonaws.com");
        verify(s3Service, times(1)).gerarUrlDownload(anyString(), eq(300));
    }

    @Test
    @DisplayName("gerarUrlDownload: deve lançar ProdutoNaoEncontradoException se produto não existir")
    void gerarUrlDownload_deveLancarExcecao_seProdutoNaoExistir() {
        UUID idInexistente = UUID.randomUUID();
        when(produtoRepositorio.findById(idInexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoServico.gerarUrlDownload(idInexistente))
                .isInstanceOf(ProdutoNaoEncontradoException.class)
                .hasMessageContaining(idInexistente.toString());

        verify(s3Service, never()).gerarUrlDownload(anyString(), anyInt());
    }
}

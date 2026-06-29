package com.tone.controlador.excecao;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Tratador global de exceções da API REST.
 */
@RestControllerAdvice
public class TratadorExcecoes {

    @ExceptionHandler(EmailJaCadastradoException.class)
    public ResponseEntity<Map<String, Object>> tratarEmailDuplicado(EmailJaCadastradoException ex) {
        return criarResposta(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(CpfJaCadastradoException.class)
    public ResponseEntity<Map<String, Object>> tratarCpfDuplicado(CpfJaCadastradoException ex) {
        return criarResposta(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ResponseEntity<Map<String, Object>> tratarCredenciaisInvalidas(CredenciaisInvalidasException ex) {
        return criarResposta(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(ContaBloqueadaException.class)
    public ResponseEntity<Map<String, Object>> tratarContaBloqueada(ContaBloqueadaException ex) {
        return criarResposta(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    @ExceptionHandler(ContaInativaException.class)
    public ResponseEntity<Map<String, Object>> tratarContaInativa(ContaInativaException ex) {
        return criarResposta(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(EmailNaoVerificadoException.class)
    public ResponseEntity<Map<String, Object>> tratarEmailNaoVerificado(EmailNaoVerificadoException ex) {
        return criarResposta(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(TokenVerificacaoInvalidoException.class)
    public ResponseEntity<Map<String, Object>> tratarTokenInvalido(TokenVerificacaoInvalidoException ex) {
        return criarResposta(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ProdutoNaoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> tratarProdutoNaoEncontrado(ProdutoNaoEncontradoException ex) {
        return criarResposta(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(CriadorNaoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> tratarCriadorNaoEncontrado(CriadorNaoEncontradoException ex) {
        return criarResposta(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> tratarValidacao(MethodArgumentNotValidException ex) {
        Map<String, String> erros = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(erro -> {
            String campo = ((FieldError) erro).getField();
            String mensagem = erro.getDefaultMessage();
            erros.put(campo, mensagem);
        });

        Map<String, Object> corpo = new HashMap<>();
        corpo.put("timestamp", LocalDateTime.now());
        corpo.put("status", HttpStatus.BAD_REQUEST.value());
        corpo.put("erro", "Dados inválidos");
        corpo.put("campos", erros);

        return ResponseEntity.badRequest().body(corpo);
    }

    private ResponseEntity<Map<String, Object>> criarResposta(HttpStatus status, String mensagem) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("timestamp", LocalDateTime.now());
        corpo.put("status", status.value());
        corpo.put("mensagem", mensagem);
        return ResponseEntity.status(status).body(corpo);
    }
}

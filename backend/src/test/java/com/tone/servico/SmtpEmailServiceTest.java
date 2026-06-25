package com.tone.servico;

import com.tone.modelo.Usuario;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SmtpEmailService — Testes Unitários")
class SmtpEmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private SmtpEmailService smtpEmailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(smtpEmailService, "remetente", "tone@gmail.com");
        ReflectionTestUtils.setField(smtpEmailService, "appUrl", "http://localhost:4200");
    }

    @Test
    @DisplayName("Envio de e-mail de verificação deve chamar JavaMailSender")
    void envioDeveriaUsarMailSender() {
        Usuario usuario = new Usuario();
        usuario.setNomeCompleto("João Silva");
        usuario.setEmail("joao@example.com");

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doNothing().when(mailSender).send(any(MimeMessage.class));

        smtpEmailService.enviarEmailVerificacao(usuario, "uuid-teste");

        verify(mailSender).createMimeMessage();
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Erro no JavaMailSender deve lançar RuntimeException")
    void erroNoMailSenderDeveLancarRuntimeException() {
        Usuario usuario = new Usuario();
        usuario.setNomeCompleto("João Silva");
        usuario.setEmail("joao@example.com");

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new org.springframework.mail.MailSendException("SMTP falhou"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThrows(RuntimeException.class,
                () -> smtpEmailService.enviarEmailVerificacao(usuario, "uuid-teste"));
    }
}

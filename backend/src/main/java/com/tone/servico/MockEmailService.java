package com.tone.servico;

import com.tone.modelo.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Implementação de {@link EmailService} para desenvolvimento local.
 * Em vez de enviar e-mails reais, loga o link de verificação no console.
 * Ativa apenas com o profile "dev".
 */
@Service
@Profile("dev")
public class MockEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(MockEmailService.class);

    @Value("${app.url}")
    private String appUrl;

    @Override
    public void enviarEmailVerificacao(Usuario usuario, String token) {
        String link = appUrl + "/verificar-email?token=" + token;

        log.info("""
                \n
                ╔══════════════════════════════════════════════════════════╗
                ║          📧  E-MAIL DE VERIFICAÇÃO  (MOCK / DEV)        ║
                ╠══════════════════════════════════════════════════════════╣
                ║  Para : {}
                ║  Nome : {}
                ║  Link : {}
                ╚══════════════════════════════════════════════════════════╝
                """,
                usuario.getEmail(),
                usuario.getNomeCompleto(),
                link);
    }
}

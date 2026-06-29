package com.tone.servico;

import com.tone.modelo.Usuario;

/**
 * Contrato para envio de e-mails transacionais.
 * As implementações são selecionadas via Spring Profile:
 *  - "dev"  → MockEmailService  (loga no console)
 *  - "prod" → SmtpEmailService  (envio real via SMTP)
 */
public interface EmailService {

    /**
     * Envia o e-mail de verificação de conta para o usuário.
     *
     * @param usuario o usuário que acabou de se cadastrar
     * @param token   o token UUID de verificação
     */
    void enviarEmailVerificacao(Usuario usuario, String token);
}

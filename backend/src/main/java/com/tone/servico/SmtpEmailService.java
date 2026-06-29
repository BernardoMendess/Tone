package com.tone.servico;

import com.tone.modelo.Usuario;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Implementação de {@link EmailService} que envia e-mails reais via SMTP.
 * Ativa apenas com o profile "prod".
 */
@Service
@Profile("prod")
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String remetente;

    @Value("${app.url}")
    private String appUrl;

    public SmtpEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void enviarEmailVerificacao(Usuario usuario, String token) {
        String link = appUrl + "/verificar-email?token=" + token;
        String conteudo = construirCorpoEmail(usuario.getNomeCompleto(), link);

        try {
            MimeMessage mensagem = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensagem, true, "UTF-8");
            helper.setFrom(remetente);
            helper.setTo(usuario.getEmail());
            helper.setSubject("Tone — Confirme seu e-mail");
            helper.setText(conteudo, true);
            mailSender.send(mensagem);
        } catch (MessagingException e) {
            throw new RuntimeException("Erro ao enviar e-mail de verificação", e);
        }
    }

    private String construirCorpoEmail(String nomeCompleto, String link) {
        return """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head>
                  <meta charset="UTF-8">
                  <style>
                    body { margin: 0; padding: 0; background-color: #0A0A0A; font-family: 'Inter', Arial, sans-serif; }
                    .container { max-width: 580px; margin: 40px auto; background-color: #1A1A1A; border-radius: 12px; overflow: hidden; }
                    .header { padding: 32px 40px; background: linear-gradient(135deg, #1A1A1A 0%, #27272A 100%); border-bottom: 1px solid #27272A; }
                    .logo { font-size: 28px; font-weight: 700; color: #F4F4F5; letter-spacing: -0.5px; }
                    .body { padding: 40px; }
                    .greeting { font-size: 18px; color: #F4F4F5; margin-bottom: 16px; }
                    .text { font-size: 14px; color: #A1A1AA; line-height: 1.6; margin-bottom: 32px; }
                    .btn { display: inline-block; padding: 14px 32px; background-color: #F4F4F5; color: #0A0A0A; text-decoration: none; border-radius: 8px; font-size: 14px; font-weight: 600; }
                    .footer { padding: 24px 40px; border-top: 1px solid #27272A; font-size: 12px; color: #52525B; }
                    .link-fallback { word-break: break-all; color: #A1A1AA; font-size: 12px; margin-top: 24px; }
                  </style>
                </head>
                <body>
                  <div class="container">
                    <div class="header">
                      <div class="logo">tone</div>
                    </div>
                    <div class="body">
                      <p class="greeting">Olá, %s!</p>
                      <p class="text">
                        Obrigado por criar sua conta na <strong style="color:#F4F4F5">Tone</strong>.<br>
                        Clique no botão abaixo para confirmar seu e-mail e ativar sua conta.
                      </p>
                      <a href="%s" class="btn">Confirmar e-mail</a>
                      <p class="link-fallback">
                        Ou copie e cole este link no seu navegador:<br>%s
                      </p>
                    </div>
                    <div class="footer">
                      Se você não criou uma conta na Tone, ignore este e-mail. O link expira em 24 horas.
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(nomeCompleto, link, link);
    }
}

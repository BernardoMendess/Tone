package com.toneguitar.payment;

import com.toneguitar.domain.enums.PaymentMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * RF03 (PIX prioritário) + RF13 (Motor de Split de Pagamento):
 * Gera payload EMVCo BR Code (PIX Copia e Cola com CRC16-CCITT válido), registra as
 * instruções de split por criador + comissão da plataforma e valida assinatura HMAC de webhooks.
 */
@Service
public class SimulatedPixPaymentGateway implements PaymentGateway {

    private final String webhookSecret;
    private final String pixKey;
    private final String merchantName;
    private final String merchantCity;

    public SimulatedPixPaymentGateway(
            @Value("${app.payment.webhook-secret}") String webhookSecret,
            @Value("${app.payment.pix-key:pix@toneguitar.com.br}") String pixKey,
            @Value("${app.payment.pix-merchant-name:TONEGUITAR MARKETPLACE}") String merchantName,
            @Value("${app.payment.pix-merchant-city:SAO PAULO}") String merchantCity
    ) {
        this.webhookSecret = webhookSecret;
        this.pixKey = pixKey;
        this.merchantName = merchantName;
        this.merchantCity = merchantCity;
    }

    @Override
    public GatewayChargeResult createChargeWithSplit(
            String orderCode,
            BigDecimal totalAmount,
            PaymentMethod method,
            List<SplitInstruction> splitInstructions
    ) {
        String txId = "TG_GW_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        Map<UUID, String> splitRefs = new HashMap<>();
        int idx = 1;
        for (SplitInstruction instr : splitInstructions) {
            splitRefs.put(instr.creatorId(), "SPL_" + txId.substring(6, 14) + "_" + idx++);
        }

        String pixCode = null;
        Instant pixExpires = null;
        if (method == PaymentMethod.PIX) {
            pixCode = buildEmvPixBrCode(orderCode, totalAmount);
            pixExpires = Instant.now().plus(Duration.ofMinutes(30));
        }

        return new GatewayChargeResult(txId, pixCode, pixExpires, splitRefs);
    }

    @Override
    public String signWebhookPayload(String gatewayTransactionId, String eventType) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal((gatewayTransactionId + ":" + eventType).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sig);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao assinar webhook de pagamento", e);
        }
    }

    @Override
    public boolean verifyWebhookSignature(String gatewayTransactionId, String eventType, String providedSignature) {
        if (providedSignature == null || providedSignature.isBlank()) return false;
        String expected = signWebhookPayload(gatewayTransactionId, eventType);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                providedSignature.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Generates a standards-compliant Brazilian PIX EMVCo BR Code with CRC16-CCITT (0x1021).
     */
    private String buildEmvPixBrCode(String txRef, BigDecimal amount) {
        String gui = emv("00", "br.gov.bcb.pix");
        String keyField = emv("01", pixKey);
        String merchantAccountInfo = emv("26", gui + keyField);

        String cleanName = merchantName.length() > 25 ? merchantName.substring(0, 25) : merchantName;
        String cleanCity = merchantCity.length() > 15 ? merchantCity.substring(0, 15) : merchantCity;
        String cleanRef = txRef.replaceAll("[^A-Za-z0-9]", "");
        if (cleanRef.length() > 25) cleanRef = cleanRef.substring(0, 25);

        StringBuilder sb = new StringBuilder();
        sb.append(emv("00", "01")); // Payload Format Indicator
        sb.append(merchantAccountInfo);
        sb.append(emv("52", "0000")); // Merchant Category Code
        sb.append(emv("53", "986"));  // Currency BRL (986)
        sb.append(emv("54", String.format(Locale.US, "%.2f", amount)));
        sb.append(emv("58", "BR"));
        sb.append(emv("59", cleanName));
        sb.append(emv("60", cleanCity));
        sb.append(emv("62", emv("05", cleanRef)));
        sb.append("6304"); // CRC16 header

        String crc = crc16Ccitt(sb.toString());
        return sb.append(crc).toString();
    }

    private String emv(String id, String value) {
        return id + String.format("%02d", value.length()) + value;
    }

    private String crc16Ccitt(String payload) {
        int crc = 0xFFFF;
        for (byte b : payload.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x8000) != 0) {
                    crc = (crc << 1) ^ 0x1021;
                } else {
                    crc <<= 1;
                }
                crc &= 0xFFFF;
            }
        }
        return String.format("%04X", crc);
    }
}

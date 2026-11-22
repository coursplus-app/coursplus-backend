package tn.coursplus.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tn.coursplus.entity.Payment;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client pour l'API de paiement Konnect (https://konnect.network) :
 * carte bancaire, e-DINAR et wallet Konnect.
 *
 * Étapes :
 *  1. init-payment  -> Konnect renvoie payUrl + paymentRef
 *  2. l'étudiant paie sur payUrl
 *  3. Konnect appelle notre webhook ?payment_ref=...
 *  4. on redemande le statut à Konnect (on ne fait jamais confiance au webhook seul)
 *
 * ⚠️ Vérifier les noms de champs exacts dans la documentation Konnect avant la mise en production.
 */
@Component
public class KonnectClient {

    private static final Logger log = LoggerFactory.getLogger(KonnectClient.class);

    private final String apiKey;
    private final String walletId;
    private final RestClient http;

    public KonnectClient(@Value("${app.payment.konnect.base-url}") String baseUrl,
                         @Value("${app.payment.konnect.api-key}") String apiKey,
                         @Value("${app.payment.konnect.wallet-id}") String walletId) {
        this.apiKey = apiKey;
        this.walletId = walletId;
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** Konnect est utilisable seulement si la clé API et le wallet sont renseignés. */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && walletId != null && !walletId.isBlank();
    }

    public record InitResult(String payUrl, String paymentRef) {
    }

    public InitResult initPayment(Payment p, String webhookUrl, String successUrl, String failUrl) {
        Map<String, Object> body = new HashMap<>();
        body.put("receiverWalletId", walletId);
        body.put("token", "TND");
        // Konnect attend le montant en millimes (1 TND = 1000 millimes)
        body.put("amount", p.getAmount().multiply(BigDecimal.valueOf(1000)).intValue());
        body.put("type", "immediate");
        body.put("description", p.getLabel());
        body.put("acceptedPaymentMethods", List.of("wallet", "bank_card", "e-DINAR"));
        body.put("lifespan", 30);
        body.put("checkoutForm", false);
        body.put("addPaymentFeesToAmount", false);
        body.put("email", p.getStudent().getEmail());
        body.put("orderId", p.getReference());
        body.put("webhook", webhookUrl);
        body.put("successUrl", successUrl);
        body.put("failUrl", failUrl);
        body.put("theme", "light");

        JsonNode res = http.post().uri("/payments/init-payment")
                .header("x-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        if (res == null || !res.hasNonNull("payUrl")) {
            throw new IllegalStateException("Réponse Konnect inattendue : " + res);
        }
        return new InitResult(res.get("payUrl").asText(), res.path("paymentRef").asText());
    }

    /** Renvoie le statut Konnect du paiement ("completed", "pending", ...). */
    public String getStatus(String paymentRef) {
        try {
            JsonNode res = http.get().uri("/payments/{ref}", paymentRef)
                    .header("x-api-key", apiKey)
                    .retrieve()
                    .body(JsonNode.class);
            return res == null ? "unknown" : res.path("payment").path("status").asText("unknown");
        } catch (Exception e) {
            log.warn("Impossible de lire le statut Konnect de {}", paymentRef, e);
            return "unknown";
        }
    }
}

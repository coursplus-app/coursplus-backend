package tn.coursplus.dto;

import jakarta.validation.constraints.NotNull;
import tn.coursplus.entity.OfferType;
import tn.coursplus.entity.Payment;
import tn.coursplus.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record CheckoutRequest(@NotNull OfferType offerType, @NotNull Long targetId, @NotNull PaymentMethod method) {
    }

    /**
     * Réponse au checkout :
     * - KONNECT : payUrl = page de paiement Konnect (ou page de simulation en dev)
     * - MANUAL  : instructions + référence à mettre dans la note du D17 / virement
     */
    public record CheckoutResponse(PaymentDto payment, String payUrl, String instructions) {
    }

    public record ManualProofRequest(String note) {
    }

    public record PaymentDto(Long id, String reference, String offerType, Long targetId, String label,
                             BigDecimal amount, String method, String status,
                             Long studentId, String studentName, String studentEmail,
                             String studentNote, Instant createdAt, Instant paidAt) {
        public static PaymentDto from(Payment p) {
            return new PaymentDto(p.getId(), p.getReference(), p.getOfferType().name(), p.getTargetId(),
                    p.getLabel(), p.getAmount(), p.getMethod().name(), p.getStatus().name(),
                    p.getStudent().getId(), p.getStudent().getFullName(), p.getStudent().getEmail(),
                    p.getStudentNote(), p.getCreatedAt(), p.getPaidAt());
        }
    }
}

package tn.coursplus.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import tn.coursplus.dto.PaymentDtos.*;
import tn.coursplus.security.CurrentUser;
import tn.coursplus.service.PaymentService;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** Démarre un achat : { "offerType": "COURSE", "targetId": 3, "method": "KONNECT" } */
    @PostMapping("/checkout")
    public CheckoutResponse checkout(@Valid @RequestBody CheckoutRequest req) {
        return paymentService.checkout(CurrentUser.require().id(), req);
    }

    @GetMapping("/{id}")
    public PaymentDto get(@PathVariable Long id) {
        return paymentService.getForStudent(CurrentUser.require().id(), id);
    }

    /** Paiement manuel : l'étudiant colle son numéro de transaction D17. */
    @PostMapping("/{id}/proof")
    public PaymentDto proof(@PathVariable Long id, @RequestBody ManualProofRequest req) {
        return paymentService.addManualProof(CurrentUser.require().id(), id, req.note());
    }

    /** Dev uniquement (app.payment.simulation=true). */
    @PostMapping("/{id}/simulate-success")
    public PaymentDto simulate(@PathVariable Long id) {
        return paymentService.simulateSuccess(CurrentUser.require().id(), id);
    }

    /** Appelé par Konnect quand le statut d'un paiement change. */
    @GetMapping("/konnect/webhook")
    public void konnectWebhook(@RequestParam("payment_ref") String paymentRef) {
        paymentService.handleKonnectWebhook(paymentRef);
    }
}

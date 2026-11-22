package tn.coursplus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.coursplus.dto.PaymentDtos.*;
import tn.coursplus.entity.*;
import tn.coursplus.exception.ApiException;
import tn.coursplus.repository.*;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

/**
 * Paiement d'une offre (cours, abonnement enseignant, abonnement filière).
 *
 * Deux moyens :
 *  - KONNECT : paiement en ligne. Si Konnect n'est pas encore configuré et que
 *    app.payment.simulation=true (profil dev), on affiche une page de simulation.
 *  - MANUAL  : D17 / virement / espèces. L'étudiant reçoit une référence, paie,
 *    colle son numéro de transaction, puis un admin valide.
 *
 * Dans les deux cas, quand le paiement passe à PAID, l'accès est activé automatiquement.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String REF_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final TrackRepository trackRepository;
    private final AccessService accessService;
    private final KonnectClient konnect;
    private final boolean simulation;
    private final String frontendUrl;
    private final String backendUrl;
    private final String manualInstructions;
    private final NotificationService notifications;

    public PaymentService(PaymentRepository paymentRepository, UserRepository userRepository,
                          CourseRepository courseRepository, TrackRepository trackRepository,
                          AccessService accessService, KonnectClient konnect,
                          NotificationService notifications,
                          @Value("${app.payment.simulation}") boolean simulation,
                          @Value("${app.frontend-url}") String frontendUrl,
                          @Value("${app.backend-url}") String backendUrl,
                          @Value("${app.payment.manual-instructions}") String manualInstructions) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.trackRepository = trackRepository;
        this.accessService = accessService;
        this.konnect = konnect;
        this.simulation = simulation;
        this.frontendUrl = frontendUrl;
        this.backendUrl = backendUrl;
        this.manualInstructions = manualInstructions;
        this.notifications = notifications;
    }

    @Transactional
    public CheckoutResponse checkout(Long studentId, CheckoutRequest req) {
        User student = userRepository.findById(studentId).orElseThrow(() -> ApiException.notFound("Utilisateur"));
        if (student.getRole() != Role.STUDENT) {
            throw ApiException.badRequest("Seuls les comptes étudiants peuvent acheter");
        }

        Payment p = new Payment();
        p.setStudent(student);
        p.setOfferType(req.offerType());
        p.setTargetId(req.targetId());
        p.setMethod(req.method());
        p.setReference(newReference());
        fillOffer(p);

        if (req.offerType() == OfferType.COURSE) {
            Course c = courseRepository.findById(req.targetId()).orElseThrow();
            if (accessService.canWatch(studentId, student.getRole(), c)) {
                throw ApiException.badRequest("Tu as déjà accès à ce cours");
            }
        }
        paymentRepository.save(p);

        if (req.method() == PaymentMethod.MANUAL) {
            String instr = manualInstructions + " Référence : " + p.getReference()
                    + " — Montant : " + p.getAmount() + " TND.";
            return new CheckoutResponse(PaymentDto.from(p), null, instr);
        }

        // Paiement en ligne
        if (konnect.isConfigured()) {
            try {
                var res = konnect.initPayment(p,
                        backendUrl + "/api/payments/konnect/webhook",
                        frontendUrl + "/paiement/resultat/" + p.getId(),
                        frontendUrl + "/paiement/resultat/" + p.getId());
                p.setProviderRef(res.paymentRef());
                paymentRepository.save(p);
                return new CheckoutResponse(PaymentDto.from(p), res.payUrl(), null);
            } catch (Exception e) {
                log.error("Erreur Konnect", e);
                p.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(p);
                throw ApiException.badRequest("Le paiement en ligne est indisponible. Essaie le paiement manuel (D17).");
            }
        }
        if (simulation) {
            return new CheckoutResponse(PaymentDto.from(p), frontendUrl + "/paiement/simulation/" + p.getId(), null);
        }
        p.setStatus(PaymentStatus.CANCELLED);
        paymentRepository.save(p);
        throw ApiException.badRequest("Le paiement en ligne n'est pas encore activé. Utilise le paiement manuel (D17).");
    }

    /** Calcule le prix, le libellé et l'enseignant concerné selon l'offre choisie. */
    private void fillOffer(Payment p) {
        switch (p.getOfferType()) {
            case COURSE -> {
                Course c = courseRepository.findById(p.getTargetId())
                        .filter(Course::isPublished)
                        .orElseThrow(() -> ApiException.notFound("Cours"));
                p.setAmount(c.getPrice());
                p.setLabel("Cours : " + c.getTitle());
                p.setTeacher(c.getTeacher());
            }
            case TEACHER_SUB -> {
                User t = userRepository.findById(p.getTargetId())
                        .filter(u -> u.getRole() == Role.TEACHER && u.getMonthlyPrice() != null
                                && u.getApprovalStatus() == ApprovalStatus.APPROVED)
                        .orElseThrow(() -> ApiException.notFound("Abonnement enseignant"));
                p.setAmount(t.getMonthlyPrice());
                p.setLabel("Abonnement 1 mois : " + t.getFullName());
                p.setTeacher(t);
            }
            case TRACK_SUB -> {
                Track tr = trackRepository.findById(p.getTargetId())
                        .filter(x -> x.getMonthlyPrice() != null)
                        .orElseThrow(() -> ApiException.notFound("Abonnement filière"));
                p.setAmount(tr.getMonthlyPrice());
                p.setLabel("Abonnement 1 mois : " + tr.getUniversity().getCode() + " - " + tr.getName());
                p.setTeacher(null); // réparti par l'admin entre les enseignants de la filière
            }
        }
        if (p.getAmount() == null || p.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw ApiException.badRequest("Prix invalide");
        }
    }

    private String newReference() {
        String ref;
        do {
            StringBuilder sb = new StringBuilder("CP-");
            for (int i = 0; i < 6; i++) sb.append(REF_CHARS.charAt(random.nextInt(REF_CHARS.length())));
            ref = sb.toString();
        } while (paymentRepository.findByReference(ref).isPresent());
        return ref;
    }

    /** Passe le paiement à PAID et active l'accès (une seule fois). */
    @Transactional
    public Payment markPaid(Payment p) {
        if (p.getStatus() == PaymentStatus.PAID) return p;
        p.setStatus(PaymentStatus.PAID);
        p.setPaidAt(Instant.now());
        paymentRepository.save(p);
        accessService.grant(p);

        String link = p.getOfferType() == OfferType.COURSE ? "/cours/" + p.getTargetId() + "/apprendre" : "/mon-espace";
        notifications.notifyUser(p.getStudent().getId(), "PAYMENT_PAID", "Paiement confirmé",
                "Ton accès est activé : " + p.getLabel(), link);
        if (p.getTeacher() != null) {
            notifications.notifyUser(p.getTeacher().getId(), "NEW_SALE", "Nouvelle vente",
                    p.getStudent().getFullName() + " a acheté : " + p.getLabel(), "/enseignant");
        }
        return p;
    }

    /** Webhook Konnect : on vérifie le statut directement chez Konnect. */
    @Transactional
    public void handleKonnectWebhook(String paymentRef) {
        paymentRepository.findByProviderRef(paymentRef).ifPresent(p -> {
            String status = konnect.getStatus(paymentRef);
            if ("completed".equalsIgnoreCase(status)) {
                markPaid(p);
            } else if ("failed".equalsIgnoreCase(status) || "expired".equalsIgnoreCase(status)) {
                if (p.getStatus() == PaymentStatus.PENDING) {
                    p.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(p);
                }
            }
        });
    }

    /** Page de résultat : si Konnect est configuré, on revérifie le statut au retour. */
    @Transactional
    public PaymentDto getForStudent(Long studentId, Long paymentId) {
        Payment p = ownPayment(studentId, paymentId);
        if (p.getStatus() == PaymentStatus.PENDING && p.getProviderRef() != null && konnect.isConfigured()) {
            handleKonnectWebhook(p.getProviderRef());
        }
        return PaymentDto.from(p);
    }

    /** Uniquement en dev : simule un paiement en ligne réussi. */
    @Transactional
    public PaymentDto simulateSuccess(Long studentId, Long paymentId) {
        if (!simulation) {
            throw ApiException.forbidden("Simulation désactivée");
        }
        Payment p = ownPayment(studentId, paymentId);
        if (p.getMethod() != PaymentMethod.KONNECT || p.getStatus() != PaymentStatus.PENDING) {
            throw ApiException.badRequest("Ce paiement ne peut pas être simulé");
        }
        return PaymentDto.from(markPaid(p));
    }

    /** L'étudiant colle son numéro de transaction D17 / virement. */
    @Transactional
    public PaymentDto addManualProof(Long studentId, Long paymentId, String note) {
        Payment p = ownPayment(studentId, paymentId);
        if (p.getMethod() != PaymentMethod.MANUAL || p.getStatus() != PaymentStatus.PENDING) {
            throw ApiException.badRequest("Ce paiement n'attend pas de justificatif");
        }
        p.setStudentNote(note == null ? null : note.trim());
        paymentRepository.save(p);
        notifications.notifyAdmins("PAYMENT_PROOF", "Paiement manuel à valider",
                p.getStudent().getFullName() + " : " + p.getAmount() + " TND (réf. " + p.getReference() + ")",
                "/admin?tab=payments");
        return PaymentDto.from(p);
    }

    public List<PaymentDto> myPayments(Long studentId) {
        return paymentRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream().map(PaymentDto::from).toList();
    }

    // ---------- Admin ----------

    @Transactional
    public PaymentDto adminConfirm(Long paymentId) {
        Payment p = paymentRepository.findById(paymentId).orElseThrow(() -> ApiException.notFound("Paiement"));
        if (p.getStatus() != PaymentStatus.PENDING) {
            throw ApiException.badRequest("Ce paiement n'est plus en attente");
        }
        return PaymentDto.from(markPaid(p));
    }

    @Transactional
    public PaymentDto adminReject(Long paymentId) {
        Payment p = paymentRepository.findById(paymentId).orElseThrow(() -> ApiException.notFound("Paiement"));
        if (p.getStatus() != PaymentStatus.PENDING) {
            throw ApiException.badRequest("Ce paiement n'est plus en attente");
        }
        p.setStatus(PaymentStatus.CANCELLED);
        paymentRepository.save(p);
        notifications.notifyUser(p.getStudent().getId(), "PAYMENT_REJECTED", "Paiement refusé",
                "Ton paiement " + p.getReference() + " n'a pas pu être validé. Contacte-nous si tu penses qu'il y a une erreur.",
                "/mon-espace");
        return PaymentDto.from(p);
    }

    private Payment ownPayment(Long studentId, Long paymentId) {
        Payment p = paymentRepository.findById(paymentId).orElseThrow(() -> ApiException.notFound("Paiement"));
        if (!p.getStudent().getId().equals(studentId)) {
            throw ApiException.notFound("Paiement");
        }
        return p;
    }
}

package tn.coursplus.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.coursplus.dto.CatalogDtos.CourseCardDto;
import tn.coursplus.dto.MeDtos.*;
import tn.coursplus.dto.PaymentDtos.PaymentDto;
import tn.coursplus.repository.AccessRepository;
import tn.coursplus.security.AuthUser;
import tn.coursplus.security.CurrentUser;
import tn.coursplus.service.AccessService;
import tn.coursplus.service.PaymentService;

import java.util.List;

/** "Mon espace" étudiant : mes cours, mes abonnements, mes paiements. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final AccessService accessService;
    private final AccessRepository accessRepository;
    private final PaymentService paymentService;

    public MeController(AccessService accessService, AccessRepository accessRepository, PaymentService paymentService) {
        this.accessService = accessService;
        this.accessRepository = accessRepository;
        this.paymentService = paymentService;
    }

    @GetMapping("/learning")
    public MyLearningDto learning() {
        AuthUser me = CurrentUser.require();
        List<CourseCardDto> courses = accessService.accessibleCourses(me.id(), me.role()).stream()
                .map(CourseCardDto::from).toList();
        List<AccessDto> accesses = accessRepository.findByStudentId(me.id()).stream()
                .map(a -> new AccessDto(a.getId(), a.getType().name(), AccessService.label(a),
                        a.getStartsAt(), a.getEndsAt(), a.isActive()))
                .toList();
        return new MyLearningDto(courses, accesses);
    }

    @GetMapping("/payments")
    public List<PaymentDto> payments() {
        return paymentService.myPayments(CurrentUser.require().id());
    }
}

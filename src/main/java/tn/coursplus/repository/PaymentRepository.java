package tn.coursplus.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.coursplus.entity.Payment;
import tn.coursplus.entity.PaymentStatus;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByReference(String reference);
    Optional<Payment> findByProviderRef(String providerRef);
    List<Payment> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<Payment> findByStatusOrderByCreatedAtDesc(PaymentStatus status);
    List<Payment> findAllByOrderByCreatedAtDesc();
    List<Payment> findByTeacherIdAndStatusOrderByCreatedAtDesc(Long teacherId, PaymentStatus status);
}

package tn.coursplus.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.coursplus.entity.Role;
import tn.coursplus.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<User> findByRoleOrderByFullNameAsc(Role role);
    long countByRole(Role role);
    long countByRoleAndApprovalStatus(Role role, tn.coursplus.entity.ApprovalStatus status);
}

package tn.coursplus.dto;

import tn.coursplus.entity.User;

import java.math.BigDecimal;

public record UserDto(Long id, String fullName, String email, String role,
                      Long universityId, String universityCode,
                      String specialty, String bio, BigDecimal monthlyPrice, boolean active,
                      String approvalStatus, String credentialsLink, String reviewNote) {

    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getFullName(), u.getEmail(), u.getRole().name(),
                u.getUniversity() == null ? null : u.getUniversity().getId(),
                u.getUniversity() == null ? null : u.getUniversity().getCode(),
                u.getSpecialty(), u.getBio(), u.getMonthlyPrice(), u.isActive(),
                u.getApprovalStatus().name(), u.getCredentialsLink(), u.getReviewNote());
    }
}

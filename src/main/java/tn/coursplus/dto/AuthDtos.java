package tn.coursplus.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "obligatoire") String fullName,
            @NotBlank(message = "obligatoire") @Email(message = "email invalide") String email,
            @NotBlank(message = "obligatoire") @Size(min = 6, message = "6 caractères minimum") String password,
            Long universityId) {
    }

    /** Candidature enseignant : le compte reste en attente jusqu'à validation par l'admin. */
    public record TeacherRegisterRequest(
            @NotBlank(message = "obligatoire") String fullName,
            @NotBlank(message = "obligatoire") @Email(message = "email invalide") String email,
            @NotBlank(message = "obligatoire") @Size(min = 6, message = "6 caractères minimum") String password,
            @NotBlank(message = "obligatoire") @Size(max = 120) String specialty,
            @NotBlank(message = "obligatoire") @Size(min = 20, max = 2000, message = "20 caractères minimum") String bio,
            @Size(max = 500) String credentialsLink,
            Long universityId) {
    }

    public record LoginRequest(
            @NotBlank(message = "obligatoire") String email,
            @NotBlank(message = "obligatoire") String password) {
    }

    public record AuthResponse(String token, UserDto user) {
    }
}

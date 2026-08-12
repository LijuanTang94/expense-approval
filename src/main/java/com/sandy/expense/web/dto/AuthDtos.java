package com.sandy.expense.web.dto;

import com.sandy.expense.domain.Role;
import com.sandy.expense.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Authentication request/response payloads. */
public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, message = "password must be at least 8 characters") String password,
            @NotBlank String fullName,
            @NotNull Role role,
            Long departmentId) {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

    public record AuthResponse(String token, String tokenType, long expiresInSeconds, UserView user) {}

    public record UserView(
            Long id, String email, String fullName, Role role, Long departmentId, String departmentName) {

        public static UserView of(User u) {
            return new UserView(
                    u.getId(),
                    u.getEmail(),
                    u.getFullName(),
                    u.getRole(),
                    u.getDepartment() != null ? u.getDepartment().getId() : null,
                    u.getDepartment() != null ? u.getDepartment().getName() : null);
        }
    }
}

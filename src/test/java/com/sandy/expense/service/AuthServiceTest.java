package com.sandy.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.sandy.expense.domain.Role;
import com.sandy.expense.domain.User;
import com.sandy.expense.repo.DepartmentRepository;
import com.sandy.expense.repo.UserRepository;
import com.sandy.expense.security.JwtService;
import com.sandy.expense.web.dto.AuthDtos.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Registration must never let a caller choose their own privilege level. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository users;
    @Mock DepartmentRepository departments;
    @Mock PasswordEncoder encoder;
    @Mock JwtService jwt;
    @Mock AuthenticationManager authManager;
    @InjectMocks AuthService service;

    /**
     * The registration DTO deliberately has no role field, so the only thing standing between an
     * anonymous caller and a FINANCE account is this assignment. If someone ever "helpfully" wires
     * a client-supplied role back through, this test fails.
     */
    @Test
    void selfRegistrationAlwaysCreatesAnEmployee() {
        when(users.existsByEmail(anyString())).thenReturn(false);
        when(encoder.encode(anyString())).thenReturn("hashed");
        when(jwt.issue(any())).thenReturn("token");

        service.register(new RegisterRequest("new@acme.com", "password123", "New Person", null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(users).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
    }
}

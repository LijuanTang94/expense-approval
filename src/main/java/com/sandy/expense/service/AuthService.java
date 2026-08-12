package com.sandy.expense.service;

import com.sandy.expense.domain.Department;
import com.sandy.expense.domain.User;
import com.sandy.expense.repo.DepartmentRepository;
import com.sandy.expense.repo.UserRepository;
import com.sandy.expense.security.JwtService;
import com.sandy.expense.web.dto.AuthDtos.AuthResponse;
import com.sandy.expense.web.dto.AuthDtos.LoginRequest;
import com.sandy.expense.web.dto.AuthDtos.RegisterRequest;
import com.sandy.expense.web.dto.AuthDtos.UserView;
import com.sandy.expense.web.error.ApiException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final DepartmentRepository departments;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuthenticationManager authManager;

    public AuthService(
            UserRepository users,
            DepartmentRepository departments,
            PasswordEncoder encoder,
            JwtService jwt,
            AuthenticationManager authManager) {
        this.users = users;
        this.departments = departments;
        this.encoder = encoder;
        this.jwt = jwt;
        this.authManager = authManager;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (users.existsByEmail(req.email())) {
            throw ApiException.conflict("EMAIL_TAKEN", "That email is already registered");
        }
        Department dept = null;
        if (req.departmentId() != null) {
            dept =
                    departments
                            .findById(req.departmentId())
                            .orElseThrow(() -> ApiException.badRequest("BAD_DEPARTMENT", "Unknown department"));
        }
        User u = new User();
        u.setEmail(req.email());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setFullName(req.fullName());
        u.setRole(req.role());
        u.setDepartment(dept);
        users.save(u);
        return new AuthResponse(jwt.issue(u), "Bearer", jwt.ttlSeconds(), UserView.of(u));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        } catch (BadCredentialsException e) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Invalid email or password");
        }
        User u = users.findByEmail(req.email()).orElseThrow();
        return new AuthResponse(jwt.issue(u), "Bearer", jwt.ttlSeconds(), UserView.of(u));
    }

    @Transactional(readOnly = true)
    public UserView me(Long userId) {
        return users.findById(userId).map(UserView::of).orElseThrow(() -> ApiException.notFound("User not found"));
    }
}

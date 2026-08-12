package com.sandy.expense.web;

import com.sandy.expense.security.AppUserPrincipal;
import com.sandy.expense.service.AuthService;
import com.sandy.expense.web.dto.AuthDtos.AuthResponse;
import com.sandy.expense.web.dto.AuthDtos.LoginRequest;
import com.sandy.expense.web.dto.AuthDtos.RegisterRequest;
import com.sandy.expense.web.dto.AuthDtos.UserView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal AppUserPrincipal me) {
        return auth.me(me.getUserId());
    }
}

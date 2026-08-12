package com.sandy.expense.security;

import com.sandy.expense.domain.Role;
import com.sandy.expense.domain.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated principal placed in the SecurityContext. Carries the identity fields the
 * app authorises on (id, role). Two construction paths: from a DB {@link User} (login, includes
 * the password hash) and from verified JWT claims (per-request, no DB hit, no password).
 */
public class AppUserPrincipal implements UserDetails {

    private final Long userId;
    private final String email;
    private final String passwordHash; // nullable when built from a token
    private final Role role;
    private final Long departmentId; // nullable

    public AppUserPrincipal(Long userId, String email, String passwordHash, Role role, Long departmentId) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.departmentId = departmentId;
    }

    public static AppUserPrincipal fromUser(User u) {
        return new AppUserPrincipal(
                u.getId(),
                u.getEmail(),
                u.getPasswordHash(),
                u.getRole(),
                u.getDepartment() != null ? u.getDepartment().getId() : null);
    }

    public Long getUserId() {
        return userId;
    }

    public Role getRole() {
        return role;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}

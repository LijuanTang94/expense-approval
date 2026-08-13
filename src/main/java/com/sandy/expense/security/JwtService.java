package com.sandy.expense.security;

import com.sandy.expense.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Issues and verifies stateless HS256 access tokens (subject = user id, "role" claim). */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /** HS256 needs a key of at least 256 bits. */
    private static final int MIN_SECRET_BYTES = 32;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.key = resolveKey(secret);
        this.ttl = Duration.ofMinutes(expirationMinutes);
    }

    /**
     * Never fall back to a hardcoded secret: a signing key committed to the repo is public, and
     * anyone holding it can forge a token for any user and role. So either the operator supplies a
     * strong secret, or we generate a random one for this JVM only (dev convenience — tokens simply
     * don't survive a restart). A supplied-but-weak secret fails fast rather than silently
     * downgrading security.
     */
    private static SecretKey resolveKey(String secret) {
        if (secret == null || secret.isBlank()) {
            log.warn(
                    "APP_JWT_SECRET is not set — generating a random signing key for this process. "
                            + "Tokens will be invalidated on restart. Set APP_JWT_SECRET in any real deployment.");
            return Jwts.SIG.HS256.key().build();
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET must be at least "
                            + MIN_SECRET_BYTES
                            + " bytes for HS256 (got "
                            + bytes.length
                            + ")");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String issue(User user) {
        Instant now = Instant.now();
        var builder =
                Jwts.builder()
                        .subject(String.valueOf(user.getId()))
                        .claim("email", user.getEmail())
                        .claim("role", user.getRole().name());
        if (user.getDepartment() != null) {
            builder.claim("dept", user.getDepartment().getId());
        }
        return builder
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }

    /** Parse and verify a token; returns its claims, or null if invalid/expired. */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}

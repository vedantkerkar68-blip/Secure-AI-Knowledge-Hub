package com.sakh.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Represents a revoked JWT token identified by its jti claim.
 */
@Entity
@Table(name = "jwt_revocations")
@Getter
@Setter
@NoArgsConstructor
public class JwtRevocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "token_jti", length = 64, unique = true, nullable = false)
    private String tokenJti;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "reason", length = 50, nullable = false)
    private String reason;

    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt;
}
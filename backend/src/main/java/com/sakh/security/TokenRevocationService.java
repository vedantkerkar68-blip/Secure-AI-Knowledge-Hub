package com.sakh.security;

import com.sakh.entity.JwtRevocation;
import com.sakh.repository.JwtRevocationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Manages the set of revoked JWT tokens. Revocations are persisted so they
 * survive restarts and work across multiple application instances.
 */
@Service
public class TokenRevocationService {

    private static final Logger logger = LoggerFactory.getLogger(TokenRevocationService.class);

    private final JwtRevocationRepository repository;
    private final JwtService jwtService;

    public TokenRevocationService(JwtRevocationRepository repository, JwtService jwtService) {
        this.repository = repository;
        this.jwtService = jwtService;
    }

    @Transactional
    public void revoke(String token, Long userId, String reason) {
        try {
            String jti = jwtService.extractTokenId(token);
            if (jti == null) {
                return;
            }
            revokeJti(jti, userId, reason);
        } catch (Exception e) {
            logger.warn("Failed to extract jti for revocation: {}", e.getMessage());
        }
    }

    @Transactional
    public void revokeJti(String tokenJti, Long userId, String reason) {
        if (tokenJti == null || repository.existsByTokenJti(tokenJti)) {
            return;
        }
        JwtRevocation revocation = new JwtRevocation();
        revocation.setTokenJti(tokenJti);
        revocation.setUserId(userId);
        revocation.setReason(reason);
        revocation.setRevokedAt(Instant.now());
        repository.save(revocation);
    }

    public boolean isRevoked(String tokenJti) {
        return tokenJti != null && repository.existsByTokenJti(tokenJti);
    }
}
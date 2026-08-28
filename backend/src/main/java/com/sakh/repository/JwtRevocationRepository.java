package com.sakh.repository;

import com.sakh.entity.JwtRevocation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JwtRevocationRepository extends JpaRepository<JwtRevocation, Long> {

    boolean existsByTokenJti(String tokenJti);
}
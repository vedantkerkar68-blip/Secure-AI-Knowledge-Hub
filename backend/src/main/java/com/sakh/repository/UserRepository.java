package com.sakh.repository;

import java.util.Optional;

import com.sakh.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for accessing user records.
 */
public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);
}

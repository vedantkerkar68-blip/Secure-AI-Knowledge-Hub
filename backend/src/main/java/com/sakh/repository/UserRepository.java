package com.sakh.repository;

import com.sakh.entity.User;
import com.sakh.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * Repository for accessing user records.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE " +
            "(?1 IS NULL OR u.firstName ILIKE %?1% OR u.lastName ILIKE %?1% OR u.email ILIKE %?1%) AND " +
            "(?2 IS NULL OR u.role.name = ?2) AND " +
            "(?3 IS NULL OR u.department.name = ?3) AND " +
            "(?4 IS NULL OR u.status = ?4)")
    Page<User> findWithFilters(String search, String role, String department, UserStatus status, Pageable pageable);
}

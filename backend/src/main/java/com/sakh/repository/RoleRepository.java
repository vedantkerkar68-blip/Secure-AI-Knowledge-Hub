package com.sakh.repository;

import com.sakh.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for accessing role records.
 */
public interface RoleRepository extends JpaRepository<Role, Long> {
}

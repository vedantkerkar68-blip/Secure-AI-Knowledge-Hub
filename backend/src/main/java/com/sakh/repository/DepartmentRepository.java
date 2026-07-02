package com.sakh.repository;

import com.sakh.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for accessing department records.
 */
public interface DepartmentRepository extends JpaRepository<Department, Long> {
}

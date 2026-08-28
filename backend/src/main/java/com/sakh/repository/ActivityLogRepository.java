package com.sakh.repository;

import com.sakh.entity.ActivityLog;
import com.sakh.enums.ActivityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long>, JpaSpecificationExecutor<ActivityLog> {

    Page<ActivityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByAction(ActivityType action);

    long deleteByCreatedAtBefore(Instant cutoff);
}
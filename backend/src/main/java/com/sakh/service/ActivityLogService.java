package com.sakh.service;

import com.sakh.dto.ActivityLogResponse;
import com.sakh.dto.ActivityLogSummaryResponse;
import com.sakh.entity.ActivityLog;
import com.sakh.entity.User;
import com.sakh.enums.ActivityType;
import com.sakh.repository.ActivityLogRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for recording and querying the system activity audit trail.
 */
@Service
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    public ActivityLogService(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    public void log(User user, ActivityType action, String resource) {
        ActivityLog log = new ActivityLog();
        log.setUserId(user.getId());
        log.setUserEmail(user.getEmail());
        log.setAction(action);
        log.setResource(resource);
        log.setIpAddress(resolveClientIp());
        log.setCreatedAt(Instant.now());
        activityLogRepository.save(log);
    }

    public Page<ActivityLogResponse> getAll(ActivityType action, String search, Instant from, Instant to, Pageable pageable) {
        Specification<ActivityLog> spec = buildSpecification(action, search, from, to);
        return activityLogRepository.findAll(spec, pageable)
                .map(this::toResponse);
    }

    public ActivityLogSummaryResponse getSummary() {
        Map<String, Long> byAction = new LinkedHashMap<>();
        for (ActivityType type : ActivityType.values()) {
            long count = activityLogRepository.countByAction(type);
            if (count > 0) {
                byAction.put(type.name(), count);
            }
        }
        return ActivityLogSummaryResponse.builder()
                .total(activityLogRepository.count())
                .byAction(byAction)
                .build();
    }

    /**
     * Deletes activity logs older than the given retention period.
     *
     * @param days minimum retention in days (logs older than this are removed)
     * @return the number of logs deleted
     */
    @Transactional
    public long deleteOlderThan(int days) {
        if (days < 30) {
            throw new IllegalArgumentException("Retention must be at least 30 days.");
        }
        Instant cutoff = Instant.now().minus(java.time.Duration.ofDays(days));
        return activityLogRepository.deleteByCreatedAtBefore(cutoff);
    }

    private Specification<ActivityLog> buildSpecification(ActivityType action, String search, Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }

            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("userEmail")), like),
                        cb.like(cb.lower(root.get("resource")), like)
                ));
            }

            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }

            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private ActivityLogResponse toResponse(ActivityLog log) {
        return ActivityLogResponse.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .userEmail(log.getUserEmail())
                .action(log.getAction())
                .resource(log.getResource())
                .ipAddress(log.getIpAddress())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private static String resolveClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attrs.getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isBlank()) {
                ip = request.getRemoteAddr();
            } else {
                ip = ip.split(",")[0].trim();
            }
            return ip;
        } catch (Exception e) {
            return null;
        }
    }
}
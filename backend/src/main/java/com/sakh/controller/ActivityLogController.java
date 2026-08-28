package com.sakh.controller;

import com.sakh.dto.ActivityLogResponse;
import com.sakh.dto.ActivityLogSummaryResponse;
import com.sakh.enums.ActivityType;
import com.sakh.service.ActivityLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@Tag(name = "Activity", description = "Activity log audit trail for admin monitoring")
@SecurityRequirement(name = "JWT")
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    public ActivityLogController(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @GetMapping("/activity")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get activity logs", description = "Returns a paginated, filterable list of all user activities (admin only)")
    public ResponseEntity<Page<ActivityLogResponse>> getActivityLogs(
            @RequestParam(required = false) ActivityType action,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(activityLogService.getAll(action, search, from, to, pageable));
    }

    @GetMapping("/activity/summary")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get activity summary", description = "Returns total activity count and counts per action type (admin only)")
    public ResponseEntity<ActivityLogSummaryResponse> getActivitySummary() {
        return ResponseEntity.ok(activityLogService.getSummary());
    }

    @DeleteMapping("/activity")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete old activity logs", description = "Deletes activity logs older than the given retention in days (admin only). Minimum 30 days, 'never' is simply not calling this.")
    public ResponseEntity<Map<String, Long>> deleteOldActivity(
            @RequestParam("olderThanDays") int olderThanDays) {
        long deleted = activityLogService.deleteOlderThan(olderThanDays);
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }
}
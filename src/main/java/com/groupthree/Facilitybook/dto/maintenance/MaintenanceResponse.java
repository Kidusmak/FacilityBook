package com.groupthree.Facilitybook.dto.maintenance;

import com.groupthree.Facilitybook.dto.resource.ResourceSummary;
import com.groupthree.Facilitybook.dto.user.UserSummary;
import com.groupthree.Facilitybook.entity.MaintenanceRecord;
import com.groupthree.Facilitybook.entity.enums.MaintenanceStatus;

import java.time.Instant;

public record MaintenanceResponse(
        Long id,
        ResourceSummary resource,
        UserSummary reportedBy,
        String reason,
        MaintenanceStatus status,
        Instant startTime,
        Instant endTime,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static MaintenanceResponse from(MaintenanceRecord record) {
        return new MaintenanceResponse(
                record.getId(),
                ResourceSummary.from(record.getResource()),
                UserSummary.from(record.getReportedBy()),
                record.getReason(),
                record.getStatus(),
                record.getStartTime(),
                record.getEndTime(),
                record.getCompletedAt(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }
}

package com.groupthree.Facilitybook.dto.report;

import java.time.Instant;

public record ReportSummaryResponse(
        Instant from,
        Instant to,
        long totalReservations,
        long confirmed,
        long pendingApproval,
        long cancelled,
        long rejected,
        long flaggedForResolution,
        long activeResources,
        long maintenanceRecords,
        double bookedHours,
        double maintenanceDowntimeHours) {
}

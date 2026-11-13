package com.groupthree.Facilitybook.dto.report;

public record ResourceUsageResponse(
        Long resourceId,
        String resourceName,
        long reservationCount,
        double bookedHours,
        double utilizationPercent) {
}

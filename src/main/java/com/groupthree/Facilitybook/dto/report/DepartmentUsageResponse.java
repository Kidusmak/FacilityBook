package com.groupthree.Facilitybook.dto.report;

public record DepartmentUsageResponse(
        Long departmentId,
        String departmentName,
        long reservationCount) {
}

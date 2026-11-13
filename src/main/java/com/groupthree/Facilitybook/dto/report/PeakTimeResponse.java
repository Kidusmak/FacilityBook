package com.groupthree.Facilitybook.dto.report;

public record PeakTimeResponse(
        int hour,
        long reservationCount) {
}

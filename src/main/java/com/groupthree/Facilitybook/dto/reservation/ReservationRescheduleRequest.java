package com.groupthree.Facilitybook.dto.reservation;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ReservationRescheduleRequest(

        @NotNull(message = "Start time is required")
        Instant startTime,

        @NotNull(message = "End time is required")
        Instant endTime) {
}

package com.groupthree.Facilitybook.dto.reservation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ReservationCreateRequest(

        @NotNull(message = "Resource is required")
        Long resourceId,

        @NotBlank(message = "Purpose is required")
        @Size(max = 300, message = "Purpose must be at most 300 characters")
        String purpose,

        @Size(max = 2000, message = "Notes must be at most 2000 characters")
        String notes,

        @NotNull(message = "Start time is required")
        Instant startTime,

        @NotNull(message = "End time is required")
        Instant endTime) {
}

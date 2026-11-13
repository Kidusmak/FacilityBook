package com.groupthree.Facilitybook.dto.resource;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record OperatingHourRequest(

        @NotNull(message = "Day of week is required")
        @Min(value = 1, message = "Day of week must be 1 (Monday) to 7 (Sunday)")
        @Max(value = 7, message = "Day of week must be 1 (Monday) to 7 (Sunday)")
        Short dayOfWeek,

        @NotNull(message = "Opening time is required")
        LocalTime openTime,

        @NotNull(message = "Closing time is required")
        LocalTime closeTime) {
}

package com.groupthree.Facilitybook.dto.resource;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OperatingHoursRequest(

        @NotNull(message = "Operating hours are required")
        @Size(max = 7, message = "At most one operating-hours row per weekday is allowed")
        List<@Valid OperatingHourRequest> hours) {
}

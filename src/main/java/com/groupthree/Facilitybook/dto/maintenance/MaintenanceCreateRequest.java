package com.groupthree.Facilitybook.dto.maintenance;

import com.groupthree.Facilitybook.entity.enums.MaintenanceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record MaintenanceCreateRequest(

        @NotNull(message = "Resource is required")
        Long resourceId,

        @NotBlank(message = "Maintenance reason is required")
        @Size(max = 2000, message = "Reason must be at most 2000 characters")
        String reason,

        @NotNull(message = "Start time is required")
        Instant startTime,

        @NotNull(message = "End time is required")
        Instant endTime,

        MaintenanceStatus status) {
}

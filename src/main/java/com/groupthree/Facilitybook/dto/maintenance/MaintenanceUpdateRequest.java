package com.groupthree.Facilitybook.dto.maintenance;

import com.groupthree.Facilitybook.entity.enums.MaintenanceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MaintenanceUpdateRequest(

        @NotNull(message = "Status is required")
        MaintenanceStatus status,

        @Size(max = 2000, message = "Reason must be at most 2000 characters")
        String reason) {
}

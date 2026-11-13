package com.groupthree.Facilitybook.dto.reservation;

import com.groupthree.Facilitybook.entity.enums.ConflictResolutionAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResolveConflictRequest(

        @NotNull(message = "Action is required")
        ConflictResolutionAction action,

        @Size(max = 300, message = "Note must be at most 300 characters")
        String note) {
}

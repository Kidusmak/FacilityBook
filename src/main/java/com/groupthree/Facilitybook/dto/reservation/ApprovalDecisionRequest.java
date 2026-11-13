package com.groupthree.Facilitybook.dto.reservation;

import com.groupthree.Facilitybook.entity.enums.ApprovalDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApprovalDecisionRequest(

        @NotNull(message = "Decision is required")
        ApprovalDecision decision,

        @Size(max = 500, message = "Reason must be at most 500 characters")
        String reason) {
}

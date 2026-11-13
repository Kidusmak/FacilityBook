package com.groupthree.Facilitybook.dto.reservation;

import com.groupthree.Facilitybook.dto.user.UserSummary;
import com.groupthree.Facilitybook.entity.ReservationApproval;
import com.groupthree.Facilitybook.entity.enums.ApprovalDecision;

import java.time.Instant;

public record ApprovalResponse(
        Long id,
        UserSummary approver,
        ApprovalDecision decision,
        String reason,
        Instant decidedAt) {

    public static ApprovalResponse from(ReservationApproval approval) {
        return new ApprovalResponse(
                approval.getId(),
                UserSummary.from(approval.getApprover()),
                approval.getDecision(),
                approval.getReason(),
                approval.getDecidedAt());
    }
}

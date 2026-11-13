package com.groupthree.Facilitybook.dto.reservation;

import com.groupthree.Facilitybook.dto.resource.ResourceSummary;
import com.groupthree.Facilitybook.dto.user.UserSummary;
import com.groupthree.Facilitybook.entity.Reservation;
import com.groupthree.Facilitybook.entity.ReservationApproval;
import com.groupthree.Facilitybook.entity.enums.ReservationStatus;

import java.time.Instant;
import java.util.List;

public record ReservationResponse(
        Long id,
        ResourceSummary resource,
        UserSummary requester,
        String purpose,
        String notes,
        Instant startTime,
        Instant endTime,
        ReservationStatus status,
        boolean needsResolution,
        Instant cancelledAt,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt,
        List<ApprovalResponse> approvals) {

    public static ReservationResponse from(Reservation reservation) {
        return from(reservation, List.of());
    }

    public static ReservationResponse from(Reservation reservation, List<ReservationApproval> approvals) {
        return new ReservationResponse(
                reservation.getId(),
                ResourceSummary.from(reservation.getResource()),
                UserSummary.from(reservation.getRequester()),
                reservation.getPurpose(),
                reservation.getNotes(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getStatus(),
                reservation.isNeedsResolution(),
                reservation.getCancelledAt(),
                reservation.getCancellationReason(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt(),
                approvals.stream().map(ApprovalResponse::from).toList());
    }
}

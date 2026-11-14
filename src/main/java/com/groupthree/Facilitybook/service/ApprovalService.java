package com.groupthree.Facilitybook.service;

import com.groupthree.Facilitybook.dto.common.PageResponse;
import com.groupthree.Facilitybook.dto.reservation.ApprovalDecisionRequest;
import com.groupthree.Facilitybook.dto.reservation.ReservationResponse;
import com.groupthree.Facilitybook.entity.MaintenanceRecord;
import com.groupthree.Facilitybook.entity.Reservation;
import com.groupthree.Facilitybook.entity.ReservationApproval;
import com.groupthree.Facilitybook.entity.ResourceCategory;
import com.groupthree.Facilitybook.entity.User;
import com.groupthree.Facilitybook.entity.enums.ApprovalDecision;
import com.groupthree.Facilitybook.entity.enums.NotificationType;
import com.groupthree.Facilitybook.entity.enums.ReservationStatus;
import com.groupthree.Facilitybook.exception.BadRequestException;
import com.groupthree.Facilitybook.exception.ConflictException;
import com.groupthree.Facilitybook.exception.NotFoundException;
import com.groupthree.Facilitybook.repository.MaintenanceRecordRepository;
import com.groupthree.Facilitybook.repository.ReservationApprovalRepository;
import com.groupthree.Facilitybook.repository.ReservationRepository;
import com.groupthree.Facilitybook.repository.UserRepository;
import com.groupthree.Facilitybook.security.CurrentUser;
import com.groupthree.Facilitybook.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ApprovalService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_FACILITY_MANAGER = "FACILITY_MANAGER";
    private static final String ROLE_DEPARTMENT_MANAGER = "DEPARTMENT_MANAGER";

    private final ReservationRepository reservationRepository;
    private final ReservationApprovalRepository approvalRepository;
    private final MaintenanceRecordRepository maintenanceRecordRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final CurrentUser currentUser;

    public ApprovalService(ReservationRepository reservationRepository,
                           ReservationApprovalRepository approvalRepository,
                           MaintenanceRecordRepository maintenanceRecordRepository,
                           UserRepository userRepository,
                           NotificationService notificationService,
                           CurrentUser currentUser) {
        this.reservationRepository = reservationRepository;
        this.approvalRepository = approvalRepository;
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.currentUser = currentUser;
    }

    /**
     * Pending requests visible to the current approver. Department managers only see
     * requests raised by their own department; facility managers see facility-routed
     * requests; admins see everything.
     */
    public PageResponse<ReservationResponse> pending(Pageable pageable) {
        UserPrincipal principal = currentUser.require();
        Page<Reservation> page;

        if (ROLE_ADMIN.equals(principal.getRole())) {
            page = reservationRepository.findByStatus(ReservationStatus.PENDING_APPROVAL, pageable);
        } else if (ROLE_FACILITY_MANAGER.equals(principal.getRole())) {
            page = reservationRepository.findPendingForApproverRole(
                    ReservationStatus.PENDING_APPROVAL, ROLE_FACILITY_MANAGER, pageable);
        } else if (ROLE_DEPARTMENT_MANAGER.equals(principal.getRole())) {
            if (principal.getDepartmentId() == null) {
                return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(),
                        0, 0, true, true);
            }
            page = reservationRepository.findPendingForApproverRoleAndDepartment(
                    ReservationStatus.PENDING_APPROVAL, ROLE_DEPARTMENT_MANAGER,
                    principal.getDepartmentId(), pageable);
        } else {
            throw new AccessDeniedException("You are not allowed to view pending approvals");
        }

        return PageResponse.from(page, ReservationResponse::from);
    }

    @Transactional
    public ReservationResponse decide(Long reservationId, ApprovalDecisionRequest request) {
        UserPrincipal principal = currentUser.require();
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new NotFoundException(
                        "Reservation " + reservationId + " was not found"));

        if (reservation.getStatus() != ReservationStatus.PENDING_APPROVAL) {
            throw new ConflictException("Only reservations pending approval can be decided");
        }

        ResourceCategory category = reservation.getResource().getCategory();
        if (!category.isRequiresApproval() || category.getApproverRole() == null) {
            throw new ConflictException("This reservation does not require approval");
        }

        String requiredRole = category.getApproverRole().getName();
        boolean admin = ROLE_ADMIN.equals(principal.getRole());

        if (!admin && !requiredRole.equals(principal.getRole())) {
            throw new AccessDeniedException(
                    "This reservation must be decided by a " + requiredRole.replace('_', ' ').toLowerCase());
        }
        if (ROLE_DEPARTMENT_MANAGER.equals(requiredRole) && !admin) {
            Long requesterDepartment = reservation.getRequester().getDepartment() != null
                    ? reservation.getRequester().getDepartment().getId() : null;
            if (principal.getDepartmentId() == null
                    || !principal.getDepartmentId().equals(requesterDepartment)) {
                throw new AccessDeniedException(
                        "Department managers can only approve requests from their own department");
            }
        }
        if (principal.getId().equals(reservation.getRequester().getId())) {
            throw new AccessDeniedException("You cannot approve or reject your own reservation");
        }
        if (request.decision() == ApprovalDecision.REJECTED
                && (request.reason() == null || request.reason().isBlank())) {
            throw new BadRequestException("A reason is required when rejecting a reservation");
        }

        if (request.decision() == ApprovalDecision.APPROVED) {
            List<MaintenanceRecord> maintenance = maintenanceRecordRepository
                    .findByResourceIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                            reservation.getResource().getId(),
                            ReservationService.BLOCKING_MAINTENANCE_STATUSES,
                            reservation.getEndTime(),
                            reservation.getStartTime());
            if (!maintenance.isEmpty()) {
                throw new ConflictException(
                        "The resource is now under maintenance during this period; reject or "
                                + "reschedule the request instead");
            }
        }

        User approver = userRepository.getReferenceById(principal.getId());
        ReservationApproval approval = new ReservationApproval();
        approval.setReservation(reservation);
        approval.setApprover(approver);
        approval.setDecision(request.decision());
        approval.setReason(request.reason() != null ? request.reason().trim() : null);
        approval.setDecidedAt(Instant.now());
        approvalRepository.save(approval);

        if (request.decision() == ApprovalDecision.APPROVED) {
            reservation.setStatus(ReservationStatus.CONFIRMED);
            notificationService.notify(reservation.getRequester(),
                    NotificationType.RESERVATION_APPROVED,
                    "Reservation approved",
                    "Reservation #" + reservation.getId() + " for "
                            + reservation.getResource().getName() + " was approved.",
                    reservation);
        } else {
            reservation.setStatus(ReservationStatus.REJECTED);
            notificationService.notify(reservation.getRequester(),
                    NotificationType.RESERVATION_REJECTED,
                    "Reservation rejected",
                    "Reservation #" + reservation.getId() + " for "
                            + reservation.getResource().getName() + " was rejected: "
                            + request.reason().trim(),
                    reservation);
        }
        reservationRepository.save(reservation);

        return ReservationResponse.from(reservation,
                approvalRepository.findByReservationIdOrderByDecidedAtAsc(reservationId));
    }
}

package com.groupthree.Facilitybook.service;

import com.groupthree.Facilitybook.dto.common.PageResponse;
import com.groupthree.Facilitybook.dto.maintenance.MaintenanceCreateRequest;
import com.groupthree.Facilitybook.dto.maintenance.MaintenanceResponse;
import com.groupthree.Facilitybook.dto.maintenance.MaintenanceUpdateRequest;
import com.groupthree.Facilitybook.entity.MaintenanceRecord;
import com.groupthree.Facilitybook.entity.Reservation;
import com.groupthree.Facilitybook.entity.Resource;
import com.groupthree.Facilitybook.entity.User;
import com.groupthree.Facilitybook.entity.enums.MaintenanceStatus;
import com.groupthree.Facilitybook.entity.enums.NotificationType;
import com.groupthree.Facilitybook.exception.BadRequestException;
import com.groupthree.Facilitybook.exception.ConflictException;
import com.groupthree.Facilitybook.exception.NotFoundException;
import com.groupthree.Facilitybook.repository.MaintenanceRecordRepository;
import com.groupthree.Facilitybook.repository.ReservationRepository;
import com.groupthree.Facilitybook.repository.ResourceRepository;
import com.groupthree.Facilitybook.repository.UserRepository;
import com.groupthree.Facilitybook.security.CurrentUser;
import com.groupthree.Facilitybook.security.UserPrincipal;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class MaintenanceService {

    private static final Set<MaintenanceStatus> BLOCKING_STATUSES =
            EnumSet.of(MaintenanceStatus.REPORTED, MaintenanceStatus.SCHEDULED,
                    MaintenanceStatus.IN_PROGRESS);
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_FACILITY_MANAGER = "FACILITY_MANAGER";
    private static final String ROLE_RESOURCE_OPERATOR = "RESOURCE_OPERATOR";
    private static final Map<MaintenanceStatus, Set<MaintenanceStatus>> ALLOWED_TRANSITIONS =
            buildTransitions();

    private final MaintenanceRecordRepository maintenanceRepository;
    private final ReservationRepository reservationRepository;
    private final ResourceRepository resourceRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final CurrentUser currentUser;

    public MaintenanceService(MaintenanceRecordRepository maintenanceRepository,
                              ReservationRepository reservationRepository,
                              ResourceRepository resourceRepository,
                              UserRepository userRepository,
                              NotificationService notificationService,
                              CurrentUser currentUser) {
        this.maintenanceRepository = maintenanceRepository;
        this.reservationRepository = reservationRepository;
        this.resourceRepository = resourceRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.currentUser = currentUser;
    }

    public PageResponse<MaintenanceResponse> list(MaintenanceStatus status, Long resourceId,
                                                  Instant from, Instant to, Pageable pageable) {
        Specification<MaintenanceRecord> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (resourceId != null) {
                predicates.add(cb.equal(root.get("resource").get("id"), resourceId));
            }
            if (from != null) {
                predicates.add(cb.greaterThan(root.get("endTime"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("startTime"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<MaintenanceRecord> page = maintenanceRepository.findAll(specification, pageable);
        return PageResponse.from(page, MaintenanceResponse::from);
    }

    public MaintenanceResponse get(Long id) {
        return MaintenanceResponse.from(find(id));
    }

    @Transactional
    public MaintenanceResponse create(MaintenanceCreateRequest request) {
        validateWindow(request.startTime(), request.endTime());

        MaintenanceStatus status = request.status() != null
                ? request.status() : MaintenanceStatus.REPORTED;
        if (status != MaintenanceStatus.REPORTED && status != MaintenanceStatus.SCHEDULED) {
            throw new BadRequestException("New maintenance must start as REPORTED or SCHEDULED");
        }
        UserPrincipal principal = currentUser.require();
        // Every employee may report a damaged resource, but only facility staff can
        // pre-schedule a maintenance window.
        if (status == MaintenanceStatus.SCHEDULED
                && !ReservationService.hasAnyRole(principal, ROLE_ADMIN, ROLE_FACILITY_MANAGER,
                        ROLE_RESOURCE_OPERATOR)) {
            throw new AccessDeniedException(
                    "Only facility staff can schedule a maintenance window");
        }

        Resource resource = resourceRepository.findById(request.resourceId())
                .orElseThrow(() -> new NotFoundException(
                        "Resource " + request.resourceId() + " was not found"));
        User reporter = userRepository.getReferenceById(principal.getId());

        MaintenanceRecord record = new MaintenanceRecord();
        record.setResource(resource);
        record.setReportedBy(reporter);
        record.setReason(request.reason().trim());
        record.setStartTime(request.startTime());
        record.setEndTime(request.endTime());
        record.setStatus(status);

        MaintenanceRecord saved = maintenanceRepository.save(record);
        flagConflictingReservations(saved);
        notifyStaff(saved);
        return MaintenanceResponse.from(saved);
    }

    @Transactional
    public MaintenanceResponse update(Long id, MaintenanceUpdateRequest request) {
        MaintenanceRecord record = findForUpdate(id);
        MaintenanceStatus current = record.getStatus();
        MaintenanceStatus next = request.status();

        if (current != next && !ALLOWED_TRANSITIONS.get(current).contains(next)) {
            throw new ConflictException("Cannot change maintenance status from " + current + " to " + next);
        }

        record.setStatus(next);
        if (request.reason() != null && !request.reason().isBlank()) {
            record.setReason(request.reason().trim());
        }
        if (next == MaintenanceStatus.COMPLETED) {
            if (record.getCompletedAt() == null) {
                record.setCompletedAt(Instant.now());
            }
            // Only pull the window end in when the window is currently running; never move the
            // end before the start (that would violate chk_maintenance_times).
            Instant now = Instant.now();
            if (record.getStartTime().isBefore(now) && record.getEndTime().isAfter(now)) {
                record.setEndTime(now);
            }
        }
        MaintenanceRecord saved = maintenanceRepository.save(record);

        if (BLOCKING_STATUSES.contains(next)) {
            flagConflictingReservations(saved);
        }
        notifyStaff(saved);
        return MaintenanceResponse.from(saved);
    }

    /**
     * Existing reservations that overlap a maintenance window are flagged for authorized
     * resolution and their requesters are notified. They are never silently deleted.
     */
    private void flagConflictingReservations(MaintenanceRecord record) {
        List<Reservation> conflicts = reservationRepository
                .findByResourceIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                        record.getResource().getId(),
                        ReservationService.BLOCKING_RESERVATION_STATUSES,
                        record.getEndTime(),
                        record.getStartTime());

        for (Reservation reservation : conflicts) {
            if (!reservation.isNeedsResolution()) {
                reservation.setNeedsResolution(true);
                reservationRepository.save(reservation);
            }
            notificationService.notify(reservation.getRequester(),
                    NotificationType.MAINTENANCE_CONFLICT,
                    "Maintenance affects your reservation",
                    "Reservation #" + reservation.getId() + " for "
                            + reservation.getResource().getName()
                            + " overlaps scheduled maintenance. Facility staff will review it.",
                    reservation);
        }
    }

    private void notifyStaff(MaintenanceRecord record) {
        notificationService.notifyRole("FACILITY_MANAGER", NotificationType.MAINTENANCE_SCHEDULED,
                "Maintenance " + record.getStatus().name().toLowerCase().replace('_', ' '),
                "Resource " + record.getResource().getName() + " has maintenance from "
                        + record.getStartTime() + " to " + record.getEndTime() + ".",
                null);
    }

    private void validateWindow(Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null) {
            throw new BadRequestException("Both startTime and endTime are required");
        }
        if (!endTime.isAfter(startTime)) {
            throw new BadRequestException("endTime must be after startTime");
        }
    }

    private MaintenanceRecord find(Long id) {
        return maintenanceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Maintenance record " + id + " was not found"));
    }

    private MaintenanceRecord findForUpdate(Long id) {
        return maintenanceRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Maintenance record " + id + " was not found"));
    }

    private static Map<MaintenanceStatus, Set<MaintenanceStatus>> buildTransitions() {
        Map<MaintenanceStatus, Set<MaintenanceStatus>> transitions = new EnumMap<>(MaintenanceStatus.class);
        transitions.put(MaintenanceStatus.REPORTED, EnumSet.of(
                MaintenanceStatus.SCHEDULED, MaintenanceStatus.IN_PROGRESS,
                MaintenanceStatus.COMPLETED, MaintenanceStatus.CANCELLED));
        transitions.put(MaintenanceStatus.SCHEDULED, EnumSet.of(
                MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.COMPLETED,
                MaintenanceStatus.CANCELLED));
        transitions.put(MaintenanceStatus.IN_PROGRESS, EnumSet.of(
                MaintenanceStatus.COMPLETED, MaintenanceStatus.CANCELLED));
        transitions.put(MaintenanceStatus.COMPLETED, EnumSet.noneOf(MaintenanceStatus.class));
        transitions.put(MaintenanceStatus.CANCELLED, EnumSet.noneOf(MaintenanceStatus.class));
        return transitions;
    }
}

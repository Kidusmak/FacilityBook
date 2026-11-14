package com.groupthree.Facilitybook.service;

import com.groupthree.Facilitybook.dto.common.PageResponse;
import com.groupthree.Facilitybook.dto.reservation.ReservationCancelRequest;
import com.groupthree.Facilitybook.dto.reservation.ReservationCreateRequest;
import com.groupthree.Facilitybook.dto.reservation.ReservationRescheduleRequest;
import com.groupthree.Facilitybook.dto.reservation.ReservationResponse;
import com.groupthree.Facilitybook.dto.reservation.ResolveConflictRequest;
import com.groupthree.Facilitybook.dto.reservation.ApprovalResponse;
import com.groupthree.Facilitybook.entity.MaintenanceRecord;
import com.groupthree.Facilitybook.entity.OperatingHour;
import com.groupthree.Facilitybook.entity.Reservation;
import com.groupthree.Facilitybook.entity.Resource;
import com.groupthree.Facilitybook.entity.ResourceCategory;
import com.groupthree.Facilitybook.entity.User;
import com.groupthree.Facilitybook.entity.enums.ConflictResolutionAction;
import com.groupthree.Facilitybook.entity.enums.MaintenanceStatus;
import com.groupthree.Facilitybook.entity.enums.NotificationType;
import com.groupthree.Facilitybook.entity.enums.ReservationStatus;
import com.groupthree.Facilitybook.entity.enums.ResourceStatus;
import com.groupthree.Facilitybook.exception.BadRequestException;
import com.groupthree.Facilitybook.exception.ConflictException;
import com.groupthree.Facilitybook.exception.NotFoundException;
import com.groupthree.Facilitybook.repository.MaintenanceRecordRepository;
import com.groupthree.Facilitybook.repository.OperatingHourRepository;
import com.groupthree.Facilitybook.repository.ReservationApprovalRepository;
import com.groupthree.Facilitybook.repository.ReservationRepository;
import com.groupthree.Facilitybook.repository.ResourceRepository;
import com.groupthree.Facilitybook.repository.UserRepository;
import com.groupthree.Facilitybook.security.CurrentUser;
import com.groupthree.Facilitybook.security.UserPrincipal;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ReservationService {

    public static final Set<ReservationStatus> BLOCKING_RESERVATION_STATUSES =
            EnumSet.of(ReservationStatus.PENDING_APPROVAL, ReservationStatus.CONFIRMED);
    public static final Set<MaintenanceStatus> BLOCKING_MAINTENANCE_STATUSES =
            EnumSet.of(MaintenanceStatus.REPORTED, MaintenanceStatus.SCHEDULED,
                    MaintenanceStatus.IN_PROGRESS);

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_FACILITY_MANAGER = "FACILITY_MANAGER";
    private static final String ROLE_DEPARTMENT_MANAGER = "DEPARTMENT_MANAGER";
    private static final String ROLE_RESOURCE_OPERATOR = "RESOURCE_OPERATOR";

    private final ReservationRepository reservationRepository;
    private final ResourceRepository resourceRepository;
    private final UserRepository userRepository;
    private final OperatingHourRepository operatingHourRepository;
    private final MaintenanceRecordRepository maintenanceRecordRepository;
    private final ReservationApprovalRepository approvalRepository;
    private final NotificationService notificationService;
    private final CurrentUser currentUser;
    private final ZoneId zone;
    private final DateTimeFormatter displayFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");

    public ReservationService(ReservationRepository reservationRepository,
                              ResourceRepository resourceRepository,
                              UserRepository userRepository,
                              OperatingHourRepository operatingHourRepository,
                              MaintenanceRecordRepository maintenanceRecordRepository,
                              ReservationApprovalRepository approvalRepository,
                              NotificationService notificationService,
                              CurrentUser currentUser,
                              @Value("${app.timezone:Europe/Istanbul}") String timezone) {
        this.reservationRepository = reservationRepository;
        this.resourceRepository = resourceRepository;
        this.userRepository = userRepository;
        this.operatingHourRepository = operatingHourRepository;
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.approvalRepository = approvalRepository;
        this.notificationService = notificationService;
        this.currentUser = currentUser;
        this.zone = ZoneId.of(timezone);
    }

    @Transactional
    public ReservationResponse create(ReservationCreateRequest request) {
        UserPrincipal principal = currentUser.require();
        validateWindow(request.startTime(), request.endTime(), true);
        Resource resource = findResource(request.resourceId());
        ensureResourceIsActive(resource);
        ensureWithinOperatingHours(resource, request.startTime(), request.endTime());
        ensureNoMaintenance(resource, request.startTime(), request.endTime());
        ensureNoOverlap(resource.getId(), request.startTime(), request.endTime(), null);

        User requester = userRepository.getReferenceById(principal.getId());
        boolean requiresApproval = resource.getCategory().isRequiresApproval();

        Reservation reservation = new Reservation();
        reservation.setResource(resource);
        reservation.setRequester(requester);
        reservation.setPurpose(request.purpose().trim());
        reservation.setNotes(trimToNull(request.notes()));
        reservation.setStartTime(request.startTime());
        reservation.setEndTime(request.endTime());
        reservation.setStatus(requiresApproval
                ? ReservationStatus.PENDING_APPROVAL
                : ReservationStatus.CONFIRMED);

        // saveAndFlush surfaces the database exclusion constraint inside this transaction,
        // so a concurrent overlapping insert can never return a success response.
        Reservation saved = reservationRepository.saveAndFlush(reservation);

        if (requiresApproval) {
            notifyApprovers(saved, "A new reservation is waiting for your approval.");
            notificationService.notify(requester, NotificationType.SYSTEM,
                    "Reservation submitted",
                    "Reservation #" + saved.getId() + " for " + resource.getName()
                            + " is pending approval.",
                    saved);
        } else {
            notificationService.notify(requester, NotificationType.RESERVATION_CONFIRMED,
                    "Reservation confirmed",
                    "Reservation #" + saved.getId() + " for " + resource.getName()
                            + " is confirmed for " + describeWindow(saved),
                    saved);
        }

        return ReservationResponse.from(saved);
    }

    public PageResponse<ReservationResponse> mine(ReservationStatus status, Instant from, Instant to,
                                                  Pageable pageable) {
        UserPrincipal principal = currentUser.require();
        Specification<Reservation> specification = filterSpecification(
                status, null, principal.getId(), from, to, null);
        Page<Reservation> page = reservationRepository.findAll(specification, pageable);
        return PageResponse.from(page, ReservationResponse::from);
    }

    public PageResponse<ReservationResponse> list(ReservationStatus status, Long resourceId,
                                                  Long requesterId, Instant from, Instant to,
                                                  String search, Pageable pageable) {
        UserPrincipal principal = currentUser.require();
        Long departmentScope = null;
        if (ROLE_DEPARTMENT_MANAGER.equals(principal.getRole())) {
            departmentScope = principal.getDepartmentId();
            if (departmentScope == null) {
                return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(),
                        0, 0, true, true);
            }
        }
        Specification<Reservation> specification = filterSpecification(
                status, resourceId, requesterId, from, to, search)
                .and(departmentScope(departmentScope));
        Page<Reservation> page = reservationRepository.findAll(specification, pageable);
        return PageResponse.from(page, ReservationResponse::from);
    }

    public ReservationResponse get(Long id) {
        Reservation reservation = find(id);
        ensureCanView(reservation, currentUser.require());
        return ReservationResponse.from(reservation,
                approvalRepository.findByReservationIdOrderByDecidedAtAsc(id));
    }

    @Transactional
    public ReservationResponse reschedule(Long id, ReservationRescheduleRequest request) {
        UserPrincipal principal = currentUser.require();
        Reservation reservation = findForUpdate(id);
        if (!principal.getId().equals(reservation.getRequester().getId())) {
            throw new AccessDeniedException("Only the requester can reschedule this reservation");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING_APPROVAL
                && reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new ConflictException("Only pending or confirmed reservations can be rescheduled");
        }

        validateWindow(request.startTime(), request.endTime(), true);
        Resource resource = reservation.getResource();
        ensureResourceIsActive(resource);
        ensureWithinOperatingHours(resource, request.startTime(), request.endTime());
        ensureNoMaintenance(resource, request.startTime(), request.endTime());
        ensureNoOverlap(resource.getId(), request.startTime(), request.endTime(), id);

        reservation.setStartTime(request.startTime());
        reservation.setEndTime(request.endTime());
        // The new slot was just checked against maintenance, so any older conflict flag is stale.
        reservation.setNeedsResolution(false);

        boolean reapproval = reservation.getStatus() == ReservationStatus.CONFIRMED
                && resource.getCategory().isRequiresApproval();
        if (reapproval) {
            reservation.setStatus(ReservationStatus.PENDING_APPROVAL);
        }
        Reservation saved = reservationRepository.saveAndFlush(reservation);

        if (saved.getStatus() == ReservationStatus.PENDING_APPROVAL) {
            notifyApprovers(saved, "A reservation was rescheduled and needs approval again.");
            notificationService.notify(saved.getRequester(), NotificationType.SYSTEM,
                    "Reservation rescheduled",
                    "Reservation #" + saved.getId() + " was moved to " + describeWindow(saved)
                            + " and is pending approval.",
                    saved);
        } else {
            notificationService.notify(saved.getRequester(), NotificationType.RESERVATION_CONFIRMED,
                    "Reservation rescheduled",
                    "Reservation #" + saved.getId() + " was moved to " + describeWindow(saved) + ".",
                    saved);
        }
        return ReservationResponse.from(saved,
                approvalRepository.findByReservationIdOrderByDecidedAtAsc(id));
    }

    @Transactional
    public ReservationResponse cancel(Long id, ReservationCancelRequest request) {
        UserPrincipal principal = currentUser.require();
        Reservation reservation = findForUpdate(id);
        boolean self = principal.getId().equals(reservation.getRequester().getId());
        boolean staff = hasAnyRole(principal, ROLE_ADMIN, ROLE_FACILITY_MANAGER);

        if (!self && !staff) {
            throw new AccessDeniedException("You are not allowed to cancel this reservation");
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new ConflictException("Reservation is already cancelled");
        }
        if (reservation.getStatus() == ReservationStatus.REJECTED) {
            throw new ConflictException("A rejected reservation cannot be cancelled");
        }
        if (self && !staff && reservation.getStartTime().isBefore(Instant.now())) {
            throw new ConflictException("A reservation cannot be cancelled after it has started");
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancelledAt(Instant.now());
        reservation.setCancellationReason(trimToNull(request.reason()));
        Reservation saved = reservationRepository.save(reservation);

        if (!self) {
            notificationService.notify(saved.getRequester(), NotificationType.RESERVATION_CANCELLED,
                    "Reservation cancelled",
                    "Reservation #" + saved.getId() + " for " + saved.getResource().getName()
                            + " was cancelled by facility staff"
                            + (saved.getCancellationReason() != null
                            ? ": " + saved.getCancellationReason() : "."),
                    saved);
        }
        return ReservationResponse.from(saved,
                approvalRepository.findByReservationIdOrderByDecidedAtAsc(id));
    }

    @Transactional
    public ReservationResponse resolveConflict(Long id, ResolveConflictRequest request) {
        UserPrincipal principal = currentUser.require();
        if (!hasAnyRole(principal, ROLE_ADMIN, ROLE_FACILITY_MANAGER, ROLE_RESOURCE_OPERATOR)) {
            throw new AccessDeniedException("Only facility staff can resolve maintenance conflicts");
        }
        Reservation reservation = findForUpdate(id);
        if (!reservation.isNeedsResolution()) {
            throw new ConflictException("Reservation is not flagged for maintenance resolution");
        }

        if (request.action() == ConflictResolutionAction.CANCEL_RESERVATION) {
            reservation.setStatus(ReservationStatus.CANCELLED);
            reservation.setCancelledAt(Instant.now());
            reservation.setCancellationReason(request.note() != null && !request.note().isBlank()
                    ? request.note().trim()
                    : "Cancelled due to scheduled maintenance");
            notificationService.notify(reservation.getRequester(),
                    NotificationType.RESERVATION_CANCELLED,
                    "Reservation cancelled",
                    "Reservation #" + reservation.getId() + " was cancelled because the resource "
                            + "is under maintenance.",
                    reservation);
        } else {
            reservation.setNeedsResolution(false);
            notificationService.notify(reservation.getRequester(),
                    NotificationType.MAINTENANCE_CONFLICT,
                    "Maintenance conflict reviewed",
                    "Facility staff kept reservation #" + reservation.getId()
                            + ". Please check the maintenance schedule before attending.",
                    reservation);
        }

        Reservation saved = reservationRepository.save(reservation);
        return ReservationResponse.from(saved,
                approvalRepository.findByReservationIdOrderByDecidedAtAsc(id));
    }

    public Reservation find(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Reservation " + id + " was not found"));
    }

    /**
     * Same lookup as {@link #find(Long)} but takes a row lock. Every method that mutates a
     * reservation uses this so concurrent approve/cancel/reschedule requests are serialised.
     */
    private Reservation findForUpdate(Long id) {
        return reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Reservation " + id + " was not found"));
    }

    public List<ApprovalResponse> approvalHistory(Long id) {
        Reservation reservation = find(id);
        ensureCanView(reservation, currentUser.require());
        return approvalRepository.findByReservationIdOrderByDecidedAtAsc(id).stream()
                .map(ApprovalResponse::from)
                .toList();
    }

    private Resource findResource(Long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Resource " + id + " was not found"));
    }

    private void validateWindow(Instant startTime, Instant endTime, boolean requireFuture) {
        if (startTime == null || endTime == null) {
            throw new BadRequestException("Both startTime and endTime are required");
        }
        if (!endTime.isAfter(startTime)) {
            throw new BadRequestException("endTime must be after startTime");
        }
        if (requireFuture && startTime.isBefore(Instant.now())) {
            throw new BadRequestException("startTime must be in the future");
        }
    }

    private void ensureResourceIsActive(Resource resource) {
        if (resource.getStatus() != ResourceStatus.ACTIVE) {
            throw new ConflictException("Resource '" + resource.getName() + "' is not available for booking");
        }
    }

    private void ensureWithinOperatingHours(Resource resource, Instant startTime, Instant endTime) {
        List<OperatingHour> hours = operatingHourRepository
                .findByResourceIdOrderByDayOfWeekAsc(resource.getId());
        if (hours.isEmpty()) {
            return;
        }
        Map<Short, OperatingHour> byDay = new HashMap<>();
        for (OperatingHour hour : hours) {
            byDay.put(hour.getDayOfWeek(), hour);
        }

        ZonedDateTime start = startTime.atZone(zone);
        ZonedDateTime end = endTime.atZone(zone);
        LocalDate date = start.toLocalDate();
        LocalDate lastDate = end.minusNanos(1).toLocalDate();

        while (!date.isAfter(lastDate)) {
            OperatingHour hour = byDay.get((short) date.getDayOfWeek().getValue());
            if (hour == null) {
                throw new BadRequestException("Resource is closed on " + date.getDayOfWeek()
                        + " (" + date + ")");
            }
            LocalDateTime windowStart = LocalDateTime.of(date, hour.getOpenTime());
            LocalDateTime windowEnd = LocalDateTime.of(date, hour.getCloseTime());
            LocalDateTime reservationStart = start.toLocalDateTime().isAfter(date.atStartOfDay())
                    ? start.toLocalDateTime() : date.atStartOfDay();
            LocalDateTime reservationEnd = end.toLocalDateTime().isBefore(date.plusDays(1).atStartOfDay())
                    ? end.toLocalDateTime() : date.plusDays(1).atStartOfDay();

            if (reservationStart.isBefore(windowStart) || reservationEnd.isAfter(windowEnd)) {
                throw new BadRequestException("Reservation must be within the resource operating hours: "
                        + hour.getOpenTime() + "-" + hour.getCloseTime()
                        + " on " + date.getDayOfWeek());
            }
            date = date.plusDays(1);
        }
    }

    private void ensureNoMaintenance(Resource resource, Instant startTime, Instant endTime) {
        List<MaintenanceRecord> conflicts = maintenanceRecordRepository
                .findByResourceIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                        resource.getId(), BLOCKING_MAINTENANCE_STATUSES, endTime, startTime);
        if (!conflicts.isEmpty()) {
            MaintenanceRecord conflict = conflicts.get(0);
            throw new ConflictException("Resource is unavailable due to maintenance ("
                    + displayFormatter.format(conflict.getStartTime().atZone(zone)) + " - "
                    + displayFormatter.format(conflict.getEndTime().atZone(zone)) + ")");
        }
    }

    private void ensureNoOverlap(Long resourceId, Instant startTime, Instant endTime, Long excludeId) {
        boolean overlaps = excludeId == null
                ? reservationRepository.existsByResourceIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                        resourceId, BLOCKING_RESERVATION_STATUSES, endTime, startTime)
                : reservationRepository
                        .existsByResourceIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThanAndIdNot(
                                resourceId, BLOCKING_RESERVATION_STATUSES, endTime, startTime, excludeId);
        if (overlaps) {
            throw new ConflictException(
                    "The selected time slot overlaps an existing reservation for this resource");
        }
    }

    private void notifyApprovers(Reservation reservation, String extra) {
        ResourceCategory category = reservation.getResource().getCategory();
        if (!category.isRequiresApproval() || category.getApproverRole() == null) {
            return;
        }
        String roleName = category.getApproverRole().getName();
        String title = "Approval required";
        String message = "Reservation #" + reservation.getId() + " for "
                + reservation.getResource().getName() + " (" + describeWindow(reservation) + "). "
                + extra;

        if (ROLE_DEPARTMENT_MANAGER.equals(roleName)) {
            Long departmentId = reservation.getRequester().getDepartment() != null
                    ? reservation.getRequester().getDepartment().getId() : null;
            notificationService.notifyRoleInDepartment(roleName, departmentId,
                    NotificationType.SYSTEM, title, message, reservation);
        } else {
            notificationService.notifyRole(roleName, NotificationType.SYSTEM, title, message, reservation);
        }
    }

    private Specification<Reservation> filterSpecification(ReservationStatus status, Long resourceId,
                                                           Long requesterId, Instant from, Instant to,
                                                           String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (resourceId != null) {
                predicates.add(cb.equal(root.get("resource").get("id"), resourceId));
            }
            if (requesterId != null) {
                predicates.add(cb.equal(root.get("requester").get("id"), requesterId));
            }
            if (from != null) {
                predicates.add(cb.greaterThan(root.get("endTime"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("startTime"), to));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                var resource = root.join("resource", JoinType.LEFT);
                var requester = root.join("requester", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("purpose")), pattern),
                        cb.like(cb.lower(resource.get("name")), pattern),
                        cb.like(cb.lower(requester.get("fullName")), pattern),
                        cb.like(cb.lower(requester.get("email")), pattern)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<Reservation> departmentScope(Long departmentId) {
        return (root, query, cb) -> {
            if (departmentId == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("requester").get("department").get("id"), departmentId);
        };
    }

    private void ensureCanView(Reservation reservation, UserPrincipal principal) {
        if (principal.getId().equals(reservation.getRequester().getId())) {
            return;
        }
        if (hasAnyRole(principal, ROLE_ADMIN, ROLE_FACILITY_MANAGER, ROLE_RESOURCE_OPERATOR)) {
            return;
        }
        if (ROLE_DEPARTMENT_MANAGER.equals(principal.getRole())
                && principal.getDepartmentId() != null
                && reservation.getRequester().getDepartment() != null
                && principal.getDepartmentId().equals(
                        reservation.getRequester().getDepartment().getId())) {
            return;
        }
        throw new AccessDeniedException("You are not allowed to view this reservation");
    }

    public static boolean hasAnyRole(UserPrincipal principal, String... roles) {
        for (String role : roles) {
            if (role.equals(principal.getRole())) {
                return true;
            }
        }
        return false;
    }

    private String describeWindow(Reservation reservation) {
        return displayFormatter.format(reservation.getStartTime().atZone(zone))
                + " - " + displayFormatter.format(reservation.getEndTime().atZone(zone));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

package com.groupthree.Facilitybook.service;

import com.groupthree.Facilitybook.dto.reservation.ReservationCreateRequest;
import com.groupthree.Facilitybook.dto.reservation.ReservationResponse;
import com.groupthree.Facilitybook.entity.Department;
import com.groupthree.Facilitybook.entity.Reservation;
import com.groupthree.Facilitybook.entity.Resource;
import com.groupthree.Facilitybook.entity.ResourceCategory;
import com.groupthree.Facilitybook.entity.Role;
import com.groupthree.Facilitybook.entity.User;
import com.groupthree.Facilitybook.entity.enums.NotificationType;
import com.groupthree.Facilitybook.entity.enums.ReservationStatus;
import com.groupthree.Facilitybook.entity.enums.ResourceStatus;
import com.groupthree.Facilitybook.exception.BadRequestException;
import com.groupthree.Facilitybook.exception.ConflictException;
import com.groupthree.Facilitybook.repository.MaintenanceRecordRepository;
import com.groupthree.Facilitybook.repository.OperatingHourRepository;
import com.groupthree.Facilitybook.repository.ReservationApprovalRepository;
import com.groupthree.Facilitybook.repository.ReservationRepository;
import com.groupthree.Facilitybook.repository.ResourceRepository;
import com.groupthree.Facilitybook.repository.UserRepository;
import com.groupthree.Facilitybook.security.CurrentUser;
import com.groupthree.Facilitybook.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ResourceRepository resourceRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OperatingHourRepository operatingHourRepository;
    @Mock
    private MaintenanceRecordRepository maintenanceRecordRepository;
    @Mock
    private ReservationApprovalRepository approvalRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private CurrentUser currentUser;

    private ReservationService service;
    private UserPrincipal employeePrincipal;
    private User requester;
    private Resource resource;

    @BeforeEach
    void setUp() {
        service = new ReservationService(
                reservationRepository, resourceRepository, userRepository,
                operatingHourRepository, maintenanceRecordRepository, approvalRepository,
                notificationService, currentUser, "UTC");

        employeePrincipal = principal(10L, "EMPLOYEE", null);
        requester = user(10L, "EMPLOYEE", null);

        resource = resource(false, null);
    }

    @Test
    void createsConfirmedReservationWhenCategoryDoesNotRequireApproval() {
        Instant start = futureStart();
        stubHappyPath();

        ReservationResponse response = service.create(
                new ReservationCreateRequest(1L, "Sprint planning", "notes", start, start.plus(1, ChronoUnit.HOURS)));

        assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(response.startTime()).isEqualTo(start);
        verify(reservationRepository).saveAndFlush(any(Reservation.class));
        verify(notificationService).notify(eq(requester),
                eq(NotificationType.RESERVATION_CONFIRMED), any(), any(), any(Reservation.class));
    }

    @Test
    void routesRestrictedCategoryToConfiguredApprover() {
        Role approver = new Role();
        approver.setId((short) 3);
        approver.setName("FACILITY_MANAGER");
        resource = resource(true, approver);
        Instant start = futureStart();
        stubHappyPath();
        when(notificationService.notifyRole(eq("FACILITY_MANAGER"), eq(NotificationType.SYSTEM),
                any(), any(), any(Reservation.class))).thenReturn(1);

        ReservationResponse response = service.create(
                new ReservationCreateRequest(1L, "All-hands", null, start, start.plus(2, ChronoUnit.HOURS)));

        assertThat(response.status()).isEqualTo(ReservationStatus.PENDING_APPROVAL);
        verify(notificationService).notifyRole(eq("FACILITY_MANAGER"), eq(NotificationType.SYSTEM),
                any(), any(), any(Reservation.class));
    }

    @Test
    void rejectsOverlappingReservationBeforeInserting() {
        Instant start = futureStart();
        when(currentUser.require()).thenReturn(employeePrincipal);
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));
        when(operatingHourRepository.findByResourceIdOrderByDayOfWeekAsc(1L)).thenReturn(List.of());
        when(maintenanceRecordRepository
                .findByResourceIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(anyLong(), any(), any(), any()))
                .thenReturn(List.of());
        when(reservationRepository
                .existsByResourceIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(anyLong(), any(), any(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(new ReservationCreateRequest(
                1L, "Overlap", null, start, start.plus(1, ChronoUnit.HOURS))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("overlaps");

        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsEndTimeBeforeStartTime() {
        Instant start = futureStart();

        assertThatThrownBy(() -> service.create(new ReservationCreateRequest(
                1L, "Bad range", null, start, start.minus(1, ChronoUnit.HOURS))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("endTime must be after startTime");

        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsStartTimeInThePast() {
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS);

        assertThatThrownBy(() -> service.create(new ReservationCreateRequest(
                1L, "Past", null, past, past.plus(1, ChronoUnit.HOURS))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("future");
    }

    @Test
    void preventsAnotherEmployeeFromCancellingSomeonesReservation() {
        Reservation reservation = new Reservation();
        reservation.setId(5L);
        reservation.setRequester(requester);
        reservation.setResource(resource);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setStartTime(futureStart());

        when(currentUser.require()).thenReturn(principal(99L, "EMPLOYEE", null));
        when(reservationRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.cancel(5L, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void resolvesMaintenanceConflictByCancellingReservation() {
        Reservation reservation = new Reservation();
        reservation.setId(7L);
        reservation.setRequester(requester);
        reservation.setResource(resource);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setNeedsResolution(true);
        reservation.setStartTime(futureStart());

        when(currentUser.require()).thenReturn(principal(1L, "FACILITY_MANAGER", 1L));
        when(reservationRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(approvalRepository.findByReservationIdOrderByDecidedAtAsc(7L)).thenReturn(List.of());

        var response = service.resolveConflict(7L, new com.groupthree.Facilitybook.dto.reservation.ResolveConflictRequest(
                com.groupthree.Facilitybook.entity.enums.ConflictResolutionAction.CANCEL_RESERVATION, "Blocked"));

        assertThat(response.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(response.cancellationReason()).isEqualTo("Blocked");
        verify(notificationService).notify(eq(requester),
                eq(NotificationType.RESERVATION_CANCELLED), any(), any(), any(Reservation.class));
    }

    private void stubHappyPath() {
        when(currentUser.require()).thenReturn(employeePrincipal);
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));
        when(userRepository.getReferenceById(10L)).thenReturn(requester);
        when(operatingHourRepository.findByResourceIdOrderByDayOfWeekAsc(1L)).thenReturn(List.of());
        when(maintenanceRecordRepository
                .findByResourceIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(anyLong(), any(), any(), any()))
                .thenReturn(List.of());
        when(reservationRepository
                .existsByResourceIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(anyLong(), any(), any(), any()))
                .thenReturn(false);
        when(reservationRepository.saveAndFlush(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });
    }

    private ReservationResponse assertCreated(ReservationResponse response) {
        return response;
    }

    private Resource resource(boolean requiresApproval, Role approverRole) {
        ResourceCategory category = new ResourceCategory();
        category.setId(1L);
        category.setName(requiresApproval ? "Conference Hall" : "Meeting Room");
        category.setRequiresApproval(requiresApproval);
        category.setApproverRole(approverRole);

        Resource value = new Resource();
        value.setId(1L);
        value.setName("Conference Room A");
        value.setCategory(category);
        value.setStatus(ResourceStatus.ACTIVE);
        return value;
    }

    private User user(Long id, String roleName, Long departmentId) {
        Role role = new Role();
        role.setId((short) 2);
        role.setName(roleName);

        Department department = null;
        if (departmentId != null) {
            department = new Department();
            department.setId(departmentId);
            department.setName("Engineering");
        }

        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@facilitybook.local");
        user.setFullName("Test User " + id);
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setDepartment(department);
        user.setActive(true);
        return user;
    }

    private UserPrincipal principal(Long id, String role, Long departmentId) {
        return new UserPrincipal(id, "user" + id + "@facilitybook.local", "hash",
                "Test User " + id, role, departmentId,
                departmentId != null ? "Engineering" : null, true);
    }

    private Instant futureStart() {
        return Instant.now().plus(2, ChronoUnit.DAYS);
    }
}

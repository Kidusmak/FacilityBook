package com.groupthree.Facilitybook.service;

import com.groupthree.Facilitybook.dto.maintenance.MaintenanceCreateRequest;
import com.groupthree.Facilitybook.dto.maintenance.MaintenanceResponse;
import com.groupthree.Facilitybook.dto.maintenance.MaintenanceUpdateRequest;
import com.groupthree.Facilitybook.entity.Department;
import com.groupthree.Facilitybook.entity.MaintenanceRecord;
import com.groupthree.Facilitybook.entity.Reservation;
import com.groupthree.Facilitybook.entity.Resource;
import com.groupthree.Facilitybook.entity.ResourceCategory;
import com.groupthree.Facilitybook.entity.Role;
import com.groupthree.Facilitybook.entity.User;
import com.groupthree.Facilitybook.entity.enums.MaintenanceStatus;
import com.groupthree.Facilitybook.entity.enums.NotificationType;
import com.groupthree.Facilitybook.entity.enums.ReservationStatus;
import com.groupthree.Facilitybook.entity.enums.ResourceStatus;
import com.groupthree.Facilitybook.exception.BadRequestException;
import com.groupthree.Facilitybook.exception.ConflictException;
import com.groupthree.Facilitybook.repository.MaintenanceRecordRepository;
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
class MaintenanceServiceTest {

    @Mock
    private MaintenanceRecordRepository maintenanceRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ResourceRepository resourceRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private CurrentUser currentUser;

    private MaintenanceService service;
    private Resource resource;

    @BeforeEach
    void setUp() {
        service = new MaintenanceService(maintenanceRepository, reservationRepository,
                resourceRepository, userRepository, notificationService, currentUser);
        resource = resource("Conference Room A");
    }

    @Test
    void createsReportedMaintenanceAndFlagsOverlappingReservation() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        Reservation conflict = reservation(20L, resource, start.plus(30, ChronoUnit.MINUTES));

        when(currentUser.require()).thenReturn(principal(5L, "FACILITY_MANAGER"));
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));
        when(userRepository.getReferenceById(5L)).thenReturn(user(5L, "FACILITY_MANAGER"));
        when(maintenanceRepository.save(any(MaintenanceRecord.class)))
                .thenAnswer(invocation -> {
                    MaintenanceRecord saved = invocation.getArgument(0);
                    saved.setId(30L);
                    return saved;
                });
        when(reservationRepository
                .findByResourceIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                        anyLong(), any(), any(), any()))
                .thenReturn(List.of(conflict));

        MaintenanceResponse response = service.create(
                new MaintenanceCreateRequest(1L, "Projector replacement", start, end, null));

        assertThat(response.status()).isEqualTo(MaintenanceStatus.REPORTED);
        assertThat(conflict.isNeedsResolution()).isTrue();
        verify(reservationRepository).save(conflict);
        verify(notificationService).notify(eq(conflict.getRequester()),
                eq(NotificationType.MAINTENANCE_CONFLICT), any(), any(), any(Reservation.class));
    }

    @Test
    void rejectsCreateWhenEndIsNotAfterStart() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);

        assertThatThrownBy(() -> service.create(new MaintenanceCreateRequest(
                1L, "Invalid", start, start.minus(1, ChronoUnit.HOURS), MaintenanceStatus.SCHEDULED)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("endTime must be after startTime");

        verify(maintenanceRepository, never()).save(any());
    }

    @Test
    void employeeCanReportDamagedResourceButCannotScheduleMaintenance() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        when(currentUser.require()).thenReturn(principal(10L, "EMPLOYEE"));
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));
        when(userRepository.getReferenceById(10L)).thenReturn(user(10L, "EMPLOYEE"));
        when(maintenanceRepository.save(any(MaintenanceRecord.class)))
                .thenAnswer(invocation -> {
                    MaintenanceRecord saved = invocation.getArgument(0);
                    saved.setId(35L);
                    return saved;
                });
        when(reservationRepository
                .findByResourceIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                        anyLong(), any(), any(), any()))
                .thenReturn(List.of());

        MaintenanceResponse response = service.create(
                new MaintenanceCreateRequest(1L, "Projector is broken", start, end, null));
        assertThat(response.status()).isEqualTo(MaintenanceStatus.REPORTED);

        assertThatThrownBy(() -> service.create(new MaintenanceCreateRequest(
                1L, "Block the room for a day", start, end, MaintenanceStatus.SCHEDULED)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only facility staff");
    }

    @Test
    void rejectsTransitionOutOfTerminalState() {
        MaintenanceRecord record = record(31L, MaintenanceStatus.COMPLETED);
        when(maintenanceRepository.findByIdForUpdate(31L)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.update(31L,
                new MaintenanceUpdateRequest(MaintenanceStatus.IN_PROGRESS, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot change maintenance status");

        verify(maintenanceRepository, never()).save(any());
    }

    @Test
    void completingRunningMaintenanceClampsEndToNow() {
        MaintenanceRecord record = record(32L, MaintenanceStatus.IN_PROGRESS);
        record.setStartTime(Instant.now().minus(1, ChronoUnit.HOURS));
        record.setEndTime(Instant.now().plus(3, ChronoUnit.HOURS));
        Instant originalEnd = record.getEndTime();

        when(maintenanceRepository.findByIdForUpdate(32L)).thenReturn(Optional.of(record));
        when(maintenanceRepository.save(any(MaintenanceRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MaintenanceResponse response = service.update(32L,
                new MaintenanceUpdateRequest(MaintenanceStatus.COMPLETED, "Fixed"));

        assertThat(response.status()).isEqualTo(MaintenanceStatus.COMPLETED);
        assertThat(response.completedAt()).isNotNull();
        assertThat(response.endTime()).isAfter(response.startTime());
        assertThat(response.endTime()).isBefore(originalEnd);
    }

    @Test
    void completingFutureMaintenanceKeepsValidWindow() {
        MaintenanceRecord record = record(33L, MaintenanceStatus.SCHEDULED);
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        record.setStartTime(start);
        record.setEndTime(end);

        when(maintenanceRepository.findByIdForUpdate(33L)).thenReturn(Optional.of(record));
        when(maintenanceRepository.save(any(MaintenanceRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MaintenanceResponse response = service.update(33L,
                new MaintenanceUpdateRequest(MaintenanceStatus.COMPLETED, "Done early"));

        assertThat(response.completedAt()).isNotNull();
        assertThat(response.startTime()).isEqualTo(start);
        assertThat(response.endTime()).isEqualTo(end);
        assertThat(response.endTime()).isAfter(response.startTime());
    }

    @Test
    void movingToInProgressFlagsOverlappingReservations() {
        MaintenanceRecord record = record(34L, MaintenanceStatus.REPORTED);
        record.setStartTime(Instant.now().minus(30, ChronoUnit.MINUTES));
        record.setEndTime(Instant.now().plus(2, ChronoUnit.HOURS));
        Reservation conflict = reservation(21L, resource, Instant.now());

        when(maintenanceRepository.findByIdForUpdate(34L)).thenReturn(Optional.of(record));
        when(maintenanceRepository.save(any(MaintenanceRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationRepository
                .findByResourceIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                        anyLong(), any(), any(), any()))
                .thenReturn(List.of(conflict));

        MaintenanceResponse response = service.update(34L,
                new MaintenanceUpdateRequest(MaintenanceStatus.IN_PROGRESS, null));

        assertThat(response.status()).isEqualTo(MaintenanceStatus.IN_PROGRESS);
        assertThat(conflict.isNeedsResolution()).isTrue();
        verify(reservationRepository).save(conflict);
    }

    private MaintenanceRecord record(Long id, MaintenanceStatus status) {
        MaintenanceRecord record = new MaintenanceRecord();
        record.setId(id);
        record.setResource(resource);
        record.setReportedBy(user(5L, "FACILITY_MANAGER"));
        record.setReason("Air-conditioning service");
        record.setStatus(status);
        record.setStartTime(Instant.now().plus(1, ChronoUnit.DAYS));
        record.setEndTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS));
        return record;
    }

    private Reservation reservation(Long id, Resource resource, Instant start) {
        Reservation reservation = new Reservation();
        reservation.setId(id);
        reservation.setResource(resource);
        reservation.setRequester(user(10L, "EMPLOYEE"));
        reservation.setPurpose("Team meeting");
        reservation.setStartTime(start);
        reservation.setEndTime(start.plus(1, ChronoUnit.HOURS));
        reservation.setStatus(ReservationStatus.CONFIRMED);
        return reservation;
    }

    private Resource resource(String name) {
        ResourceCategory category = new ResourceCategory();
        category.setId(1L);
        category.setName("Meeting Room");
        category.setRequiresApproval(false);

        Resource value = new Resource();
        value.setId(1L);
        value.setName(name);
        value.setCategory(category);
        value.setStatus(ResourceStatus.ACTIVE);
        return value;
    }

    private UserPrincipal principal(Long id, String roleName) {
        return new UserPrincipal(id, "user" + id + "@facilitybook.local", "hash",
                "Test User " + id, roleName, 1L, "Engineering", true);
    }

    private User user(Long id, String roleName) {
        Role role = new Role();
        role.setId((short) 2);
        role.setName(roleName);

        Department department = new Department();
        department.setId(1L);
        department.setName("Engineering");

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
}

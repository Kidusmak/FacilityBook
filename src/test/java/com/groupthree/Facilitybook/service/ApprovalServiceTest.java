package com.groupthree.Facilitybook.service;

import com.groupthree.Facilitybook.dto.reservation.ApprovalDecisionRequest;
import com.groupthree.Facilitybook.dto.reservation.ReservationResponse;
import com.groupthree.Facilitybook.entity.Department;
import com.groupthree.Facilitybook.entity.Reservation;
import com.groupthree.Facilitybook.entity.ReservationApproval;
import com.groupthree.Facilitybook.entity.Resource;
import com.groupthree.Facilitybook.entity.ResourceCategory;
import com.groupthree.Facilitybook.entity.Role;
import com.groupthree.Facilitybook.entity.User;
import com.groupthree.Facilitybook.entity.enums.ApprovalDecision;
import com.groupthree.Facilitybook.entity.enums.NotificationType;
import com.groupthree.Facilitybook.entity.enums.ReservationStatus;
import com.groupthree.Facilitybook.entity.enums.ResourceStatus;
import com.groupthree.Facilitybook.exception.BadRequestException;
import com.groupthree.Facilitybook.exception.ConflictException;
import com.groupthree.Facilitybook.repository.MaintenanceRecordRepository;
import com.groupthree.Facilitybook.repository.ReservationApprovalRepository;
import com.groupthree.Facilitybook.repository.ReservationRepository;
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
class ApprovalServiceTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ReservationApprovalRepository approvalRepository;
    @Mock
    private MaintenanceRecordRepository maintenanceRecordRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private CurrentUser currentUser;

    private ApprovalService service;

    @BeforeEach
    void setUp() {
        service = new ApprovalService(reservationRepository, approvalRepository,
                maintenanceRecordRepository, userRepository, notificationService, currentUser);
    }

    @Test
    void approvesPendingReservationWhenRolesMatch() {
        Reservation reservation = reservation(100L, department(1L), "FACILITY_MANAGER");
        UserPrincipal approver = principal(50L, "FACILITY_MANAGER", 1L);

        when(currentUser.require()).thenReturn(approver);
        when(reservationRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(reservation));
        when(maintenanceRecordRepository
                .findByResourceIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                        anyLong(), any(), any(), any()))
                .thenReturn(List.of());
        when(userRepository.getReferenceById(50L)).thenReturn(user(50L, "FACILITY_MANAGER", 1L));
        when(approvalRepository.save(any(ReservationApproval.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(approvalRepository.findByReservationIdOrderByDecidedAtAsc(100L)).thenReturn(List.of());

        ReservationResponse response = service.decide(100L,
                new ApprovalDecisionRequest(ApprovalDecision.APPROVED, "Looks good"));

        assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(reservationRepository).save(reservation);
        verify(notificationService).notify(eq(reservation.getRequester()),
                eq(NotificationType.RESERVATION_APPROVED), any(), any(), any(Reservation.class));
    }

    @Test
    void rejectsApprovalWhenFacilityManagerIsNotTheConfiguredApprover() {
        Reservation reservation = reservation(101L, department(1L), "DEPARTMENT_MANAGER");

        when(currentUser.require()).thenReturn(principal(50L, "FACILITY_MANAGER", 1L));
        when(reservationRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.decide(101L,
                new ApprovalDecisionRequest(ApprovalDecision.APPROVED, null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("department manager");

        verify(approvalRepository, never()).save(any());
    }

    @Test
    void departmentManagerCannotApproveAnotherDepartment() {
        Reservation reservation = reservation(102L, department(1L), "DEPARTMENT_MANAGER");

        when(currentUser.require()).thenReturn(principal(60L, "DEPARTMENT_MANAGER", 2L));
        when(reservationRepository.findByIdForUpdate(102L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.decide(102L,
                new ApprovalDecisionRequest(ApprovalDecision.APPROVED, null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("own department");

        verify(approvalRepository, never()).save(any());
    }

    @Test
    void approverCannotApproveOwnReservation() {
        User requester = user(70L, "DEPARTMENT_MANAGER", 1L);
        Reservation reservation = reservation(103L, requester, "DEPARTMENT_MANAGER");

        when(currentUser.require()).thenReturn(principal(70L, "DEPARTMENT_MANAGER", 1L));
        when(reservationRepository.findByIdForUpdate(103L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.decide(103L,
                new ApprovalDecisionRequest(ApprovalDecision.APPROVED, null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("your own reservation");

        verify(approvalRepository, never()).save(any());
    }

    @Test
    void rejectionRequiresAReason() {
        Reservation reservation = reservation(104L, department(1L), "FACILITY_MANAGER");

        when(currentUser.require()).thenReturn(principal(50L, "FACILITY_MANAGER", 1L));
        when(reservationRepository.findByIdForUpdate(104L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.decide(104L,
                new ApprovalDecisionRequest(ApprovalDecision.REJECTED, "   ")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("reason is required");

        verify(approvalRepository, never()).save(any());
    }

    @Test
    void rejectionStoresReasonAndNotifiesRequester() {
        Reservation reservation = reservation(105L, department(1L), "FACILITY_MANAGER");

        when(currentUser.require()).thenReturn(principal(50L, "FACILITY_MANAGER", 1L));
        when(reservationRepository.findByIdForUpdate(105L)).thenReturn(Optional.of(reservation));
        when(userRepository.getReferenceById(50L)).thenReturn(user(50L, "FACILITY_MANAGER", 1L));
        when(approvalRepository.save(any(ReservationApproval.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(approvalRepository.findByReservationIdOrderByDecidedAtAsc(105L)).thenReturn(List.of());

        ReservationResponse response = service.decide(105L,
                new ApprovalDecisionRequest(ApprovalDecision.REJECTED, "Room is being refurbished"));

        assertThat(response.status()).isEqualTo(ReservationStatus.REJECTED);
        verify(notificationService).notify(eq(reservation.getRequester()),
                eq(NotificationType.RESERVATION_REJECTED), any(), any(), any(Reservation.class));
    }

    @Test
    void cannotDecideReservationThatIsNotPending() {
        Reservation reservation = reservation(106L, department(1L), "FACILITY_MANAGER");
        reservation.setStatus(ReservationStatus.CONFIRMED);

        when(currentUser.require()).thenReturn(principal(50L, "FACILITY_MANAGER", 1L));
        when(reservationRepository.findByIdForUpdate(106L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.decide(106L,
                new ApprovalDecisionRequest(ApprovalDecision.APPROVED, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("pending approval");
    }

    @Test
    void approvalIsBlockedWhenMaintenanceNowOverlapsTheReservation() {
        Reservation reservation = reservation(107L, department(1L), "FACILITY_MANAGER");

        when(currentUser.require()).thenReturn(principal(50L, "FACILITY_MANAGER", 1L));
        when(reservationRepository.findByIdForUpdate(107L)).thenReturn(Optional.of(reservation));
        when(maintenanceRecordRepository
                .findByResourceIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                        anyLong(), any(), any(), any()))
                .thenReturn(List.of(new com.groupthree.Facilitybook.entity.MaintenanceRecord()));

        assertThatThrownBy(() -> service.decide(107L,
                new ApprovalDecisionRequest(ApprovalDecision.APPROVED, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("under maintenance");

        verify(approvalRepository, never()).save(any());
    }

    private Reservation reservation(Long id, Department department, String approverRoleName) {
        return reservation(id, user(10L, "EMPLOYEE", department.getId()), approverRoleName);
    }

    private Reservation reservation(Long id, User requester, String approverRoleName) {
        Role approverRole = new Role();
        approverRole.setId((short) 3);
        approverRole.setName(approverRoleName);

        ResourceCategory category = new ResourceCategory();
        category.setId(1L);
        category.setName("Conference Hall");
        category.setRequiresApproval(true);
        category.setApproverRole(approverRole);

        Resource resource = new Resource();
        resource.setId(1L);
        resource.setName("Conference Hall");
        resource.setCategory(category);
        resource.setStatus(ResourceStatus.ACTIVE);

        Reservation reservation = new Reservation();
        reservation.setId(id);
        reservation.setResource(resource);
        reservation.setRequester(requester);
        reservation.setPurpose("Company event");
        reservation.setStartTime(Instant.now().plus(2, ChronoUnit.DAYS));
        reservation.setEndTime(Instant.now().plus(2, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS));
        reservation.setStatus(ReservationStatus.PENDING_APPROVAL);
        return reservation;
    }

    private Department department(Long id) {
        Department department = new Department();
        department.setId(id);
        department.setName("Engineering");
        return department;
    }

    private User user(Long id, String roleName, Long departmentId) {
        Role role = new Role();
        role.setId((short) 2);
        role.setName(roleName);

        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@facilitybook.local");
        user.setFullName("Test User " + id);
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setDepartment(department(departmentId));
        user.setActive(true);
        return user;
    }

    private UserPrincipal principal(Long id, String role, Long departmentId) {
        return new UserPrincipal(id, "user" + id + "@facilitybook.local", "hash",
                "Test User " + id, role, departmentId, "Engineering", true);
    }
}

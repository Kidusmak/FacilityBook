package com.groupthree.Facilitybook.repository;

import com.groupthree.Facilitybook.entity.Reservation;
import com.groupthree.Facilitybook.entity.enums.ReservationStatus;
import com.groupthree.Facilitybook.repository.projection.DepartmentUsageProjection;
import com.groupthree.Facilitybook.repository.projection.HourUsageProjection;
import com.groupthree.Facilitybook.repository.projection.ResourceUsageProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ReservationRepository
        extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {

    /**
     * Overlap test for [startTime, endTime) against reservations in the given statuses.
     * The database exclusion constraint is the final guard; this check only produces
     * friendly error messages before attempting the insert.
     */
    boolean existsByResourceIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
            Long resourceId,
            Collection<ReservationStatus> statuses,
            Instant endTime,
            Instant startTime);

    boolean existsByResourceIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThanAndIdNot(
            Long resourceId,
            Collection<ReservationStatus> statuses,
            Instant endTime,
            Instant startTime,
            Long id);

    Page<Reservation> findByRequesterId(Long requesterId, Pageable pageable);

    boolean existsByResourceId(Long resourceId);

    List<Reservation> findByResourceIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
            Long resourceId,
            Collection<ReservationStatus> statuses,
            Instant endTime,
            Instant startTime);

    Page<Reservation> findByStatus(ReservationStatus status, Pageable pageable);

    List<Reservation> findByStatusAndStartTimeBetween(ReservationStatus status, Instant from, Instant to);

    List<Reservation> findByStatusAndNeedsResolutionTrue(ReservationStatus status);

    long countByStatus(ReservationStatus status);

    long countByNeedsResolutionTrue();

    long countByNeedsResolutionTrueAndStartTimeGreaterThanEqualAndStartTimeLessThan(
            Instant from, Instant to);

    long countByStartTimeGreaterThanEqualAndStartTimeLessThan(Instant from, Instant to);

    long countByStatusAndStartTimeGreaterThanEqualAndStartTimeLessThan(
            ReservationStatus status, Instant from, Instant to);

    @Query("""
            select r from Reservation r
            where r.status = :status
              and r.resource.category.requiresApproval = true
              and r.resource.category.approverRole.name = :roleName
            """)
    Page<Reservation> findPendingForApproverRole(
            @Param("status") ReservationStatus status,
            @Param("roleName") String roleName,
            Pageable pageable);

    @Query("""
            select r from Reservation r
            where r.status = :status
              and r.resource.category.requiresApproval = true
              and r.resource.category.approverRole.name = :roleName
              and r.requester.department.id = :departmentId
            """)
    Page<Reservation> findPendingForApproverRoleAndDepartment(
            @Param("status") ReservationStatus status,
            @Param("roleName") String roleName,
            @Param("departmentId") Long departmentId,
            Pageable pageable);

    @Query(value = """
            SELECT res.id AS "resourceId", res.name AS "resourceName",
                   COUNT(r.id) AS "reservationCount",
                   COALESCE(SUM(EXTRACT(EPOCH FROM (r.end_time - r.start_time)) / 3600.0), 0)
                       AS "bookedHours"
            FROM resources res
            LEFT JOIN reservations r ON r.resource_id = res.id
                 AND r.status IN ('CONFIRMED', 'PENDING_APPROVAL')
                 AND r.start_time >= :from AND r.start_time < :to
            GROUP BY res.id, res.name
            ORDER BY "reservationCount" DESC, "bookedHours" DESC
            """, nativeQuery = true)
    List<ResourceUsageProjection> findResourceUsage(
            @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query(value = """
            SELECT d.id AS "departmentId",
                   COALESCE(d.name, 'Unassigned') AS "departmentName",
                   COUNT(r.id) AS "reservationCount"
            FROM reservations r
            JOIN users u ON u.id = r.requester_id
            LEFT JOIN departments d ON d.id = u.department_id
            WHERE r.start_time >= :from AND r.start_time < :to
              AND r.status IN ('CONFIRMED', 'PENDING_APPROVAL')
            GROUP BY d.id, d.name
            ORDER BY "reservationCount" DESC
            """, nativeQuery = true)
    List<DepartmentUsageProjection> findDepartmentUsage(
            @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = """
            SELECT EXTRACT(HOUR FROM (r.start_time AT TIME ZONE CAST(:zone AS text)))::int AS "hour",
                   COUNT(*) AS "reservationCount"
            FROM reservations r
            WHERE r.start_time >= :from AND r.start_time < :to
              AND r.status IN ('CONFIRMED', 'PENDING_APPROVAL')
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<HourUsageProjection> findPeakHours(
            @Param("from") Instant from, @Param("to") Instant to, @Param("zone") String zone);

    @Query(value = """
            SELECT COALESCE(SUM(EXTRACT(EPOCH FROM (r.end_time - r.start_time)) / 3600.0), 0)
                   ::double precision
            FROM reservations r
            WHERE r.status = 'CONFIRMED'
              AND r.start_time >= :from AND r.start_time < :to
            """, nativeQuery = true)
    Double sumConfirmedBookedHours(@Param("from") Instant from, @Param("to") Instant to);
}

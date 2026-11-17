package com.groupthree.Facilitybook.repository;

import com.groupthree.Facilitybook.entity.MaintenanceRecord;
import com.groupthree.Facilitybook.entity.enums.MaintenanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MaintenanceRecordRepository
        extends JpaRepository<MaintenanceRecord, Long>, JpaSpecificationExecutor<MaintenanceRecord> {

    /** Serialises status transitions so two operators cannot complete the same record twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MaintenanceRecord m where m.id = :id")
    Optional<MaintenanceRecord> findByIdForUpdate(@Param("id") Long id);

    List<MaintenanceRecord> findByResourceIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
            Long resourceId,
            Collection<MaintenanceStatus> statuses,
            Instant endTime,
            Instant startTime);

    List<MaintenanceRecord> findByStatus(MaintenanceStatus status);

    boolean existsByResourceId(Long resourceId);

    boolean existsByReportedById(Long reportedById);

    long countByStartTimeLessThanAndEndTimeGreaterThan(Instant to, Instant from);

    @Query(value = """
            SELECT COALESCE(SUM(
                       EXTRACT(EPOCH FROM (LEAST(m.end_time, :to) - GREATEST(m.start_time, :from))) / 3600.0
                   ), 0)::double precision
            FROM maintenance_records m
            WHERE m.status <> 'CANCELLED'
              AND m.start_time < :to AND m.end_time > :from
            """, nativeQuery = true)
    Double sumDowntimeHours(@Param("from") Instant from, @Param("to") Instant to);
}

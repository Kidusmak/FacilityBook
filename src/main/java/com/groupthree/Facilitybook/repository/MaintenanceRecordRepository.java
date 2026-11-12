package com.groupthree.Facilitybook.repository;

import com.groupthree.Facilitybook.entity.MaintenanceRecord;
import com.groupthree.Facilitybook.entity.enums.MaintenanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, Long> {

    List<MaintenanceRecord> findByResourceIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
            Long resourceId,
            Collection<MaintenanceStatus> statuses,
            Instant endTime,
            Instant startTime);

    List<MaintenanceRecord> findByStatus(MaintenanceStatus status);
}

package com.groupthree.Facilitybook.repository;

import com.groupthree.Facilitybook.entity.OperatingHour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OperatingHourRepository extends JpaRepository<OperatingHour, Long> {

    List<OperatingHour> findByResourceIdOrderByDayOfWeekAsc(Long resourceId);

    boolean existsByResourceIdAndDayOfWeek(Long resourceId, Short dayOfWeek);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from OperatingHour oh where oh.resource.id = :resourceId")
    int deleteAllByResourceId(@Param("resourceId") Long resourceId);
}

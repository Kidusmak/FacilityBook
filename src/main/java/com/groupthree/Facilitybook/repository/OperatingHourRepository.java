package com.groupthree.Facilitybook.repository;

import com.groupthree.Facilitybook.entity.OperatingHour;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OperatingHourRepository extends JpaRepository<OperatingHour, Long> {

    List<OperatingHour> findByResourceIdOrderByDayOfWeekAsc(Long resourceId);

    void deleteByResourceId(Long resourceId);
}

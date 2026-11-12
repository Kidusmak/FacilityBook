package com.groupthree.Facilitybook.repository;

import com.groupthree.Facilitybook.entity.Reservation;
import com.groupthree.Facilitybook.entity.enums.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * Overlap test for [startTime, endTime) against reservations in the given statuses.
     * The database exclusion constraint is the final guard; this is used for friendly errors.
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

    List<Reservation> findByResourceIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
            Long resourceId,
            Collection<ReservationStatus> statuses,
            Instant endTime,
            Instant startTime);

    List<Reservation> findByStatus(ReservationStatus status);
}

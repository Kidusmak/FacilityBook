package com.groupthree.Facilitybook.repository;

import com.groupthree.Facilitybook.entity.ReservationApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationApprovalRepository extends JpaRepository<ReservationApproval, Long> {

    List<ReservationApproval> findByReservationIdOrderByDecidedAtAsc(Long reservationId);

    boolean existsByReservationId(Long reservationId);

    boolean existsByApproverId(Long approverId);
}

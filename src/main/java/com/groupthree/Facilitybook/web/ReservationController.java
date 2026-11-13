package com.groupthree.Facilitybook.web;

import com.groupthree.Facilitybook.dto.common.PageResponse;
import com.groupthree.Facilitybook.dto.reservation.ApprovalResponse;
import com.groupthree.Facilitybook.dto.reservation.ReservationCancelRequest;
import com.groupthree.Facilitybook.dto.reservation.ReservationCreateRequest;
import com.groupthree.Facilitybook.dto.reservation.ReservationRescheduleRequest;
import com.groupthree.Facilitybook.dto.reservation.ReservationResponse;
import com.groupthree.Facilitybook.dto.reservation.ResolveConflictRequest;
import com.groupthree.Facilitybook.entity.enums.ReservationStatus;
import com.groupthree.Facilitybook.service.ReservationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@Tag(name = "Reservations")
public class ReservationController {

    private static final String STAFF =
            "hasAnyRole('ADMIN','FACILITY_MANAGER','RESOURCE_OPERATOR','DEPARTMENT_MANAGER')";
    private static final String FACILITY_STAFF =
            "hasAnyRole('ADMIN','FACILITY_MANAGER','RESOURCE_OPERATOR')";

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(@Valid @RequestBody ReservationCreateRequest request) {
        return reservationService.create(request);
    }

    @GetMapping("/mine")
    public PageResponse<ReservationResponse> mine(
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "startTime") Pageable pageable) {
        return reservationService.mine(status, from, to, pageable);
    }

    @GetMapping
    @PreAuthorize(STAFF)
    public PageResponse<ReservationResponse> list(
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) Long resourceId,
            @RequestParam(required = false) Long requesterId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "startTime") Pageable pageable) {
        return reservationService.list(status, resourceId, requesterId, from, to, search, pageable);
    }

    @GetMapping("/{id}")
    public ReservationResponse get(@PathVariable Long id) {
        return reservationService.get(id);
    }

    @GetMapping("/{id}/approvals")
    public List<ApprovalResponse> approvals(@PathVariable Long id) {
        return reservationService.approvalHistory(id);
    }

    @PatchMapping("/{id}/reschedule")
    public ReservationResponse reschedule(@PathVariable Long id,
                                          @Valid @RequestBody ReservationRescheduleRequest request) {
        return reservationService.reschedule(id, request);
    }

    @PatchMapping("/{id}/cancel")
    public ReservationResponse cancel(@PathVariable Long id,
                                      @Valid @RequestBody(required = false) ReservationCancelRequest request) {
        return reservationService.cancel(id,
                request != null ? request : new ReservationCancelRequest(null));
    }

    @PatchMapping("/{id}/resolve-conflict")
    @PreAuthorize(FACILITY_STAFF)
    public ReservationResponse resolveConflict(@PathVariable Long id,
                                               @Valid @RequestBody ResolveConflictRequest request) {
        return reservationService.resolveConflict(id, request);
    }
}

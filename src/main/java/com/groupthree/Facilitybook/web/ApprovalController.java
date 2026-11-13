package com.groupthree.Facilitybook.web;

import com.groupthree.Facilitybook.dto.common.PageResponse;
import com.groupthree.Facilitybook.dto.reservation.ApprovalDecisionRequest;
import com.groupthree.Facilitybook.dto.reservation.ReservationResponse;
import com.groupthree.Facilitybook.service.ApprovalService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approvals")
@Tag(name = "Approvals")
public class ApprovalController {

    private static final String APPROVERS =
            "hasAnyRole('ADMIN','FACILITY_MANAGER','DEPARTMENT_MANAGER')";

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/pending")
    @PreAuthorize(APPROVERS)
    public PageResponse<ReservationResponse> pending(
            @PageableDefault(size = 20, sort = "startTime") Pageable pageable) {
        return approvalService.pending(pageable);
    }

    @PostMapping("/{reservationId}/decision")
    @PreAuthorize(APPROVERS)
    public ReservationResponse decide(@PathVariable Long reservationId,
                                      @Valid @RequestBody ApprovalDecisionRequest request) {
        return approvalService.decide(reservationId, request);
    }
}

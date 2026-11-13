package com.groupthree.Facilitybook.web;

import com.groupthree.Facilitybook.dto.common.PageResponse;
import com.groupthree.Facilitybook.dto.maintenance.MaintenanceCreateRequest;
import com.groupthree.Facilitybook.dto.maintenance.MaintenanceResponse;
import com.groupthree.Facilitybook.dto.maintenance.MaintenanceUpdateRequest;
import com.groupthree.Facilitybook.entity.enums.MaintenanceStatus;
import com.groupthree.Facilitybook.service.MaintenanceService;
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

@RestController
@RequestMapping("/api/maintenance")
@Tag(name = "Maintenance")
public class MaintenanceController {

    private static final String FACILITY_STAFF =
            "hasAnyRole('ADMIN','FACILITY_MANAGER','RESOURCE_OPERATOR')";

    private final MaintenanceService maintenanceService;

    public MaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @GetMapping
    @PreAuthorize(FACILITY_STAFF)
    public PageResponse<MaintenanceResponse> list(
            @RequestParam(required = false) MaintenanceStatus status,
            @RequestParam(required = false) Long resourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "startTime") Pageable pageable) {
        return maintenanceService.list(status, resourceId, from, to, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(FACILITY_STAFF)
    public MaintenanceResponse get(@PathVariable Long id) {
        return maintenanceService.get(id);
    }

    @PostMapping
    @PreAuthorize(FACILITY_STAFF)
    @ResponseStatus(HttpStatus.CREATED)
    public MaintenanceResponse create(@Valid @RequestBody MaintenanceCreateRequest request) {
        return maintenanceService.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize(FACILITY_STAFF)
    public MaintenanceResponse update(@PathVariable Long id,
                                      @Valid @RequestBody MaintenanceUpdateRequest request) {
        return maintenanceService.update(id, request);
    }
}

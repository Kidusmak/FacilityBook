package com.groupthree.Facilitybook.web;

import com.groupthree.Facilitybook.dto.common.PageResponse;
import com.groupthree.Facilitybook.dto.resource.OperatingHourResponse;
import com.groupthree.Facilitybook.dto.resource.OperatingHoursRequest;
import com.groupthree.Facilitybook.dto.resource.ResourceRequest;
import com.groupthree.Facilitybook.dto.resource.ResourceResponse;
import com.groupthree.Facilitybook.dto.resource.ResourceStatusRequest;
import com.groupthree.Facilitybook.entity.enums.ResourceStatus;
import com.groupthree.Facilitybook.service.ResourceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/resources")
@Tag(name = "Resources",
        description = "Resource catalogue, operating hours, availability search and status.")
public class ResourceController {

    private static final String MANAGERS = "hasAnyRole('ADMIN','FACILITY_MANAGER')";
    private static final String SCHEDULERS =
            "hasAnyRole('ADMIN','FACILITY_MANAGER','RESOURCE_OPERATOR')";

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping
    public PageResponse<ResourceResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) ResourceStatus status,
            @RequestParam(required = false) Integer minCapacity,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return resourceService.list(search, categoryId, buildingId, status, minCapacity, pageable);
    }

    @GetMapping("/available")
    public PageResponse<ResourceResponse> available(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Integer minCapacity,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return resourceService.searchAvailable(startTime, endTime, search, categoryId, buildingId,
                minCapacity, pageable);
    }

    @GetMapping("/{id}")
    public ResourceResponse get(@PathVariable Long id) {
        return resourceService.get(id);
    }

    @PostMapping
    @PreAuthorize(MANAGERS)
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceResponse create(@Valid @RequestBody ResourceRequest request) {
        return resourceService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(MANAGERS)
    public ResourceResponse update(@PathVariable Long id,
                                   @Valid @RequestBody ResourceRequest request) {
        return resourceService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(SCHEDULERS)
    public ResourceResponse updateStatus(@PathVariable Long id,
                                         @Valid @RequestBody ResourceStatusRequest request) {
        return resourceService.updateStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MANAGERS)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        resourceService.delete(id);
    }

    @GetMapping("/{id}/operating-hours")
    public List<OperatingHourResponse> operatingHours(@PathVariable Long id) {
        return resourceService.operatingHours(id);
    }

    @PutMapping("/{id}/operating-hours")
    @PreAuthorize(SCHEDULERS)
    public List<OperatingHourResponse> replaceOperatingHours(
            @PathVariable Long id,
            @Valid @RequestBody OperatingHoursRequest request) {
        return resourceService.replaceOperatingHours(id, request.hours());
    }
}

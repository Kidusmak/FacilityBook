package com.groupthree.Facilitybook.web;

import com.groupthree.Facilitybook.dto.building.BuildingRequest;
import com.groupthree.Facilitybook.dto.building.BuildingResponse;
import com.groupthree.Facilitybook.dto.common.PageResponse;
import com.groupthree.Facilitybook.service.BuildingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/buildings")
@Tag(name = "Buildings", description = "Building and location reference data.")
public class BuildingController {

    private static final String MANAGERS = "hasAnyRole('ADMIN','FACILITY_MANAGER')";

    private final BuildingService buildingService;

    public BuildingController(BuildingService buildingService) {
        this.buildingService = buildingService;
    }

    @GetMapping
    public PageResponse<BuildingResponse> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return buildingService.list(search, pageable);
    }

    @GetMapping("/{id}")
    public BuildingResponse get(@PathVariable Long id) {
        return buildingService.get(id);
    }

    @PostMapping
    @PreAuthorize(MANAGERS)
    @ResponseStatus(HttpStatus.CREATED)
    public BuildingResponse create(@Valid @RequestBody BuildingRequest request) {
        return buildingService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(MANAGERS)
    public BuildingResponse update(@PathVariable Long id,
                                   @Valid @RequestBody BuildingRequest request) {
        return buildingService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MANAGERS)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        buildingService.delete(id);
    }
}

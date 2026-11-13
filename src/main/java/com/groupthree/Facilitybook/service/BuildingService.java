package com.groupthree.Facilitybook.service;

import com.groupthree.Facilitybook.dto.building.BuildingRequest;
import com.groupthree.Facilitybook.dto.building.BuildingResponse;
import com.groupthree.Facilitybook.dto.common.PageResponse;
import com.groupthree.Facilitybook.entity.Building;
import com.groupthree.Facilitybook.exception.ConflictException;
import com.groupthree.Facilitybook.exception.NotFoundException;
import com.groupthree.Facilitybook.repository.BuildingRepository;
import com.groupthree.Facilitybook.repository.ResourceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BuildingService {

    private final BuildingRepository buildingRepository;
    private final ResourceRepository resourceRepository;

    public BuildingService(BuildingRepository buildingRepository, ResourceRepository resourceRepository) {
        this.buildingRepository = buildingRepository;
        this.resourceRepository = resourceRepository;
    }

    public PageResponse<BuildingResponse> list(String search, Pageable pageable) {
        Page<Building> page = hasText(search)
                ? buildingRepository.findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(
                        search.trim(), search.trim(), pageable)
                : buildingRepository.findAll(pageable);
        return PageResponse.from(page, BuildingResponse::from);
    }

    public BuildingResponse get(Long id) {
        return BuildingResponse.from(find(id));
    }

    @Transactional
    public BuildingResponse create(BuildingRequest request) {
        String name = request.name().trim();
        if (buildingRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A building named '" + name + "' already exists");
        }
        Building building = new Building();
        building.setName(name);
        building.setAddress(trimToNull(request.address()));
        return BuildingResponse.from(buildingRepository.save(building));
    }

    @Transactional
    public BuildingResponse update(Long id, BuildingRequest request) {
        Building building = find(id);
        String name = request.name().trim();
        buildingRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("A building named '" + name + "' already exists");
                });
        building.setName(name);
        building.setAddress(trimToNull(request.address()));
        return BuildingResponse.from(buildingRepository.save(building));
    }

    @Transactional
    public void delete(Long id) {
        Building building = find(id);
        if (resourceRepository.countByBuildingId(id) > 0) {
            throw new ConflictException("Building still contains resources");
        }
        buildingRepository.delete(building);
    }

    private Building find(Long id) {
        return buildingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Building " + id + " was not found"));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

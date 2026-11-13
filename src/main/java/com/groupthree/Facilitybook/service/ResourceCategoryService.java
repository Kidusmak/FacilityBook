package com.groupthree.Facilitybook.service;

import com.groupthree.Facilitybook.dto.category.ResourceCategoryRequest;
import com.groupthree.Facilitybook.dto.category.ResourceCategoryResponse;
import com.groupthree.Facilitybook.dto.common.PageResponse;
import com.groupthree.Facilitybook.entity.ResourceCategory;
import com.groupthree.Facilitybook.entity.Role;
import com.groupthree.Facilitybook.exception.ConflictException;
import com.groupthree.Facilitybook.exception.NotFoundException;
import com.groupthree.Facilitybook.repository.ResourceCategoryRepository;
import com.groupthree.Facilitybook.repository.ResourceRepository;
import com.groupthree.Facilitybook.repository.RoleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ResourceCategoryService {

    public static final String ROLE_FACILITY_MANAGER = "FACILITY_MANAGER";
    public static final String ROLE_DEPARTMENT_MANAGER = "DEPARTMENT_MANAGER";
    private static final Set<String> APPROVER_ROLES = Set.of(ROLE_FACILITY_MANAGER, ROLE_DEPARTMENT_MANAGER);

    private final ResourceCategoryRepository categoryRepository;
    private final ResourceRepository resourceRepository;
    private final RoleRepository roleRepository;

    public ResourceCategoryService(ResourceCategoryRepository categoryRepository,
                                   ResourceRepository resourceRepository,
                                   RoleRepository roleRepository) {
        this.categoryRepository = categoryRepository;
        this.resourceRepository = resourceRepository;
        this.roleRepository = roleRepository;
    }

    public PageResponse<ResourceCategoryResponse> list(String search, Pageable pageable) {
        Page<ResourceCategory> page = hasText(search)
                ? categoryRepository.findByNameContainingIgnoreCase(search.trim(), pageable)
                : categoryRepository.findAll(pageable);
        return PageResponse.from(page, ResourceCategoryResponse::from);
    }

    public ResourceCategoryResponse get(Long id) {
        return ResourceCategoryResponse.from(find(id));
    }

    @Transactional
    public ResourceCategoryResponse create(ResourceCategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A category named '" + name + "' already exists");
        }
        ResourceCategory category = new ResourceCategory();
        apply(category, request, name);
        return ResourceCategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public ResourceCategoryResponse update(Long id, ResourceCategoryRequest request) {
        ResourceCategory category = find(id);
        String name = request.name().trim();
        categoryRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("A category named '" + name + "' already exists");
                });
        apply(category, request, name);
        return ResourceCategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public void delete(Long id) {
        ResourceCategory category = find(id);
        if (resourceRepository.countByCategoryId(id) > 0) {
            throw new ConflictException("Category is still used by resources");
        }
        categoryRepository.delete(category);
    }

    private void apply(ResourceCategory category, ResourceCategoryRequest request, String name) {
        category.setName(name);
        category.setDescription(trimToNull(request.description()));
        category.setRequiresApproval(request.requiresApproval());

        if (!request.requiresApproval()) {
            category.setApproverRole(null);
            return;
        }

        if (request.approverRoleId() == null) {
            throw new IllegalArgumentException("An approver role is required when approval is enabled");
        }
        Role approverRole = roleRepository.findById(request.approverRoleId())
                .orElseThrow(() -> new NotFoundException(
                        "Role " + request.approverRoleId() + " was not found"));
        if (!APPROVER_ROLES.contains(approverRole.getName())) {
            throw new IllegalArgumentException(
                    "Approver role must be FACILITY_MANAGER or DEPARTMENT_MANAGER");
        }
        category.setApproverRole(approverRole);
    }

    private ResourceCategory find(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Resource category " + id + " was not found"));
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

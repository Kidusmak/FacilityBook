package com.groupthree.Facilitybook.service;

import com.groupthree.Facilitybook.dto.department.DepartmentRequest;
import com.groupthree.Facilitybook.dto.department.DepartmentResponse;
import com.groupthree.Facilitybook.dto.common.PageResponse;
import com.groupthree.Facilitybook.entity.Department;
import com.groupthree.Facilitybook.exception.ConflictException;
import com.groupthree.Facilitybook.exception.NotFoundException;
import com.groupthree.Facilitybook.repository.DepartmentRepository;
import com.groupthree.Facilitybook.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    public DepartmentService(DepartmentRepository departmentRepository, UserRepository userRepository) {
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
    }

    public PageResponse<DepartmentResponse> list(String search, Pageable pageable) {
        Page<Department> page = hasText(search)
                ? departmentRepository.findByNameContainingIgnoreCase(search.trim(), pageable)
                : departmentRepository.findAll(pageable);
        return PageResponse.from(page, DepartmentResponse::from);
    }

    public DepartmentResponse get(Long id) {
        return DepartmentResponse.from(find(id));
    }

    @Transactional
    public DepartmentResponse create(DepartmentRequest request) {
        String name = request.name().trim();
        if (departmentRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A department named '" + name + "' already exists");
        }
        Department department = new Department();
        department.setName(name);
        return DepartmentResponse.from(departmentRepository.save(department));
    }

    @Transactional
    public DepartmentResponse update(Long id, DepartmentRequest request) {
        Department department = find(id);
        String name = request.name().trim();
        departmentRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("A department named '" + name + "' already exists");
                });
        department.setName(name);
        return DepartmentResponse.from(departmentRepository.save(department));
    }

    @Transactional
    public void delete(Long id) {
        Department department = find(id);
        if (userRepository.countByDepartmentId(id) > 0) {
            throw new ConflictException("Department is still assigned to users");
        }
        departmentRepository.delete(department);
    }

    private Department find(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Department " + id + " was not found"));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

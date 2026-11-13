package com.groupthree.Facilitybook.service;

import com.groupthree.Facilitybook.dto.common.PageResponse;
import com.groupthree.Facilitybook.dto.user.CreateUserRequest;
import com.groupthree.Facilitybook.dto.user.UpdateUserRequest;
import com.groupthree.Facilitybook.dto.user.UserResponse;
import com.groupthree.Facilitybook.entity.Department;
import com.groupthree.Facilitybook.entity.Role;
import com.groupthree.Facilitybook.entity.User;
import com.groupthree.Facilitybook.exception.ConflictException;
import com.groupthree.Facilitybook.exception.NotFoundException;
import com.groupthree.Facilitybook.repository.DepartmentRepository;
import com.groupthree.Facilitybook.repository.RoleRepository;
import com.groupthree.Facilitybook.repository.UserRepository;
import com.groupthree.Facilitybook.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       DepartmentRepository departmentRepository,
                       PasswordEncoder passwordEncoder,
                       CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
    }

    public PageResponse<UserResponse> list(String search, Short roleId, Long departmentId,
                                           Boolean active, Pageable pageable) {
        Specification<User> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("email")), pattern),
                        cb.like(cb.lower(root.get("fullName")), pattern)));
            }
            if (roleId != null) {
                predicates.add(cb.equal(root.get("role").get("id"), roleId));
            }
            if (departmentId != null) {
                predicates.add(cb.equal(root.get("department").get("id"), departmentId));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<User> page = userRepository.findAll(specification, pageable);
        return PageResponse.from(page, UserResponse::from);
    }

    public UserResponse get(Long id) {
        return UserResponse.from(find(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("A user with email '" + email + "' already exists");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setRole(findRole(request.roleId()));
        user.setDepartment(findDepartmentOrNull(request.departmentId()));
        user.setActive(request.active() == null || request.active());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = find(id);

        if (request.fullName() != null) {
            user.setFullName(request.fullName().trim());
        }
        if (request.roleId() != null) {
            user.setRole(findRole(request.roleId()));
        }
        if (request.departmentId() != null) {
            user.setDepartment(findDepartmentOrNull(request.departmentId()));
        }
        if (request.active() != null) {
            if (!request.active() && id.equals(currentUser.id())) {
                throw new ConflictException("You cannot deactivate your own account");
            }
            user.setActive(request.active());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return UserResponse.from(userRepository.save(user));
    }

    private User find(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User " + id + " was not found"));
    }

    private Role findRole(Short roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("Role " + roleId + " was not found"));
    }

    private Department findDepartmentOrNull(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new NotFoundException("Department " + departmentId + " was not found"));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

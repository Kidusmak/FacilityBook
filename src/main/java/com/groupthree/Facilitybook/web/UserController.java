package com.groupthree.Facilitybook.web;

import com.groupthree.Facilitybook.dto.common.PageResponse;
import com.groupthree.Facilitybook.dto.user.CreateUserRequest;
import com.groupthree.Facilitybook.dto.user.RoleResponse;
import com.groupthree.Facilitybook.dto.user.UpdateUserRequest;
import com.groupthree.Facilitybook.dto.user.UserResponse;
import com.groupthree.Facilitybook.entity.Role;
import com.groupthree.Facilitybook.repository.RoleRepository;
import com.groupthree.Facilitybook.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

import java.util.List;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Users", description = "User, role and account administration (ADMIN only).")
public class UserController {

    private final UserService userService;
    private final RoleRepository roleRepository;

    public UserController(UserService userService, RoleRepository roleRepository) {
        this.userService = userService;
        this.roleRepository = roleRepository;
    }

    @GetMapping
    public PageResponse<UserResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Short roleId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "fullName") Pageable pageable) {
        return userService.list(search, roleId, departmentId, active, pageable);
    }

    @GetMapping("/roles")
    public List<RoleResponse> roles() {
        List<Role> roles = roleRepository.findAll(Sort.by("id"));
        return roles.stream().map(RoleResponse::from).toList();
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id,
                               @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        userService.delete(id);
    }
}

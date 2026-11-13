package com.groupthree.Facilitybook.repository;

import com.groupthree.Facilitybook.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    @Override
    @EntityGraph(attributePaths = {"role", "department"})
    Page<User> findAll(Specification<User> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"role", "department"})
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByDepartmentId(Long departmentId);

    @EntityGraph(attributePaths = {"role", "department"})
    List<User> findByRoleNameAndActiveTrue(String roleName);

    @EntityGraph(attributePaths = {"role", "department"})
    List<User> findByRoleNameAndDepartmentIdAndActiveTrue(String roleName, Long departmentId);
}

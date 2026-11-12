package com.groupthree.Facilitybook.repository;

import com.groupthree.Facilitybook.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Short> {

    Optional<Role> findByName(String name);
}

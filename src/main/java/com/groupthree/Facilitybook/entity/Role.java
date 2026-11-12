package com.groupthree.Facilitybook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role {

    @Id
    @Column(name = "id", nullable = false)
    private Short id;

    @Column(name = "name", nullable = false, unique = true, length = 30)
    private String name;
}

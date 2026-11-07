package com.enterpriseflow.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Reference data seeded by Flyway (V1). Never created or modified by the application. */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    private Short id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 20)
    private RoleName name;

    protected Role() {
        // Required by JPA
    }

    public Short getId() {
        return id;
    }

    public RoleName getName() {
        return name;
    }
}

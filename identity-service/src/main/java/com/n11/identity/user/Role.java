package com.n11.identity.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Roles table. Pre-seeded with rows (1, 'ROLE_USER') and (2, 'ROLE_ADMIN')
 * by V2__init_users_addresses.sql.
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    protected Role() { /* JPA */ }

    public Integer getId()   { return id; }
    public String getName()  { return name; }

    public static final String NAME_USER  = "ROLE_USER";
    public static final String NAME_ADMIN = "ROLE_ADMIN";
}

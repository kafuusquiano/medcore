package com.medcore.auth.domain.entity;

import com.medcore.auth.domain.enums.RoleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Rol asignable a un usuario. Las filas las crea V1__create_roles_users.sql;
 * la aplicacion no inserta roles nuevos.
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Se guarda como texto, no como ordinal: insertar un valor nuevo en medio
        del enum reasignaria los roles existentes de forma silenciosa. */
    @Enumerated(EnumType.STRING)
    @Column(name = "nombre", nullable = false, unique = true, length = 30)
    private RoleType nombre;

    @Column(name = "descripcion", length = 150)
    private String descripcion;

    protected Role() {
        // Requerido por JPA.
    }

    public Role(RoleType nombre, String descripcion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public Long getId() {
        return id;
    }

    public RoleType getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Role role && nombre == role.nombre;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(nombre);
    }

    @Override
    public String toString() {
        return "Role[" + nombre + "]";
    }
}

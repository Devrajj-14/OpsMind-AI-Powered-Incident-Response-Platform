package com.opsmind.core;

import com.opsmind.domain.Types.Role;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "app_user")
class User {
    @Id UUID id;
    @Column(nullable=false, unique=true) String email;
    @Column(name="password_hash", nullable=false) String passwordHash;
    @Column(name="display_name", nullable=false) String displayName;
    @Enumerated(EnumType.STRING) @Column(nullable=false) Role role;
    @Column(nullable=false) boolean active = true;
    @Column(name="created_at", nullable=false) Instant createdAt;
    protected User() {}
    User(String email, String passwordHash, String displayName, Role role) {
        this.id=UUID.randomUUID(); this.email=email.toLowerCase(); this.passwordHash=passwordHash;
        this.displayName=displayName; this.role=role; this.createdAt=Instant.now();
    }
}

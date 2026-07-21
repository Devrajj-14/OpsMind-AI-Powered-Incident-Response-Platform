package com.opsmind.core;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="refresh_token")
class RefreshToken {
    @Id UUID id;
    @Column(name="user_id", nullable=false) UUID userId;
    @Column(name="token_hash", nullable=false, unique=true) String tokenHash;
    @Column(name="expires_at", nullable=false) Instant expiresAt;
    @Column(nullable=false) boolean revoked;
    @Column(name="created_at", nullable=false) Instant createdAt;
    protected RefreshToken() {}
    RefreshToken(UUID userId, String tokenHash, Instant expiresAt) {
        this.id=UUID.randomUUID(); this.userId=userId; this.tokenHash=tokenHash;
        this.expiresAt=expiresAt; this.createdAt=Instant.now();
    }
}

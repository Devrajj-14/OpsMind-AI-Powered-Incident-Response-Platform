package com.opsmind.core;

import com.opsmind.domain.Types.ServiceStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="monitored_service")
class MonitoredService {
    @Id UUID id;
    @Column(nullable=false) String name;
    @Column(nullable=false) String environment;
    @Column(name="owner_team", nullable=false) String ownerTeam;
    @Column(name="api_key_hash", nullable=false, unique=true) String apiKeyHash;
    @Enumerated(EnumType.STRING) @Column(nullable=false) ServiceStatus status;
    @Column(name="created_at", nullable=false) Instant createdAt;
    protected MonitoredService() {}
    MonitoredService(String name, String environment, String ownerTeam, String apiKeyHash) {
        this.id=UUID.randomUUID(); this.name=name; this.environment=environment; this.ownerTeam=ownerTeam;
        this.apiKeyHash=apiKeyHash; this.status=ServiceStatus.UNKNOWN; this.createdAt=Instant.now();
    }
}

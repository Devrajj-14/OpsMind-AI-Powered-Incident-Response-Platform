package com.opsmind.core;

import com.opsmind.domain.Types.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
class Incident {
    @Id UUID id;
    @Column(nullable=false) String title;
    @Enumerated(EnumType.STRING) @Column(nullable=false) IncidentStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable=false) Severity severity;
    @Column(name="assignee_id") UUID assigneeId;
    @Column(name="service_id", nullable=false) UUID serviceId;
    @Column(nullable=false) String fingerprint;
    @Column(name="alert_count", nullable=false) int alertCount;
    @Column(name="opened_at", nullable=false) Instant openedAt;
    @Column(name="acknowledged_at") Instant acknowledgedAt;
    @Column(name="resolved_at") Instant resolvedAt;
    @Column(name="resolution_summary") String resolutionSummary;
    @Version long version;
    protected Incident() {}
    Incident(String title, Severity severity, UUID serviceId, String fingerprint) {
        this.id=UUID.randomUUID(); this.title=title; this.status=IncidentStatus.OPEN; this.severity=severity;
        this.serviceId=serviceId; this.fingerprint=fingerprint; this.alertCount=1; this.openedAt=Instant.now();
    }
}

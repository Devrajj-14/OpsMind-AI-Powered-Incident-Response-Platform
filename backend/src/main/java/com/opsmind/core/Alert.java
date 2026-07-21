package com.opsmind.core;

import com.opsmind.domain.Types.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
class Alert {
    @Id UUID id;
    @Column(name="service_id", nullable=false) UUID serviceId;
    @Column(name="rule_id", nullable=false) UUID ruleId;
    @Column(name="incident_id") UUID incidentId;
    @Column(nullable=false) String fingerprint;
    @Enumerated(EnumType.STRING) @Column(nullable=false) Severity severity;
    @Enumerated(EnumType.STRING) @Column(nullable=false) AlertStatus status;
    @Column(nullable=false) String summary;
    @Column(nullable=false) String evidence;
    @Column(name="triggered_at", nullable=false) Instant triggeredAt;
    protected Alert() {}
    Alert(UUID serviceId, UUID ruleId, String fingerprint, Severity severity, String summary, String evidence) {
        this.id=UUID.randomUUID(); this.serviceId=serviceId; this.ruleId=ruleId; this.fingerprint=fingerprint;
        this.severity=severity; this.status=AlertStatus.OPEN; this.summary=summary; this.evidence=evidence; this.triggeredAt=Instant.now();
    }
}

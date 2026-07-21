package com.opsmind.core;

import com.opsmind.domain.Types.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="alert_rule")
class AlertRule {
    @Id UUID id;
    @Column(name="service_id", nullable=false) UUID serviceId;
    @Column(nullable=false) String name;
    @Enumerated(EnumType.STRING) @Column(name="rule_type", nullable=false) RuleType ruleType;
    String keyword;
    @Column(name="threshold_count", nullable=false) int thresholdCount;
    @Column(name="window_seconds", nullable=false) int windowSeconds;
    @Enumerated(EnumType.STRING) @Column(nullable=false) Severity severity;
    @Column(name="deduplication_seconds", nullable=false) int deduplicationSeconds;
    @Column(nullable=false) boolean enabled;
    @Column(name="created_at", nullable=false) Instant createdAt;
    protected AlertRule() {}
    AlertRule(UUID serviceId, String name, RuleType type, String keyword, int threshold, int window, Severity severity, int dedupSeconds) {
        this.id=UUID.randomUUID(); this.serviceId=serviceId; this.name=name; this.ruleType=type; this.keyword=keyword;
        this.thresholdCount=threshold; this.windowSeconds=window; this.severity=severity; this.deduplicationSeconds=dedupSeconds;
        this.enabled=true; this.createdAt=Instant.now();
    }
}

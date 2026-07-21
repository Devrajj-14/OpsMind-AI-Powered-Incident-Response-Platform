package com.opsmind.core;

import com.opsmind.domain.Types.LogLevel;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="log_event")
class LogEvent {
    @Id UUID id;
    @Column(name="external_event_id", nullable=false, unique=true) String externalEventId;
    @Column(name="service_id", nullable=false) UUID serviceId;
    @Column(name="occurred_at", nullable=false) Instant occurredAt;
    @Enumerated(EnumType.STRING) @Column(nullable=false) LogLevel level;
    @Column(nullable=false, length=4000) String message;
    @Column(name="trace_id") String traceId;
    String host;
    @Column(nullable=false) String attributes;
    @Column(name="created_at", nullable=false) Instant createdAt;
    protected LogEvent() {}
    LogEvent(String eventId, UUID serviceId, Instant occurredAt, LogLevel level, String message, String traceId, String host, String attributes) {
        this.id=UUID.randomUUID(); this.externalEventId=eventId; this.serviceId=serviceId; this.occurredAt=occurredAt;
        this.level=level; this.message=message; this.traceId=traceId; this.host=host; this.attributes=attributes; this.createdAt=Instant.now();
    }
}

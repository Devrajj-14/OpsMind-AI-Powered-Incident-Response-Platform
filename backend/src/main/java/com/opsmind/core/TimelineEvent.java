package com.opsmind.core;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="timeline_event")
class TimelineEvent {
    @Id UUID id;
    @Column(name="incident_id", nullable=false) UUID incidentId;
    @Column(name="actor_id") UUID actorId;
    @Column(name="event_type", nullable=false) String eventType;
    @Column(nullable=false) String description;
    @Column(name="created_at", nullable=false) Instant createdAt;
    protected TimelineEvent() {}
    TimelineEvent(UUID incidentId, UUID actorId, String eventType, String description) {
        this.id=UUID.randomUUID(); this.incidentId=incidentId; this.actorId=actorId; this.eventType=eventType;
        this.description=description; this.createdAt=Instant.now();
    }
}

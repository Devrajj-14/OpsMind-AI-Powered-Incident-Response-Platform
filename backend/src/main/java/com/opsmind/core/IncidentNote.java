package com.opsmind.core;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="incident_note")
class IncidentNote {
    @Id UUID id;
    @Column(name="incident_id", nullable=false) UUID incidentId;
    @Column(name="author_id", nullable=false) UUID authorId;
    @Column(nullable=false) String body;
    @Column(name="created_at", nullable=false) Instant createdAt;
    protected IncidentNote() {}
    IncidentNote(UUID incidentId, UUID authorId, String body) {
        this.id=UUID.randomUUID(); this.incidentId=incidentId; this.authorId=authorId; this.body=body; this.createdAt=Instant.now();
    }
}

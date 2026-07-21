package com.opsmind.core;

import com.opsmind.domain.Types.AnalysisStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="ai_analysis")
class AiAnalysis {
    @Id UUID id;
    @Column(name="incident_id", nullable=false) UUID incidentId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) AnalysisStatus status;
    String summary;
    String hypotheses;
    @Column(name="evidence_refs") String evidenceRefs;
    @Column(nullable=false) String model;
    @Column(name="error_message") String errorMessage;
    @Column(name="created_at", nullable=false) Instant createdAt;
    @Column(name="completed_at") Instant completedAt;
    protected AiAnalysis() {}
    AiAnalysis(UUID incidentId) {
        this.id=UUID.randomUUID(); this.incidentId=incidentId; this.status=AnalysisStatus.QUEUED;
        this.model="opsmind-local-evidence-v1"; this.createdAt=Instant.now();
    }
}

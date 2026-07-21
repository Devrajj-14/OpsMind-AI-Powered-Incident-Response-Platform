package com.opsmind.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsmind.domain.Types.AnalysisStatus;
import com.opsmind.domain.Types.LogLevel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
class AnalysisWorker {
    private final AiAnalysisRepository analyses;
    private final IncidentRepository incidents;
    private final AlertRepository alerts;
    private final LogEventRepository logs;
    private final TimelineRepository timeline;
    private final ObjectMapper mapper;

    AnalysisWorker(AiAnalysisRepository analyses, IncidentRepository incidents, AlertRepository alerts,
                   LogEventRepository logs, TimelineRepository timeline, ObjectMapper mapper) {
        this.analyses=analyses; this.incidents=incidents; this.alerts=alerts; this.logs=logs; this.timeline=timeline; this.mapper=mapper;
    }

    @Async
    void run(UUID id) {
        AiAnalysis analysis=analyses.findById(id).orElse(null);
        if(analysis==null) return;
        try {
            analysis.status=AnalysisStatus.RUNNING;
            analyses.save(analysis);
            Incident incident=incidents.findById(analysis.incidentId).orElseThrow();
            var incidentAlerts=alerts.findByIncidentIdOrderByTriggeredAtDesc(incident.id);
            var recent=logs.findTop100ByServiceIdOrderByOccurredAtDesc(incident.serviceId);
            String topError=recent.stream().filter(x->x.level==LogLevel.ERROR||x.level==LogLevel.FATAL)
                .map(x->x.message).findFirst().orElse("No error log was available");
            analysis.summary="Incident has "+incident.alertCount+" correlated alert(s). The strongest recent signal is: "+redact(topError);
            analysis.hypotheses=toJson(List.of(Map.of(
                "rank",1,
                "cause",categorize(topError),
                "confidence","medium",
                "reasoning","Derived from the most recent error evidence; verify with service metrics and recent changes.",
                "nextChecks",List.of("Inspect the referenced logs","Compare recent deployments","Verify dependency health")
            )));
            analysis.evidenceRefs=toJson(incidentAlerts.stream().limit(10).map(x->"alert:"+x.id).toList());
            analysis.status=AnalysisStatus.COMPLETED;
            analysis.completedAt=Instant.now();
            analyses.save(analysis);
            timeline.save(new TimelineEvent(incident.id,null,"AI_ANALYSIS_COMPLETED","Evidence-based analysis completed"));
        } catch(Exception error) {
            analysis.status=AnalysisStatus.FAILED;
            analysis.errorMessage="Analysis could not be completed";
            analysis.completedAt=Instant.now();
            analyses.save(analysis);
        }
    }

    private String categorize(String message) {
        String normalized=message.toLowerCase();
        if(normalized.contains("database")||normalized.contains("connection")) return "Database connectivity or connection-pool pressure";
        if(normalized.contains("memory")) return "Memory exhaustion";
        if(normalized.contains("timeout")) return "Upstream dependency timeout";
        return "Application error requiring trace-level investigation";
    }

    private String redact(String value) {
        return value.replaceAll("(?i)(password|token|secret|api[_-]?key)\\s*[=:]\\s*\\S+","$1=[REDACTED]");
    }

    private String toJson(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch(Exception error) { return "{}"; }
    }
}

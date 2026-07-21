package com.opsmind.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsmind.domain.Types.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
class PlatformService {
    private final MonitoredServiceRepository services; private final LogEventRepository logs; private final AlertRuleRepository rules;
    private final AlertRepository alerts; private final IncidentRepository incidents; private final TimelineRepository timeline;
    private final IncidentNoteRepository notes; private final AiAnalysisRepository analyses; private final ObjectMapper mapper; private final AnalysisWorker analysisWorker;
    private final Deduplicator deduplicator;
    PlatformService(MonitoredServiceRepository services, LogEventRepository logs, AlertRuleRepository rules, AlertRepository alerts,
                    IncidentRepository incidents, TimelineRepository timeline, IncidentNoteRepository notes, AiAnalysisRepository analyses, ObjectMapper mapper, Deduplicator deduplicator, AnalysisWorker analysisWorker) {
        this.services=services;this.logs=logs;this.rules=rules;this.alerts=alerts;this.incidents=incidents;this.timeline=timeline;this.notes=notes;this.analyses=analyses;this.mapper=mapper;this.deduplicator=deduplicator;this.analysisWorker=analysisWorker;
    }
    record Ingested(UUID id,String eventId) {}
    @Transactional Ingested ingest(MonitoredService svc, String eventId, Instant occurredAt, LogLevel level, String message, String traceId, String host, Map<String,Object> attributes) {
        String eid=(eventId==null||eventId.isBlank())?UUID.randomUUID().toString():eventId;
        Optional<LogEvent> existing=logs.findByExternalEventId(eid);
        if(existing.isPresent()) return new Ingested(existing.get().id,eid);
        LogEvent log;
        try { log=logs.save(new LogEvent(eid,svc.id,occurredAt==null?Instant.now():occurredAt,level,message,traceId,host,toJson(attributes))); }
        catch(org.springframework.dao.DataIntegrityViolationException e) { throw ApiException.conflict("eventId was already ingested"); }
        svc.status=level==LogLevel.FATAL?ServiceStatus.DEGRADED:ServiceStatus.HEALTHY;
        evaluate(svc,log); return new Ingested(log.id,eid);
    }
    private void evaluate(MonitoredService svc, LogEvent log) {
        for(AlertRule rule:rules.findByServiceIdAndEnabledTrue(svc.id)) {
            boolean keyword=rule.keyword==null || log.message.toLowerCase().contains(rule.keyword.toLowerCase());
            boolean match=switch(rule.ruleType) {
                case KEYWORD -> keyword;
                case COUNT_THRESHOLD -> keyword && logs.countByServiceIdAndLevelAndOccurredAtAfter(svc.id,LogLevel.ERROR,Instant.now().minusSeconds(rule.windowSeconds))>=rule.thresholdCount;
            };
            if(match) createAlertAndIncident(svc,rule,log);
        }
    }
    private void createAlertAndIncident(MonitoredService svc, AlertRule rule, LogEvent log) {
        String normalized=log.message.toLowerCase().replaceAll("[0-9a-f]{8}-[0-9a-f-]{27,}","{id}").replaceAll("\\d+","{n}");
        String fingerprint=Hashing.sha256(svc.id+":"+rule.id+":"+normalized);
        if(!deduplicator.acquire(fingerprint,Duration.ofSeconds(rule.deduplicationSeconds))) return;
        Alert alert=alerts.save(new Alert(svc.id,rule.id,fingerprint,rule.severity,rule.name+": "+log.message,toJson(Map.of("logId",log.id,"traceId",Objects.toString(log.traceId,"")))));
        Incident incident=incidents.findFirstByFingerprintAndStatusNotOrderByOpenedAtDesc(fingerprint,IncidentStatus.CLOSED).orElse(null);
        if(incident==null) {
            incident=incidents.save(new Incident(rule.name,rule.severity,svc.id,fingerprint));
            timeline.save(new TimelineEvent(incident.id,null,"CREATED","Incident created automatically from alert rule '"+rule.name+"'"));
        } else { incident.alertCount++; incident=incidents.save(incident); timeline.save(new TimelineEvent(incident.id,null,"ALERT_ATTACHED","Related alert attached")); }
        alert.incidentId=incident.id; alerts.save(alert);
    }
    @Transactional Incident transition(UUID id, IncidentStatus target, UUID assignee, String resolution, AuthPrincipal actor) {
        Incident i=incidents.findById(id).orElseThrow(()->ApiException.notFound("Incident"));
        if(!allowed(i.status,target)) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"Transition from "+i.status+" to "+target+" is not allowed");
        if(target==IncidentStatus.ACKNOWLEDGED && assignee==null && i.assigneeId==null) throw new ApiException(HttpStatus.BAD_REQUEST,"Assignee is required when acknowledging");
        if(target==IncidentStatus.RESOLVED && (resolution==null||resolution.isBlank())) throw new ApiException(HttpStatus.BAD_REQUEST,"Resolution summary is required");
        IncidentStatus old=i.status; i.status=target; if(assignee!=null)i.assigneeId=assignee;
        if(target==IncidentStatus.ACKNOWLEDGED && i.acknowledgedAt==null)i.acknowledgedAt=Instant.now();
        if(target==IncidentStatus.RESOLVED){i.resolvedAt=Instant.now();i.resolutionSummary=resolution;}
        if(target==IncidentStatus.OPEN)i.resolvedAt=null;
        timeline.save(new TimelineEvent(i.id,actor.id(),"STATUS_CHANGED",old+" -> "+target)); return incidents.save(i);
    }
    private boolean allowed(IncidentStatus from, IncidentStatus to) { return switch(from) {
        case OPEN -> Set.of(IncidentStatus.ACKNOWLEDGED,IncidentStatus.RESOLVED).contains(to);
        case ACKNOWLEDGED -> Set.of(IncidentStatus.INVESTIGATING,IncidentStatus.RESOLVED).contains(to);
        case INVESTIGATING -> Set.of(IncidentStatus.MITIGATED,IncidentStatus.RESOLVED).contains(to);
        case MITIGATED -> Set.of(IncidentStatus.INVESTIGATING,IncidentStatus.RESOLVED).contains(to);
        case RESOLVED -> Set.of(IncidentStatus.OPEN,IncidentStatus.CLOSED).contains(to);
        case CLOSED -> false;
    }; }
    @Transactional IncidentNote addNote(UUID incidentId, UUID actor, String body) {
        incidents.findById(incidentId).orElseThrow(()->ApiException.notFound("Incident"));
        IncidentNote note=notes.save(new IncidentNote(incidentId,actor,body)); timeline.save(new TimelineEvent(incidentId,actor,"NOTE_ADDED","Investigation note added")); return note;
    }
    AiAnalysis queueAnalysis(UUID incidentId) { incidents.findById(incidentId).orElseThrow(()->ApiException.notFound("Incident")); AiAnalysis a=analyses.save(new AiAnalysis(incidentId)); analysisWorker.run(a.id); return a; }
    private String toJson(Object value) { try{return mapper.writeValueAsString(value==null?Map.of():value);}catch(Exception e){return "{}";} }
}

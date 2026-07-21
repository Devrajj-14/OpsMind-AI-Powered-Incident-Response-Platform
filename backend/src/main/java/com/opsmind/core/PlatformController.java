package com.opsmind.core;

import com.opsmind.domain.Types.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;

@RestController @RequestMapping("/api/v1")
class PlatformController {
    private final MonitoredServiceRepository services; private final LogEventRepository logs; private final AlertRuleRepository rules;
    private final AlertRepository alerts; private final IncidentRepository incidents; private final IncidentNoteRepository notes;
    private final TimelineRepository timeline; private final AiAnalysisRepository analyses; private final UserRepository users; private final PlatformService platform; private final IngestionDispatcher ingestion;
    PlatformController(MonitoredServiceRepository services, LogEventRepository logs, AlertRuleRepository rules, AlertRepository alerts, IncidentRepository incidents,
                       IncidentNoteRepository notes, TimelineRepository timeline, AiAnalysisRepository analyses, UserRepository users, PlatformService platform, IngestionDispatcher ingestion) {
        this.services=services;this.logs=logs;this.rules=rules;this.alerts=alerts;this.incidents=incidents;this.notes=notes;this.timeline=timeline;this.analyses=analyses;this.users=users;this.platform=platform;this.ingestion=ingestion;
    }
    record ServiceRequest(@NotBlank @Size(max=120) String name,@NotBlank @Size(max=50) String environment,@NotBlank @Size(max=120) String ownerTeam) {}
    record ServiceView(UUID id,String name,String environment,String ownerTeam,ServiceStatus status,Instant createdAt,String apiKey) {
        static ServiceView of(MonitoredService s){return new ServiceView(s.id,s.name,s.environment,s.ownerTeam,s.status,s.createdAt,null);}
    }
    @GetMapping("/services") List<ServiceView> services(){return services.findAll().stream().map(ServiceView::of).toList();}
    @PostMapping("/services") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')") @Transactional
    ServiceView createService(@Valid @RequestBody ServiceRequest r){String key="opm_"+Hashing.randomToken();MonitoredService s=services.save(new MonitoredService(r.name(),r.environment(),r.ownerTeam(),Hashing.sha256(key)));return new ServiceView(s.id,s.name,s.environment,s.ownerTeam,s.status,s.createdAt,key);}
    @PostMapping("/services/{id}/rotate-key") @PreAuthorize("hasRole('ADMIN')") @Transactional
    Map<String,String> rotateKey(@PathVariable UUID id){MonitoredService s=services.findById(id).orElseThrow(()->ApiException.notFound("Service"));String key="opm_"+Hashing.randomToken();s.apiKeyHash=Hashing.sha256(key);return Map.of("apiKey",key);}

    record LogRequest(String eventId,Instant occurredAt,@NotNull LogLevel level,@NotBlank @Size(max=4000) String message,@Size(max=255) String traceId,@Size(max=255) String host,Map<String,Object> attributes) {}
    @PostMapping("/ingestion/logs") @ResponseStatus(HttpStatus.ACCEPTED) @Transactional
    List<PlatformService.Ingested> ingest(@RequestHeader("X-OpsMind-Key") String key,@Valid @RequestBody List<@Valid LogRequest> batch){
        if(batch.isEmpty()||batch.size()>500)throw new ApiException(HttpStatus.BAD_REQUEST,"Batch must contain 1 to 500 logs");
        MonitoredService s=services.findByApiKeyHash(Hashing.sha256(key)).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"Invalid ingestion key"));
        return batch.stream().map(x->ingestion.dispatch(new LogCommand(x.eventId(),s.id,x.occurredAt(),x.level(),x.message(),x.traceId(),x.host(),x.attributes()))).toList();
    }
    record LogView(UUID id,String eventId,UUID serviceId,Instant occurredAt,LogLevel level,String message,String traceId,String host,String attributes) {static LogView of(LogEvent l){return new LogView(l.id,l.externalEventId,l.serviceId,l.occurredAt,l.level,l.message,l.traceId,l.host,l.attributes);}}
    @GetMapping("/logs") Map<String,Object> logs(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") @Max(100) int size){var p=logs.findAllByOrderByOccurredAtDesc(PageRequest.of(page,size));return page(p.getContent().stream().map(LogView::of).toList(),p.getTotalElements(),p.getTotalPages(),page);}

    record RuleRequest(@NotNull UUID serviceId,@NotBlank @Size(max=160) String name,@NotNull RuleType ruleType,@Size(max=255) String keyword,@Min(1) int thresholdCount,@Min(10) @Max(86400) int windowSeconds,@NotNull Severity severity,@Min(1) @Max(86400) int deduplicationSeconds) {}
    record RuleView(UUID id,UUID serviceId,String name,RuleType ruleType,String keyword,int thresholdCount,int windowSeconds,Severity severity,int deduplicationSeconds,boolean enabled) {static RuleView of(AlertRule r){return new RuleView(r.id,r.serviceId,r.name,r.ruleType,r.keyword,r.thresholdCount,r.windowSeconds,r.severity,r.deduplicationSeconds,r.enabled);}}
    @GetMapping("/alert-rules") List<RuleView> rules(){return rules.findAll().stream().map(RuleView::of).toList();}
    @PostMapping("/alert-rules") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')")
    RuleView createRule(@Valid @RequestBody RuleRequest r){services.findById(r.serviceId()).orElseThrow(()->ApiException.notFound("Service"));if(r.ruleType()==RuleType.KEYWORD&&(r.keyword()==null||r.keyword().isBlank()))throw new ApiException(HttpStatus.BAD_REQUEST,"Keyword is required for a keyword rule");return RuleView.of(rules.save(new AlertRule(r.serviceId(),r.name(),r.ruleType(),r.keyword(),r.thresholdCount(),r.windowSeconds(),r.severity(),r.deduplicationSeconds())));}
    @PatchMapping("/alert-rules/{id}/enabled") @PreAuthorize("hasRole('ADMIN')") @Transactional
    RuleView toggle(@PathVariable UUID id,@RequestBody Map<String,Boolean> body){AlertRule r=rules.findById(id).orElseThrow(()->ApiException.notFound("Alert rule"));r.enabled=Boolean.TRUE.equals(body.get("enabled"));return RuleView.of(r);}

    record AlertView(UUID id,UUID serviceId,UUID ruleId,UUID incidentId,String fingerprint,Severity severity,AlertStatus status,String summary,String evidence,Instant triggeredAt){static AlertView of(Alert a){return new AlertView(a.id,a.serviceId,a.ruleId,a.incidentId,a.fingerprint,a.severity,a.status,a.summary,a.evidence,a.triggeredAt);}}
    @GetMapping("/alerts") Map<String,Object> alerts(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")@Max(100)int size){var p=alerts.findAllByOrderByTriggeredAtDesc(PageRequest.of(page,size));return page(p.getContent().stream().map(AlertView::of).toList(),p.getTotalElements(),p.getTotalPages(),page);}

    record IncidentView(UUID id,String title,IncidentStatus status,Severity severity,UUID assigneeId,UUID serviceId,int alertCount,Instant openedAt,Instant acknowledgedAt,Instant resolvedAt,String resolutionSummary,long version){static IncidentView of(Incident i){return new IncidentView(i.id,i.title,i.status,i.severity,i.assigneeId,i.serviceId,i.alertCount,i.openedAt,i.acknowledgedAt,i.resolvedAt,i.resolutionSummary,i.version);}}
    record NoteView(UUID id,UUID authorId,String body,Instant createdAt){static NoteView of(IncidentNote n){return new NoteView(n.id,n.authorId,n.body,n.createdAt);}}
    record TimelineView(UUID id,UUID actorId,String eventType,String description,Instant createdAt){static TimelineView of(TimelineEvent t){return new TimelineView(t.id,t.actorId,t.eventType,t.description,t.createdAt);}}
    record AnalysisView(UUID id,AnalysisStatus status,String summary,String hypotheses,String evidenceRefs,String model,String errorMessage,Instant createdAt,Instant completedAt){static AnalysisView of(AiAnalysis a){return new AnalysisView(a.id,a.status,a.summary,a.hypotheses,a.evidenceRefs,a.model,a.errorMessage,a.createdAt,a.completedAt);}}
    @GetMapping("/incidents") Map<String,Object> incidents(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")@Max(100)int size){var p=incidents.findAllByOrderByOpenedAtDesc(PageRequest.of(page,size));return page(p.getContent().stream().map(IncidentView::of).toList(),p.getTotalElements(),p.getTotalPages(),page);}
    @GetMapping("/incidents/{id}") Map<String,Object> incident(@PathVariable UUID id){Incident i=incidents.findById(id).orElseThrow(()->ApiException.notFound("Incident"));return Map.of("incident",IncidentView.of(i),"alerts",alerts.findByIncidentIdOrderByTriggeredAtDesc(id).stream().map(AlertView::of).toList(),"notes",notes.findByIncidentIdOrderByCreatedAtAsc(id).stream().map(NoteView::of).toList(),"timeline",timeline.findByIncidentIdOrderByCreatedAtAsc(id).stream().map(TimelineView::of).toList(),"analyses",analyses.findByIncidentIdOrderByCreatedAtDesc(id).stream().map(AnalysisView::of).toList());}
    record TransitionRequest(@NotNull IncidentStatus status,UUID assigneeId,@Size(max=2000) String resolutionSummary){}
    @PatchMapping("/incidents/{id}/status") @PreAuthorize("hasAnyRole('ADMIN','ENGINEER')") IncidentView transition(@PathVariable UUID id,@Valid @RequestBody TransitionRequest r,Authentication a){return IncidentView.of(platform.transition(id,r.status(),r.assigneeId(),r.resolutionSummary(),AuthController.principal(a)));}
    record NoteRequest(@NotBlank @Size(max=4000) String body){}
    @PostMapping("/incidents/{id}/notes") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('ADMIN','ENGINEER')") NoteView note(@PathVariable UUID id,@Valid @RequestBody NoteRequest r,Authentication a){return NoteView.of(platform.addNote(id,AuthController.principal(a).id(),r.body()));}
    @PostMapping("/incidents/{id}/analyses") @ResponseStatus(HttpStatus.ACCEPTED) @PreAuthorize("hasAnyRole('ADMIN','ENGINEER')") AnalysisView analyze(@PathVariable UUID id){return AnalysisView.of(platform.queueAnalysis(id));}
    @GetMapping("/analyses/{id}") AnalysisView analysis(@PathVariable UUID id){return AnalysisView.of(analyses.findById(id).orElseThrow(()->ApiException.notFound("Analysis")));}
    @GetMapping("/users") List<AuthController.UserView> users(){return users.findAll().stream().map(AuthController.UserView::of).toList();}
    @GetMapping("/dashboard/summary") Map<String,Object> dashboard(){var actionable=Set.of(IncidentStatus.OPEN,IncidentStatus.ACKNOWLEDGED,IncidentStatus.INVESTIGATING,IncidentStatus.MITIGATED);var all=incidents.findAll();long open=all.stream().filter(incident->actionable.contains(incident.status)).count();long sev1=all.stream().filter(incident->actionable.contains(incident.status)&&incident.severity==Severity.SEV1).count();long sev2=all.stream().filter(incident->actionable.contains(incident.status)&&incident.severity==Severity.SEV2).count();return Map.of("openIncidents",open,"sev1",sev1,"sev2",sev2,"services",services.count(),"rules",rules.count(),"alerts",alerts.count(),"logs",logs.count());}
    record VolumeBucket(String label,Instant start,long total,long errors,long warnings) {}
    record ServiceMetric(UUID serviceId,String name,String environment,long logs,long alerts,long incidents) {}
    @GetMapping("/dashboard/analytics") Map<String,Object> analytics(){
        var allLogs=logs.findAll();var allAlerts=alerts.findAll();var allIncidents=incidents.findAll();var allServices=services.findAll();
        Map<String,Long> logsByLevel=new LinkedHashMap<>();for(LogLevel value:LogLevel.values())logsByLevel.put(value.name(),0L);
        for(LogEvent log:allLogs)logsByLevel.compute(log.level.name(),(key,count)->count+1);
        Map<String,Long> incidentsByStatus=new LinkedHashMap<>();for(IncidentStatus value:IncidentStatus.values())incidentsByStatus.put(value.name(),0L);
        Map<String,Long> incidentsBySeverity=new LinkedHashMap<>();for(Severity value:Severity.values())incidentsBySeverity.put(value.name(),0L);
        for(Incident incident:allIncidents){incidentsByStatus.compute(incident.status.name(),(key,count)->count+1);incidentsBySeverity.compute(incident.severity.name(),(key,count)->count+1);}
        Map<String,Long> servicesByStatus=new LinkedHashMap<>();for(ServiceStatus value:ServiceStatus.values())servicesByStatus.put(value.name(),0L);
        for(MonitoredService service:allServices)servicesByStatus.compute(service.status.name(),(key,count)->count+1);

        Instant start=Instant.now().minus(Duration.ofHours(22)).truncatedTo(java.time.temporal.ChronoUnit.HOURS);var volume=new ArrayList<VolumeBucket>();
        for(int index=0;index<12;index++){Instant bucketStart=start.plus(Duration.ofHours(index*2L));Instant bucketEnd=bucketStart.plus(Duration.ofHours(2));long total=allLogs.stream().filter(log->!log.occurredAt.isBefore(bucketStart)&&log.occurredAt.isBefore(bucketEnd)).count();long errors=allLogs.stream().filter(log->!log.occurredAt.isBefore(bucketStart)&&log.occurredAt.isBefore(bucketEnd)&&(log.level==LogLevel.ERROR||log.level==LogLevel.FATAL)).count();long warnings=allLogs.stream().filter(log->!log.occurredAt.isBefore(bucketStart)&&log.occurredAt.isBefore(bucketEnd)&&log.level==LogLevel.WARN).count();String label=java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC).format(bucketStart);volume.add(new VolumeBucket(label,bucketStart,total,errors,warnings));}

        var serviceMetrics=allServices.stream().map(service->new ServiceMetric(service.id,service.name,service.environment,allLogs.stream().filter(log->log.serviceId.equals(service.id)).count(),allAlerts.stream().filter(alert->alert.serviceId.equals(service.id)).count(),allIncidents.stream().filter(incident->incident.serviceId.equals(service.id)).count())).sorted(Comparator.comparingLong(ServiceMetric::logs).reversed()).limit(8).toList();
        var acknowledged=allIncidents.stream().filter(incident->incident.acknowledgedAt!=null).mapToLong(incident->Duration.between(incident.openedAt,incident.acknowledgedAt).toSeconds()).average();
        var resolved=allIncidents.stream().filter(incident->incident.resolvedAt!=null).mapToLong(incident->Duration.between(incident.openedAt,incident.resolvedAt).toSeconds()).average();
        long actionable=allIncidents.stream().filter(incident->Set.of(IncidentStatus.OPEN,IncidentStatus.ACKNOWLEDGED,IncidentStatus.INVESTIGATING,IncidentStatus.MITIGATED).contains(incident.status)).count();
        var result=new LinkedHashMap<String,Object>();result.put("generatedAt",Instant.now());result.put("logVolume",volume);result.put("logsByLevel",logsByLevel);result.put("incidentsByStatus",incidentsByStatus);result.put("incidentsBySeverity",incidentsBySeverity);result.put("servicesByStatus",servicesByStatus);result.put("topServices",serviceMetrics);result.put("mttaMinutes",acknowledged.isPresent()?Math.round(acknowledged.getAsDouble()/6.0)/10.0:null);result.put("mttrMinutes",resolved.isPresent()?Math.round(resolved.getAsDouble()/6.0)/10.0:null);result.put("actionableIncidents",actionable);return result;
    }
    private Map<String,Object> page(Object content,long total,int pages,int number){var m=new LinkedHashMap<String,Object>();m.put("content",content);m.put("totalElements",total);m.put("totalPages",pages);m.put("page",number);return m;}
}

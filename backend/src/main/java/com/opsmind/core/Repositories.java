package com.opsmind.core;

import com.opsmind.domain.Types.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.*;

interface UserRepository extends JpaRepository<User, UUID> { Optional<User> findByEmailIgnoreCase(String email); }
interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> { Optional<RefreshToken> findByTokenHashAndRevokedFalse(String hash); }
interface MonitoredServiceRepository extends JpaRepository<MonitoredService, UUID> { Optional<MonitoredService> findByApiKeyHash(String hash); }
interface LogEventRepository extends JpaRepository<LogEvent, UUID> {
    Optional<LogEvent> findByExternalEventId(String externalEventId);
    Page<LogEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);
    List<LogEvent> findTop100ByServiceIdOrderByOccurredAtDesc(UUID serviceId);
    long countByServiceIdAndLevelAndOccurredAtAfter(UUID serviceId, LogLevel level, Instant after);
}
interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> { List<AlertRule> findByServiceIdAndEnabledTrue(UUID serviceId); }
interface AlertRepository extends JpaRepository<Alert, UUID> { Page<Alert> findAllByOrderByTriggeredAtDesc(Pageable pageable); List<Alert> findByIncidentIdOrderByTriggeredAtDesc(UUID id); }
interface IncidentRepository extends JpaRepository<Incident, UUID> {
    Page<Incident> findAllByOrderByOpenedAtDesc(Pageable pageable);
    Optional<Incident> findFirstByFingerprintAndStatusNotOrderByOpenedAtDesc(String fingerprint, IncidentStatus status);
    long countByStatusNot(IncidentStatus status);
    long countBySeverityAndStatusNot(Severity severity, IncidentStatus status);
}
interface IncidentNoteRepository extends JpaRepository<IncidentNote, UUID> { List<IncidentNote> findByIncidentIdOrderByCreatedAtAsc(UUID id); }
interface TimelineRepository extends JpaRepository<TimelineEvent, UUID> { List<TimelineEvent> findByIncidentIdOrderByCreatedAtAsc(UUID id); }
interface AiAnalysisRepository extends JpaRepository<AiAnalysis, UUID> { List<AiAnalysis> findByIncidentIdOrderByCreatedAtDesc(UUID id); }

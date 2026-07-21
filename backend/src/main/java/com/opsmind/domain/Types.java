package com.opsmind.domain;

public final class Types {
    private Types() {}
    public enum Role { ADMIN, ENGINEER, VIEWER }
    public enum ServiceStatus { HEALTHY, DEGRADED, DOWN, UNKNOWN }
    public enum LogLevel { TRACE, DEBUG, INFO, WARN, ERROR, FATAL }
    public enum RuleType { KEYWORD, COUNT_THRESHOLD }
    public enum Severity { SEV1, SEV2, SEV3, SEV4 }
    public enum AlertStatus { OPEN, SUPPRESSED, RESOLVED }
    public enum IncidentStatus { OPEN, ACKNOWLEDGED, INVESTIGATING, MITIGATED, RESOLVED, CLOSED }
    public enum AnalysisStatus { QUEUED, RUNNING, COMPLETED, FAILED }
}

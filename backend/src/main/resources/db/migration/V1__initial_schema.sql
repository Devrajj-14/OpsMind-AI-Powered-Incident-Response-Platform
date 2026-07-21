CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    role VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE refresh_token (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE monitored_service (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    environment VARCHAR(50) NOT NULL,
    owner_team VARCHAR(120) NOT NULL,
    api_key_hash VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(name, environment)
);

CREATE TABLE log_event (
    id UUID PRIMARY KEY,
    external_event_id VARCHAR(120) NOT NULL UNIQUE,
    service_id UUID NOT NULL REFERENCES monitored_service(id),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    level VARCHAR(20) NOT NULL,
    message VARCHAR(4000) NOT NULL,
    trace_id VARCHAR(255),
    host VARCHAR(255),
    attributes TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE alert_rule (
    id UUID PRIMARY KEY,
    service_id UUID NOT NULL REFERENCES monitored_service(id),
    name VARCHAR(160) NOT NULL,
    rule_type VARCHAR(40) NOT NULL,
    keyword VARCHAR(255),
    threshold_count INTEGER NOT NULL,
    window_seconds INTEGER NOT NULL,
    severity VARCHAR(20) NOT NULL,
    deduplication_seconds INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE incident (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    assignee_id UUID REFERENCES app_user(id),
    service_id UUID NOT NULL REFERENCES monitored_service(id),
    fingerprint VARCHAR(64) NOT NULL,
    alert_count INTEGER NOT NULL,
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL,
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolution_summary VARCHAR(2000),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE alert (
    id UUID PRIMARY KEY,
    service_id UUID NOT NULL REFERENCES monitored_service(id),
    rule_id UUID NOT NULL REFERENCES alert_rule(id),
    incident_id UUID REFERENCES incident(id),
    fingerprint VARCHAR(64) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    summary VARCHAR(1000) NOT NULL,
    evidence TEXT NOT NULL,
    triggered_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE incident_note (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES incident(id),
    author_id UUID NOT NULL REFERENCES app_user(id),
    body VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE timeline_event (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES incident(id),
    actor_id UUID REFERENCES app_user(id),
    event_type VARCHAR(50) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE ai_analysis (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES incident(id),
    status VARCHAR(30) NOT NULL,
    summary VARCHAR(4000),
    hypotheses TEXT,
    evidence_refs TEXT,
    model VARCHAR(100) NOT NULL,
    error_message VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_log_service_time ON log_event(service_id, occurred_at DESC);
CREATE INDEX idx_log_level_time ON log_event(level, occurred_at DESC);
CREATE INDEX idx_alert_status_time ON alert(status, triggered_at DESC);
CREATE INDEX idx_alert_fingerprint ON alert(fingerprint, triggered_at DESC);
CREATE INDEX idx_incident_status_severity ON incident(status, severity);
CREATE INDEX idx_incident_fingerprint ON incident(fingerprint, opened_at DESC);
CREATE INDEX idx_timeline_incident_time ON timeline_event(incident_id, created_at);

package com.opsmind.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsmind.domain.Types.LogLevel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

record LogCommand(String eventId,UUID serviceId,Instant occurredAt,LogLevel level,String message,String traceId,String host,Map<String,Object> attributes) {}

interface IngestionDispatcher { PlatformService.Ingested dispatch(LogCommand command); }

@Component @ConditionalOnProperty(name="opsmind.messaging-mode",havingValue="local",matchIfMissing=true)
class LocalIngestionDispatcher implements IngestionDispatcher {
    private final PlatformService platform; private final MonitoredServiceRepository services;
    LocalIngestionDispatcher(PlatformService platform,MonitoredServiceRepository services){this.platform=platform;this.services=services;}
    public PlatformService.Ingested dispatch(LogCommand c){return platform.ingest(services.findById(c.serviceId()).orElseThrow(()->ApiException.notFound("Service")),c.eventId(),c.occurredAt(),c.level(),c.message(),c.traceId(),c.host(),c.attributes());}
}

@Component @ConditionalOnProperty(name="opsmind.messaging-mode",havingValue="kafka")
class KafkaIngestionDispatcher implements IngestionDispatcher {
    static final String TOPIC="opsmind.raw-log-events.v1";
    private final KafkaTemplate<String,String> kafka; private final ObjectMapper mapper;
    KafkaIngestionDispatcher(KafkaTemplate<String,String> kafka,ObjectMapper mapper){this.kafka=kafka;this.mapper=mapper;}
    public PlatformService.Ingested dispatch(LogCommand c){try{String eventId=c.eventId()==null||c.eventId().isBlank()?UUID.randomUUID().toString():c.eventId();LogCommand normalized=new LogCommand(eventId,c.serviceId(),c.occurredAt()==null?Instant.now():c.occurredAt(),c.level(),c.message(),c.traceId(),c.host(),c.attributes());kafka.send(TOPIC,c.serviceId().toString(),mapper.writeValueAsString(normalized));return new PlatformService.Ingested(UUID.nameUUIDFromBytes(eventId.getBytes()),eventId);}catch(Exception e){throw new IllegalStateException("Could not publish log event",e);}}
}

@Component @ConditionalOnProperty(name="opsmind.messaging-mode",havingValue="kafka")
class KafkaLogConsumer {
    private final ObjectMapper mapper; private final PlatformService platform; private final MonitoredServiceRepository services;
    KafkaLogConsumer(ObjectMapper mapper,PlatformService platform,MonitoredServiceRepository services){this.mapper=mapper;this.platform=platform;this.services=services;}
    @KafkaListener(topics=KafkaIngestionDispatcher.TOPIC,groupId="opsmind-log-processor")
    void consume(String json) throws Exception {LogCommand c=mapper.readValue(json,LogCommand.class);platform.ingest(services.findById(c.serviceId()).orElseThrow(),c.eventId(),c.occurredAt(),c.level(),c.message(),c.traceId(),c.host(),c.attributes());}
}

interface Deduplicator { boolean acquire(String fingerprint,Duration ttl); }

@Component @ConditionalOnProperty(name="opsmind.deduplication-mode",havingValue="local",matchIfMissing=true)
class LocalDeduplicator implements Deduplicator {
    private final Map<String,Instant> entries=new ConcurrentHashMap<>();
    public boolean acquire(String key,Duration ttl){Instant now=Instant.now();entries.entrySet().removeIf(e->e.getValue().isBefore(now));return entries.putIfAbsent(key,now.plus(ttl))==null;}
}

@Component @ConditionalOnProperty(name="opsmind.deduplication-mode",havingValue="redis")
class RedisDeduplicator implements Deduplicator {
    private final StringRedisTemplate redis; RedisDeduplicator(StringRedisTemplate redis){this.redis=redis;}
    public boolean acquire(String key,Duration ttl){return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("alert:dedup:"+key,"1",ttl.toMillis(),TimeUnit.MILLISECONDS));}
}

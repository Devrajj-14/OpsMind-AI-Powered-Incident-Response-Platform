package com.opsmind.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class OpsMindFlowTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;

    @Test void completeIncidentResponseFlow() throws Exception {
        JsonNode login=body(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
            {"email":"admin@opsmind.local","password":"Admin123!"}
            """),200);
        String token=login.get("accessToken").asText();
        String auth="Bearer "+token;

        JsonNode service=body(post("/api/v1/services").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content("""
            {"name":"orders-api","environment":"test","ownerTeam":"Commerce"}
            """),201);
        String serviceId=service.get("id").asText(); String apiKey=service.get("apiKey").asText();
        assertThat(apiKey).startsWith("opm_");

        body(post("/api/v1/alert-rules").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content("""
            {"serviceId":"%s","name":"Order database timeout","ruleType":"KEYWORD","keyword":"timeout","thresholdCount":1,"windowSeconds":300,"severity":"SEV2","deduplicationSeconds":60}
            """.formatted(serviceId)),201);

        JsonNode firstIngestion=body(post("/api/v1/ingestion/logs").header("X-OpsMind-Key",apiKey).contentType(MediaType.APPLICATION_JSON).content("""
            [{"eventId":"flow-test-1","level":"ERROR","message":"Database connection timeout password=hidden","traceId":"trace-42","host":"orders-1","attributes":{"region":"test"}}]
            """),202);
        JsonNode duplicateIngestion=body(post("/api/v1/ingestion/logs").header("X-OpsMind-Key",apiKey).contentType(MediaType.APPLICATION_JSON).content("""
            [{"eventId":"flow-test-1","level":"ERROR","message":"Database connection timeout password=hidden","traceId":"trace-42","host":"orders-1","attributes":{"region":"test"}}]
            """),202);
        assertThat(duplicateIngestion.get(0).get("id").asText()).isEqualTo(firstIngestion.get(0).get("id").asText());

        JsonNode list=body(get("/api/v1/incidents").header("Authorization",auth),200);
        assertThat(list.get("totalElements").asLong()).isGreaterThanOrEqualTo(1);
        JsonNode incident=list.get("content").get(0); String incidentId=incident.get("id").asText();

        body(patch("/api/v1/incidents/{id}/status",incidentId).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content("""
            {"status":"ACKNOWLEDGED","assigneeId":"%s"}
            """.formatted(login.get("user").get("id").asText())),200);
        body(post("/api/v1/incidents/{id}/notes",incidentId).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Checking database pool metrics\"}"),201);
        JsonNode analysis=body(post("/api/v1/incidents/{id}/analyses",incidentId).header("Authorization",auth),202);
        JsonNode stored=awaitAnalysis(analysis.get("id").asText(),auth);
        assertThat(stored.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(stored.get("summary").asText()).contains("[REDACTED]").doesNotContain("hidden");

        body(patch("/api/v1/incidents/{id}/status",incidentId).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INVESTIGATING\"}"),200);
        body(patch("/api/v1/incidents/{id}/status",incidentId).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\",\"resolutionSummary\":\"Connection pool configuration corrected\"}"),200);
        JsonNode detail=body(get("/api/v1/incidents/{id}",incidentId).header("Authorization",auth),200);
        assertThat(detail.get("incident").get("status").asText()).isEqualTo("RESOLVED");
        assertThat(detail.get("timeline").size()).isGreaterThanOrEqualTo(5);
        JsonNode analytics=body(get("/api/v1/dashboard/analytics").header("Authorization",auth),200);
        assertThat(analytics.get("logVolume").size()).isEqualTo(12);
        assertThat(analytics.get("logsByLevel").get("ERROR").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(analytics.get("topServices").size()).isGreaterThanOrEqualTo(1);
        JsonNode summary=body(get("/api/v1/dashboard/summary").header("Authorization",auth),200);
        assertThat(summary.get("rules").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(summary.get("openIncidents").asLong()).isEqualTo(0);
    }

    @Test void securityValidationAndLifecycleFailuresAreClear() throws Exception {
        mvc.perform(get("/api/v1/incidents")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"admin@opsmind.local\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("Invalid email or password"));
        mvc.perform(post("/api/v1/ingestion/logs").header("X-OpsMind-Key","bad").contentType(MediaType.APPLICATION_JSON).content("[]"))
            .andExpect(status().isBadRequest());
    }

    private JsonNode body(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,int status) throws Exception {
        String value=mvc.perform(request).andExpect(status().is(status)).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andReturn().getResponse().getContentAsString();
        return json.readTree(value);
    }

    private JsonNode awaitAnalysis(String id,String auth) throws Exception {
        JsonNode stored=null;
        for(int attempt=0;attempt<30;attempt++) {
            stored=body(get("/api/v1/analyses/{id}",id).header("Authorization",auth),200);
            if(stored.get("status").asText().matches("COMPLETED|FAILED")) return stored;
            Thread.sleep(100);
        }
        return stored;
    }
}

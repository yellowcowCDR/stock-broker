package com.hermes.broker.common.monitoring.adapter.in.web;

import com.hermes.broker.common.monitoring.CronHeartbeat;
import com.hermes.broker.common.monitoring.CronHeartbeatPhase;
import com.hermes.broker.common.monitoring.CronHeartbeatService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Instant;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CronHeartbeatControllerTest {

    @Test
    void acceptsExactNextCronSlotWithoutCompatibilityInterval() throws Exception {
        CronHeartbeatService service = mock(CronHeartbeatService.class);
        CronHeartbeatController controller = new CronHeartbeatController(service);
        Instant nextSlot = Instant.parse("2026-07-20T00:00:00Z");
        given(service.record(
                "market-analysis", "run-1", CronHeartbeatPhase.STARTED,
                null, nextSlot, "started"))
                .willReturn(new CronHeartbeat(
                        "market-analysis", "run-1", CronHeartbeatPhase.STARTED,
                        3600, Instant.parse("2026-07-19T23:00:00Z"), null,
                        nextSlot, "started", Instant.parse("2026-07-19T23:00:00Z")));
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        mockMvc.perform(post("/api/v1/internal/operations/cron-heartbeats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cronName": "market-analysis",
                                  "executionId": "run-1",
                                  "phase": "STARTED",
                                  "expectedNextAt": "2026-07-20T00:00:00Z",
                                  "message": "started"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedNextAt").value("2026-07-20T00:00:00Z"));

        verify(service).record(
                "market-analysis", "run-1", CronHeartbeatPhase.STARTED,
                null, nextSlot, "started");
    }
}

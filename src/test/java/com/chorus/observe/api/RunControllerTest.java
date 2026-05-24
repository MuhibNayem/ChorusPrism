package com.chorus.observe.api;

import com.chorus.observe.model.Run;
import com.chorus.observe.persistence.InMemoryEvalResultRepository;
import com.chorus.observe.persistence.InMemoryRunRepository;
import com.chorus.observe.persistence.RunRepository;
import com.chorus.observe.security.TenantContext;
import com.chorus.observe.service.RunService;
import com.chorus.observe.service.SpanStreamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RunControllerTest {

    @BeforeEach
    void setUp() {
        TenantContext.set("tenant-1", "user-1", null);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldReturnRun() throws Exception {
        RunRepository repo = new InMemoryRunRepository();
        repo.save(new Run(
            "run-1", "tenant-1", "chorus", "agent-1", "gpt-4o",
            Instant.now(), Instant.now(), Run.Status.SUCCESS,
            Map.of(), Map.of(), 100, BigDecimal.ZERO, 1000
        ));

        RunService service = new RunService(repo, new InMemoryEvalResultRepository());
        SpanStreamService streamService = new SpanStreamService(new ObjectMapper());
        RunController controller = new RunController(service, streamService);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/api/v1/runs/run-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.runId").value("run-1"))
            .andExpect(jsonPath("$.framework").value("chorus"));
    }

    @Test
    void shouldReturnNotFoundForMissingRun() throws Exception {
        RunRepository repo = new InMemoryRunRepository();
        RunService service = new RunService(repo, new InMemoryEvalResultRepository());
        SpanStreamService streamService = new SpanStreamService(new ObjectMapper());
        RunController controller = new RunController(service, streamService);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/api/v1/runs/missing"))
            .andExpect(status().isNotFound());
    }
}

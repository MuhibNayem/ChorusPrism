package com.chorus.observe.api;

import com.chorus.observe.model.Dataset;
import com.chorus.observe.model.DatasetItem;
import com.chorus.observe.persistence.*;
import com.chorus.observe.security.TenantContext;
import com.chorus.observe.service.AgentInvoker;
import com.chorus.observe.service.EvalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EvalControllerTest {

    @Test
    void shouldSubmitEvalRun() throws Exception {
        DatasetRepository dsRepo = new InMemoryDatasetRepository();
        DatasetItemRepository itemRepo = new InMemoryDatasetItemRepository();
        EvalRunRepository runRepo = new InMemoryEvalRunRepository();
        EvalResultRepository resultRepo = new InMemoryEvalResultRepository();

        dsRepo.save(new Dataset("ds-1", "Test", null, Map.of(), "manual", Map.of(), null, null));
        itemRepo.save(new DatasetItem("item-1", "ds-1", "hello", "HELLO", Map.of(), Map.of(), null));

        AgentInvoker fakeInvoker = (config, input) -> input.toUpperCase();
        EvalService service = new EvalService(dsRepo, itemRepo, runRepo, resultRepo, fakeInvoker, new ObjectMapper());
        EvalController controller = new EvalController(service, null);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
            .addFilters((request, response, chain) -> {
                TenantContext.set("default", null, null);
                try {
                    chain.doFilter(request, response);
                } finally {
                    TenantContext.clear();
                }
            })
            .build();

        mvc.perform(post("/api/v1/eval-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"datasetId\":\"ds-1\",\"name\":\"Test Eval\",\"agentConfig\":{},\"scorerConfig\":{\"scorers\":[\"exact_match\"]},\"parallelism\":2}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.evalRunId").exists());
    }

    @Test
    void shouldGetEvalRun() throws Exception {
        EvalRunRepository runRepo = new InMemoryEvalRunRepository();
        DatasetRepository dsRepo = new InMemoryDatasetRepository();
        DatasetItemRepository itemRepo = new InMemoryDatasetItemRepository();
        EvalResultRepository resultRepo = new InMemoryEvalResultRepository();

        AgentInvoker fakeInvoker = (config, input) -> input;
        EvalService service = new EvalService(dsRepo, itemRepo, runRepo, resultRepo, fakeInvoker, new ObjectMapper());
        runRepo.save(new com.chorus.observe.model.EvalRun("eval-1", "ds-1", "Test", Map.of(), Map.of(), 8, com.chorus.observe.model.EvalRun.Status.PENDING, 0, Map.of(), null, null, null));

        EvalController controller = new EvalController(service, null);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
            .addFilters((request, response, chain) -> {
                TenantContext.set("default", null, null);
                try {
                    chain.doFilter(request, response);
                } finally {
                    TenantContext.clear();
                }
            })
            .build();

        mvc.perform(get("/api/v1/eval-runs/eval-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.evalRunId").value("eval-1"));

        mvc.perform(get("/api/v1/eval-runs/missing"))
            .andExpect(status().isNotFound());
    }
}

package com.chorus.observe.api;

import com.chorus.observe.model.Dataset;
import com.chorus.observe.persistence.*;
import com.chorus.observe.security.TenantContext;
import com.chorus.observe.service.DatasetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DatasetControllerTest {

    @Test
    void shouldCreateAndRetrieveDataset() throws Exception {
        DatasetRepository repo = new InMemoryDatasetRepository();
        DatasetItemRepository itemRepo = new InMemoryDatasetItemRepository();
        RunRepository runRepo = new InMemoryRunRepository();
        LlmCallRepository llmRepo = new InMemoryLlmCallRepository();
        DatasetService service = new DatasetService(repo, itemRepo, runRepo, llmRepo, new ObjectMapper());
        DatasetController controller = new DatasetController(service);
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

        mvc.perform(post("/api/v1/datasets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"My Dataset\",\"description\":\"Test\",\"tags\":{},\"source\":\"manual\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.datasetId").exists())
            .andExpect(jsonPath("$.name").value("My Dataset"));

        mvc.perform(get("/api/v1/datasets"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].name").value("My Dataset"));
    }

    @Test
    void shouldAddAndListItems() throws Exception {
        DatasetRepository repo = new InMemoryDatasetRepository();
        DatasetItemRepository itemRepo = new InMemoryDatasetItemRepository();
        RunRepository runRepo = new InMemoryRunRepository();
        LlmCallRepository llmRepo = new InMemoryLlmCallRepository();
        DatasetService service = new DatasetService(repo, itemRepo, runRepo, llmRepo, new ObjectMapper());
        DatasetController controller = new DatasetController(service);
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

        repo.save(new Dataset("ds-1", "Test", null, Map.of(), "manual", Map.of(), null, null));

        mvc.perform(post("/api/v1/datasets/ds-1/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"input\":\"hello\",\"expectedOutput\":\"world\",\"metadata\":{}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.itemId").exists());

        mvc.perform(get("/api/v1/datasets/ds-1/items"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].input").value("hello"));
    }
}

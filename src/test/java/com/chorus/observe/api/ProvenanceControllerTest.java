package com.chorus.observe.api;

import com.chorus.observe.model.ProvenanceEntry;
import com.chorus.observe.persistence.InMemoryProvenanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProvenanceControllerTest {

    @Test
    void shouldReturnProvenanceEntries() throws Exception {
        var repo = new InMemoryProvenanceRepository();
        repo.save(new ProvenanceEntry(
            "e1", "run-1", "agent-1", "llm_plan",
            "state-1", "reasoning-1", "output-1",
            List.of(), Instant.now(), Map.of()
        ));
        repo.save(new ProvenanceEntry(
            "e2", "run-1", "agent-1", "tool_call",
            "state-2", "reasoning-2", "output-2",
            List.of("e1"), Instant.now(), Map.of()
        ));

        var controller = new ProvenanceController(repo);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/api/v1/runs/run-1/provenance"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].entryId").value("e1"))
            .andExpect(jsonPath("$.items[1].parentIds[0]").value("e1"));
    }
}

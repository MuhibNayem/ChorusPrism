package com.chorus.observe.api;

import com.chorus.observe.model.PiiConfig;
import com.chorus.observe.model.PiiRule;
import com.chorus.observe.persistence.PiiConfigRepository;
import com.chorus.observe.security.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/pii")
public class PiiController {

    private static final PiiConfig EMPTY_DEFAULT =
        new PiiConfig(true, List.of());

    private final PiiConfigRepository piiConfigRepository;
    private final ObjectMapper mapper;

    public PiiController(@NonNull PiiConfigRepository piiConfigRepository, @NonNull ObjectMapper mapper) {
        this.piiConfigRepository = piiConfigRepository;
        this.mapper = mapper;
    }

    @GetMapping("/config")
    public ResponseEntity<PiiConfig> getConfig() {
        String tenantId = TenantContext.getTenantId();
        PiiConfig config = piiConfigRepository.findByTenantId(tenantId)
            .orElse(EMPTY_DEFAULT);
        return ResponseEntity.ok(config);
    }

    @PutMapping("/config")
    public ResponseEntity<Void> saveConfig(@RequestBody Map<String, Object> request) {
        String tenantId = TenantContext.getTenantId();
        boolean masterEnabled = Boolean.TRUE.equals(request.getOrDefault("masterEnabled", true));
        List<?> rawRules = request.get("rules") instanceof List<?> list ? list : List.of();
        List<PiiRule> rules = mapper.convertValue(rawRules, new TypeReference<>() {});
        piiConfigRepository.save(tenantId, new PiiConfig(masterEnabled, rules));
        return ResponseEntity.noContent().build();
    }
}

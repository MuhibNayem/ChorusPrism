package com.chorus.observe.api;

import com.chorus.observe.service.ModelService;
import com.chorus.observe.service.ModelService.ModelMetrics;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * REST API v1 for model usage aggregations.
 */
@RestController
@RequestMapping("/api/v1/models")
public class ModelController {

    private final ModelService modelService;

    public ModelController(@NonNull ModelService modelService) {
        this.modelService = Objects.requireNonNull(modelService);
    }

    @GetMapping
    public ResponseEntity<List<ModelMetrics>> getModels() {
        return ResponseEntity.ok(modelService.getModels());
    }
}

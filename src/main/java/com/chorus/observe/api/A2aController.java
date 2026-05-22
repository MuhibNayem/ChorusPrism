package com.chorus.observe.api;

import com.chorus.observe.service.A2aService;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

/**
 * A2A Agent Card endpoint for service discovery.
 */
@RestController
public class A2aController {

    private final A2aService a2aService;

    public A2aController(@NonNull A2aService a2aService) {
        this.a2aService = Objects.requireNonNull(a2aService);
    }

    @GetMapping("/.well-known/agent.json")
    public ResponseEntity<Map<String, Object>> getAgentCard() {
        return ResponseEntity.ok(a2aService.getAgentCard());
    }
}

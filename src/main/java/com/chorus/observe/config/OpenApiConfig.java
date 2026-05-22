package com.chorus.observe.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * OpenAPI / Swagger configuration for Chorus Observe API documentation.
 */
@OpenAPIDefinition(
    info = @Info(title = "Chorus Observe API", version = "1.0.0", description = "Observability, evaluation, and red-teaming API for Chorus Engine"),
    tags = {
        @Tag(name = "Runs", description = "Trace and run management"),
        @Tag(name = "Spans", description = "Span, LLM call, and tool call queries"),
        @Tag(name = "Evaluations", description = "Eval run submission and results"),
        @Tag(name = "Datasets", description = "Dataset and item management"),
        @Tag(name = "Red Team", description = "Adversarial scenario execution"),
        @Tag(name = "Metrics", description = "Metric snapshots and dashboards"),
        @Tag(name = "Feedback", description = "Human feedback collection"),
        @Tag(name = "Provenance", description = "Data lineage tracking"),
        @Tag(name = "Prompts", description = "Prompt versioning and A/B testing"),
        @Tag(name = "Alerts", description = "Alert rules and events"),
        @Tag(name = "Monitoring", description = "Trace clusters and budget enforcement"),
        @Tag(name = "Time Travel", description = "Checkpoints and replay runs"),
        @Tag(name = "SQL Query", description = "Ad-hoc SQL queries"),
        @Tag(name = "A2A", description = "Agent-to-agent task invocation"),
        @Tag(name = "Ingestion", description = "OTLP span ingestion")
    }
)
@SecurityScheme(
    name = "ApiKeyAuth",
    type = SecuritySchemeType.APIKEY,
    in = SecuritySchemeIn.HEADER,
    paramName = "X-API-Key",
    description = "API key authentication via X-API-Key header"
)
public class OpenApiConfig {
}

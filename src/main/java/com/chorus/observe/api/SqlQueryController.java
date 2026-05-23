package com.chorus.observe.api;

import com.chorus.observe.service.SqlQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST API v1 for SQL query execution on trace data.
 */
@PreAuthorize("hasAuthority('admin')")
@RestController
@RequestMapping("/api/v1/query")
public class SqlQueryController {

    private final SqlQueryService sqlQueryService;

    public SqlQueryController(@NonNull SqlQueryService sqlQueryService) {
        this.sqlQueryService = Objects.requireNonNull(sqlQueryService);
    }

    @PostMapping("/sql")
    public ResponseEntity<List<Map<String, Object>>> executeSql(@RequestBody @Valid @NonNull SqlRequest request) {
        List<Map<String, Object>> results = sqlQueryService.executeQuery(request.sql());
        return ResponseEntity.ok(results);
    }

    public record SqlRequest(@NotBlank String sql) {}
}

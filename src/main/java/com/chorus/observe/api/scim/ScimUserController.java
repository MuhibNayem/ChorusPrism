package com.chorus.observe.api.scim;

import com.chorus.observe.dto.scim.ScimErrorResponse;
import com.chorus.observe.dto.scim.ScimListResponse;
import com.chorus.observe.dto.scim.ScimUserDto;
import com.chorus.observe.security.TenantContext;
import com.chorus.observe.service.ScimConflictException;
import com.chorus.observe.service.ScimNotFoundException;
import com.chorus.observe.service.ScimUserService;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/scim/v2/Users")
public class ScimUserController {

    private final ScimUserService service;

    public ScimUserController(@NonNull ScimUserService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody @NonNull ScimUserDto dto) {
        try {
            ScimUserDto created = service.createUser(TenantContext.getTenantId(), dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (ScimConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ScimErrorResponse.of(409, e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable @NonNull String id) {
        var userOpt = service.getUser(TenantContext.getTenantId(), id);
        if (userOpt.isPresent()) {
            return ResponseEntity.ok(userOpt.get());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ScimErrorResponse.of(404, "User not found: " + id));
    }

    @GetMapping
    public ResponseEntity<ScimListResponse> list(
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "1") int startIndex,
            @RequestParam(defaultValue = "100") int count) {
        String tenantId = TenantContext.getTenantId();
        List<ScimUserDto> users = service.listUsers(tenantId, filter, startIndex, count);
        int total = service.countUsers(tenantId, filter);
        return ResponseEntity.ok(new ScimListResponse(
            List.of(), total, startIndex, users.size(), users));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable @NonNull String id,
                                    @RequestBody @NonNull ScimUserDto dto) {
        try {
            return ResponseEntity.ok(service.updateUser(TenantContext.getTenantId(), id, dto));
        } catch (ScimNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ScimErrorResponse.of(404, e.getMessage()));
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> patch(@PathVariable @NonNull String id,
                                   @RequestBody @NonNull ScimUserDto dto) {
        return update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @NonNull String id) {
        try {
            service.deactivateUser(TenantContext.getTenantId(), id);
            return ResponseEntity.noContent().build();
        } catch (ScimNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}

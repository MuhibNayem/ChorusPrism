package com.chorus.observe.api.scim;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/scim/v2/ServiceProviderConfig")
public class ScimServiceProviderConfigController {

    @GetMapping
    public Map<String, Object> getConfig() {
        return Map.of(
            "schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"),
            "patch", Map.of("supported", true),
            "bulk", Map.of("supported", false, "maxOperations", 0, "maxPayloadSize", 0),
            "filter", Map.of("supported", true, "maxResults", 100),
            "changePassword", Map.of("supported", false),
            "sort", Map.of("supported", false),
            "etag", Map.of("supported", false),
            "authenticationSchemes", List.of(
                Map.of(
                    "type", "oauthbearertoken",
                    "name", "OAuth Bearer Token",
                    "description", "Authentication via SCIM bearer token",
                    "specUri", "https://www.rfc-editor.org/rfc/rfc6750",
                    "documentationUri", ""
                )
            ),
            "meta", Map.of(
                "resourceType", "ServiceProviderConfig",
                "location", "/scim/v2/ServiceProviderConfig"
            )
        );
    }
}

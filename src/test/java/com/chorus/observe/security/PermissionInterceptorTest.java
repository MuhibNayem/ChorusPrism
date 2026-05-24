package com.chorus.observe.security;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionInterceptorTest {

    private PermissionInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new PermissionInterceptor();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        TenantContext.clear();
    }

    @Test
    void shouldAllowWhenScopeMatches() throws Exception {
        TenantContext.set("tenant-1", "user-1", null);
        request.setAttribute("scopes", Set.of("runs:read"));

        HandlerMethod handler = handlerMethod("readRuns");
        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldAllowWhenAdminScopePresent() throws Exception {
        TenantContext.set("tenant-1", "user-1", null);
        request.setAttribute("scopes", Set.of("admin"));

        HandlerMethod handler = handlerMethod("readRuns");
        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldDenyWhenScopeMissing() throws Exception {
        TenantContext.set("tenant-1", "user-1", null);
        request.setAttribute("scopes", Set.of("spans:read"));

        HandlerMethod handler = handlerMethod("readRuns");
        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void shouldDenyWhenNotAuthenticated() throws Exception {
        request.setAttribute("scopes", Set.of("runs:read"));

        HandlerMethod handler = handlerMethod("readRuns");
        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void shouldAllowWhenNoAnnotation() throws Exception {
        TenantContext.set("tenant-1", "user-1", null);
        request.setAttribute("scopes", Set.of());

        HandlerMethod handler = handlerMethod("noPermission");
        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldAllowNonHandlerMethod() throws Exception {
        boolean result = interceptor.preHandle(request, response, "not-a-handler");
        assertThat(result).isTrue();
    }

    private HandlerMethod handlerMethod(String methodName) throws NoSuchMethodException {
        return new HandlerMethod(new TestController(), TestController.class.getMethod(methodName));
    }

    @SuppressWarnings("unused")
    static class TestController {
        @RequirePermission("runs:read")
        public String readRuns() {
            return "runs";
        }

        public String noPermission() {
            return "ok";
        }
    }
}

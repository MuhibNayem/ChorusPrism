package com.chorus.observe.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MethodSecurityTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAllowAdminMethodWithAdminAuthority() {
        try (var ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
            var controller = ctx.getBean(TestController.class);
            var auth = new UsernamePasswordAuthenticationToken(
                "user-1", null, List.of(new SimpleGrantedAuthority("admin")));
            SecurityContextHolder.getContext().setAuthentication(auth);

            String result = controller.adminOnly();
            assertThat(result).isEqualTo("admin-ok");
        }
    }

    @Test
    void shouldDenyAdminMethodWithoutAdminAuthority() {
        try (var ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
            var controller = ctx.getBean(TestController.class);
            var auth = new UsernamePasswordAuthenticationToken(
                "user-1", null, List.of(new SimpleGrantedAuthority("viewer")));
            SecurityContextHolder.getContext().setAuthentication(auth);

            assertThatThrownBy(controller::adminOnly)
                .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void shouldDenyAdminMethodWhenAnonymous() {
        try (var ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
            var controller = ctx.getBean(TestController.class);
            SecurityContextHolder.clearContext();

            assertThatThrownBy(controller::adminOnly)
                .isInstanceOfAny(AccessDeniedException.class, AuthenticationCredentialsNotFoundException.class);
        }
    }

    @Test
    void shouldAllowUserMethodWithViewerAuthority() {
        try (var ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
            var controller = ctx.getBean(TestController.class);
            var auth = new UsernamePasswordAuthenticationToken(
                "user-1", null, List.of(new SimpleGrantedAuthority("viewer")));
            SecurityContextHolder.getContext().setAuthentication(auth);

            String result = controller.userEndpoint();
            assertThat(result).isEqualTo("user-ok");
        }
    }

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {
        @Bean
        TestController testController() {
            return new TestController();
        }
    }

    @SuppressWarnings("unused")
    static class TestController {
        @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('admin')")
        public String adminOnly() {
            return "admin-ok";
        }

        @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('viewer')")
        public String userEndpoint() {
            return "user-ok";
        }
    }
}

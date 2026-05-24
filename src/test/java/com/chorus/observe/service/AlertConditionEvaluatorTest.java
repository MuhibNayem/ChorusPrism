package com.chorus.observe.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.*;

class AlertConditionEvaluatorTest {

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private AlertConditionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:alert_eval_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        ds.setUsername("sa");
        ds.setPassword("");
        this.dataSource = ds;
        this.jdbc = new JdbcTemplate(ds);

        jdbc.execute("DROP TABLE IF EXISTS spans");
        jdbc.execute("""
            CREATE TABLE spans (
                span_id VARCHAR(64) PRIMARY KEY,
                run_id VARCHAR(64) NOT NULL,
                status VARCHAR(16) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);

        jdbc.execute("DROP TABLE IF EXISTS eval_runs");
        jdbc.execute("""
            CREATE TABLE eval_runs (
                eval_run_id VARCHAR(64) PRIMARY KEY,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);

        // Seed data with explicit timestamps
        jdbc.update("INSERT INTO spans (span_id, run_id, status, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)", "s1", "r1", "OK");
        jdbc.update("INSERT INTO spans (span_id, run_id, status, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)", "s2", "r1", "ERROR");
        jdbc.update("INSERT INTO spans (span_id, run_id, status, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)", "s3", "r1", "ERROR");
    }

    @Test
    void shouldEvaluateSqlCondition() {
        evaluator = new AlertConditionEvaluator(dataSource);
        Double result = evaluator.evaluate("sql:SELECT COUNT(*) FROM spans WHERE status = 'ERROR'");
        assertThat(result).isEqualTo(2.0);
    }

    @Test
    void shouldRejectSqlWithForbiddenKeyword() {
        evaluator = new AlertConditionEvaluator(dataSource);
        Double result = evaluator.evaluate("sql:SELECT COUNT(*) FROM spans; DELETE FROM spans");
        assertThat(result).isNull();
    }

    @Test
    void shouldRejectSqlWithSemicolon() {
        evaluator = new AlertConditionEvaluator(dataSource);
        Double result = evaluator.evaluate("sql:SELECT 1; DROP TABLE spans");
        assertThat(result).isNull();
    }

    @Test
    void shouldRejectNonSelectSql() {
        evaluator = new AlertConditionEvaluator(dataSource);
        Double result = evaluator.evaluate("sql:INSERT INTO spans VALUES ('x', 'y', 'OK')");
        assertThat(result).isNull();
    }

    @Test
    void shouldValidateReadOnlyRoleName() {
        // Invalid role names with injection payloads should be rejected before any DB call
        evaluator = new AlertConditionEvaluator(dataSource, "readonly; DROP TABLE spans--");
        assertThatThrownBy(() ->
            evaluator.evaluate("sql:SELECT COUNT(*) FROM spans")
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("Invalid read-only role name");
    }

    @Test
    void shouldRejectReadOnlyRoleWithSpecialChars() {
        evaluator = new AlertConditionEvaluator(dataSource, "role\" injection");
        assertThatThrownBy(() ->
            evaluator.evaluate("sql:SELECT COUNT(*) FROM spans")
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("Invalid read-only role name");
    }

    @Test
    void shouldAcceptValidReadOnlyRoleName() {
        // Valid role name passes regex validation (may still fail at DB level in H2,
        // but the security fix ensures no SQL injection occurs)
        evaluator = new AlertConditionEvaluator(dataSource, "readonly");
        // H2 does not support SET ROLE like PostgreSQL, so this may throw IllegalStateException
        // from the catch block — we verify it does NOT throw IllegalStateException about invalid role name
        try {
            evaluator.evaluate("sql:SELECT COUNT(*) FROM spans");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).doesNotContain("Invalid read-only role name");
        }
    }

    @Test
    void shouldEvaluateMetricConditionForIngestionErrors() {
        evaluator = new AlertConditionEvaluator(dataSource);
        Double result = evaluator.evaluate("metric:ingestion.errors.total");
        assertThat(result).isEqualTo(2.0);
    }

    @Test
    void shouldEvaluateMetricConditionForLlmCalls() {
        jdbc.execute("DROP TABLE IF EXISTS llm_calls");
        jdbc.execute("CREATE TABLE llm_calls (call_id VARCHAR(64) PRIMARY KEY, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO llm_calls (call_id) VALUES (?)", "c1");
        jdbc.update("INSERT INTO llm_calls (call_id) VALUES (?)", "c2");

        evaluator = new AlertConditionEvaluator(dataSource);
        Double result = evaluator.evaluate("metric:llm.calls.total");
        assertThat(result).isEqualTo(2.0);
    }

    @Test
    void shouldReturnNullForUnknownMetric() {
        evaluator = new AlertConditionEvaluator(dataSource);
        Double result = evaluator.evaluate("metric:unknown.metric");
        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullForUnknownConditionFormat() {
        evaluator = new AlertConditionEvaluator(dataSource);
        Double result = evaluator.evaluate("invalid:something");
        assertThat(result).isNull();
    }
}

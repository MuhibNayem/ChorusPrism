package com.chorus.observe.config;

import com.chorus.observe.api.*;
import com.chorus.observe.audit.*;
import com.chorus.observe.config.ChorusObserveHealthIndicator;
import com.chorus.observe.dashboard.CustomDashboardService;
import com.chorus.observe.embedding.EmbeddingInvoker;
import com.chorus.observe.embedding.HttpEmbeddingInvoker;
import com.chorus.observe.export.ExportService;
import com.chorus.observe.notification.*;
import com.chorus.observe.persistence.*;
import com.chorus.observe.retention.*;
import com.chorus.observe.sampling.*;
import com.chorus.observe.security.*;
import com.chorus.observe.service.*;
import com.chorus.observe.store.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.micrometer.core.aop.CountedAspect;
import jakarta.servlet.MultipartConfigElement;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.sql.DataSource;
import com.chorus.observe.budget.BudgetAwareAgentInvoker;
import com.chorus.observe.budget.PricingTable;
import com.chorus.observe.lock.DistributedLockRegistry;
import com.chorus.observe.lock.DistributedLockReaper;
import com.chorus.observe.prompt.PromptAbTestExecutor;
import com.chorus.observe.clustering.TraceClusteringEngine;
import com.chorus.observe.service.MultiTurnTestService;
import java.time.Duration;
import java.util.List;

/**
 * Spring Boot auto-configuration for Chorus Observe Server.
 */
@Configuration
@EnableConfigurationProperties(ChorusObserveProperties.class)
@EnableScheduling
@ConditionalOnProperty(prefix = "chorus.observe", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ChorusObserveAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(ChorusObserveAutoConfiguration.class);

    /**
     * Provides a DataSource for Chorus Observe persistence.
     * <p>
     * Priority:
     * <ol>
     *   <li>If {@code chorus.observe.database.url} is set, creates a dedicated HikariCP pool.</li>
     *   <li>Otherwise, uses the application's primary {@code DataSource} bean.</li>
     * </ol>
     *
     * @throws IllegalStateException if no explicit URL is configured and no primary DataSource exists
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "chorusObserveDataSource")
    public DataSource chorusObserveDataSource(@NonNull ChorusObserveProperties properties, org.springframework.beans.factory.ObjectProvider<DataSource> primaryDataSource) {
        ChorusObserveProperties.Database db = properties.getDatabase();

        if (db.getUrl() != null && !db.getUrl().isBlank()) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(db.getUrl());
            config.setUsername(db.getUsername());
            config.setPassword(db.getPassword());
            config.setMaximumPoolSize(db.getMaxPoolSize());
            config.setPoolName("chorus-observe-pool");
            config.setAutoCommit(true);
            LOG.info("Chorus Observe using dedicated DataSource: {}", db.getUrl());
            return new HikariDataSource(config);
        }

        DataSource existing = primaryDataSource.getIfAvailable();
        if (existing != null) {
            LOG.info("Chorus Observe reusing application primary DataSource");
            return existing;
        }

        throw new IllegalStateException(
            "Chorus Observe requires a DataSource. Either:\n" +
            "  1. Set chorus.observe.database.url (and username/password), or\n" +
            "  2. Provide a primary DataSource bean in your application context.\n" +
            "See: https://github.com/MuhibNayem/chorus-engine4j/blob/main/chorus-observe-server/README.md"
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper chorusObserveObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }

    @Bean
    @ConditionalOnMissingBean
    public RunRepository runRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new RunRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public SpanRepository spanRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new SpanRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public LlmCallRepository llmCallRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new LlmCallRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolCallRepository toolCallRepository(@NonNull DataSource dataSource) {
        return new ToolCallRepository(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public FeedbackRepository feedbackRepository(@NonNull DataSource dataSource) {
        return new FeedbackRepository(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public MetricRepository metricRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new MetricRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProvenanceRepository provenanceRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new ProvenanceRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RagQueryRepository ragQueryRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new RagQueryRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean(name = "chorusObserveClickHouseDataSource")
    @ConditionalOnProperty(prefix = "chorus.observe.clickhouse", name = "url")
    public DataSource chorusObserveClickHouseDataSource(@NonNull ChorusObserveProperties properties) {
        ChorusObserveProperties.ClickHouse ch = properties.getClickhouse();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(ch.getUrl());
        config.setUsername(ch.getUsername());
        config.setPassword(ch.getPassword());
        config.setMaximumPoolSize(ch.getMaxPoolSize());
        config.setPoolName("chorus-observe-ch-pool");
        config.setAutoCommit(true);
        LOG.info("Chorus Observe ClickHouse DataSource: {}", ch.getUrl());
        return new HikariDataSource(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public SpanStore spanStore(
            @NonNull SpanRepository spanRepository,
            @NonNull LlmCallRepository llmCallRepository,
            @NonNull ToolCallRepository toolCallRepository,
            @NonNull DataSource chorusObserveDataSource,
            org.springframework.beans.factory.ObjectProvider<DataSource> clickHouseDataSourceProvider,
            @NonNull ChorusObserveProperties properties,
            @NonNull ObjectMapper mapper) {
        String storeType = properties.getStorage().getSpanStore();
        SpanStore postgresStore = new PostgresSpanStore(spanRepository, llmCallRepository, toolCallRepository, chorusObserveDataSource);

        DataSource chDataSource = clickHouseDataSourceProvider.getIfAvailable();

        if ("clickhouse".equalsIgnoreCase(storeType)) {
            if (chDataSource == null) {
                throw new IllegalStateException(
                    "Chorus Observe span store is set to 'clickhouse' but no ClickHouse DataSource is configured. " +
                    "Set chorus.observe.clickhouse.url (and username/password)."
                );
            }
            LOG.info("Chorus Observe using ClickHouse span store");
            return new ClickHouseSpanStore(chDataSource, mapper);
        } else if ("dual".equalsIgnoreCase(storeType)) {
            if (chDataSource == null) {
                throw new IllegalStateException(
                    "Chorus Observe span store is set to 'dual' but no ClickHouse DataSource is configured. " +
                    "Set chorus.observe.clickhouse.url (and username/password)."
                );
            }
            LOG.info("Chorus Observe using dual-write span store (PostgreSQL + ClickHouse)");
            SpanStore clickHouseStore = new ClickHouseSpanStore(chDataSource, mapper);
            return new DualWriteSpanStore(postgresStore, clickHouseStore);
        }

        LOG.info("Chorus Observe using PostgreSQL span store");
        return postgresStore;
    }

    @Bean
    @ConditionalOnMissingBean
    public SpanStreamService spanStreamService(@NonNull ObjectMapper mapper) {
        return new SpanStreamService(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public OtlpIngestionService otlpIngestionService(
            @NonNull RunRepository runRepository,
            @NonNull SpanStore spanStore,
            @NonNull ObjectMapper mapper,
            ObjectProvider<SpanStreamService> streamServiceProvider,
            ObjectProvider<MetricsService> metricsServiceProvider) {
        SpanStreamService streamService = streamServiceProvider.getIfAvailable();
        MetricsService metricsService = metricsServiceProvider.getIfAvailable();
        return new OtlpIngestionService(runRepository, spanStore, mapper, streamService, metricsService);
    }

    @Bean
    @ConditionalOnMissingBean
    public RunService runService(@NonNull RunRepository runRepository, @NonNull EvalResultRepository evalResultRepository) {
        return new RunService(runRepository, evalResultRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public SpanService spanService(
            @NonNull SpanRepository spanRepository,
            @NonNull LlmCallRepository llmCallRepository,
            @NonNull ToolCallRepository toolCallRepository) {
        return new SpanService(spanRepository, llmCallRepository, toolCallRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public MetricService metricService(@NonNull MetricRepository metricRepository) {
        return new MetricService(metricRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public DashboardService dashboardService(@NonNull DataSource chorusObserveDataSource) {
        return new DashboardService(chorusObserveDataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public FeedbackService feedbackService(@NonNull FeedbackRepository feedbackRepository) {
        return new FeedbackService(feedbackRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public RunController runController(@NonNull RunService runService, @NonNull SpanStreamService spanStreamService) {
        return new RunController(runService, spanStreamService);
    }

    @Bean
    @ConditionalOnMissingBean
    public SpanController spanController(@NonNull SpanService spanService) {
        return new SpanController(spanService);
    }

    @Bean
    @ConditionalOnMissingBean
    public MetricController metricController(@NonNull MetricService metricService, @NonNull DashboardService dashboardService) {
        return new MetricController(metricService, dashboardService);
    }

    @Bean
    @ConditionalOnMissingBean
    public FeedbackController feedbackController(@NonNull FeedbackService feedbackService) {
        return new FeedbackController(feedbackService);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProvenanceController provenanceController(@NonNull ProvenanceRepository provenanceRepository) {
        return new ProvenanceController(provenanceRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public OtlpHttpController otlpHttpController(@NonNull OtlpIngestionService ingestionService) {
        return new OtlpHttpController(ingestionService);
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean(name = "chorusObserveHealthIndicator")
    public ChorusObserveHealthIndicator chorusObserveHealthIndicator(DataSource chorusObserveDataSource) {
        return new ChorusObserveHealthIndicator(chorusObserveDataSource);
    }

    // ============================================================
    // Phase 3-9: Repositories
    // ============================================================

    @Bean
    @ConditionalOnMissingBean
    public DatasetRepository datasetRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new DatasetRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public DatasetItemRepository datasetItemRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new DatasetItemRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public EvalRunRepository evalRunRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new EvalRunRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public EvalResultRepository evalResultRepository(@NonNull DataSource dataSource) {
        return new EvalResultRepository(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public CheckpointRepository checkpointRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new CheckpointRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ReplayRunRepository replayRunRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new ReplayRunRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public BreakpointRepository breakpointRepository(@NonNull DataSource dataSource) {
        return new BreakpointRepository(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedTeamScenarioRepository redTeamScenarioRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new RedTeamScenarioRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedTeamRunRepository redTeamRunRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new RedTeamRunRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedTeamResultRepository redTeamResultRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new RedTeamResultRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public GuardrailTelemetryRepository guardrailTelemetryRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new GuardrailTelemetryRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertRuleRepository alertRuleRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new AlertRuleRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertEventRepository alertEventRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new AlertEventRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public TraceClusterRepository traceClusterRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new TraceClusterRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public BudgetEnforcementRepository budgetEnforcementRepository(@NonNull DataSource dataSource) {
        return new BudgetEnforcementRepository(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public PromptVersionRepository promptVersionRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new PromptVersionRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public PromptTagRepository promptTagRepository(@NonNull DataSource dataSource) {
        return new PromptTagRepository(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public PromptAbTestRepository promptAbTestRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new PromptAbTestRepository(dataSource, mapper);
    }

    // ============================================================
    // Phase 3-9: Services
    // ============================================================

    @Bean
    @ConditionalOnMissingBean
    public PricingTable pricingTable() {
        return new PricingTable();
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentInvoker agentInvoker(
            @NonNull ObjectMapper mapper,
            @NonNull BudgetService budgetService,
            @NonNull PricingTable pricingTable) {
        CircuitBreaker circuitBreaker = new CircuitBreaker(5, Duration.ofSeconds(30));
        AgentInvoker base = new CircuitBreakerAgentInvoker(new HttpAgentInvoker(mapper), circuitBreaker);
        return new BudgetAwareAgentInvoker(base, budgetService, pricingTable, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public DatasetService datasetService(
            @NonNull DatasetRepository datasetRepository,
            @NonNull DatasetItemRepository datasetItemRepository,
            @NonNull RunRepository runRepository,
            @NonNull LlmCallRepository llmCallRepository,
            @NonNull ObjectMapper mapper) {
        return new DatasetService(datasetRepository, datasetItemRepository, runRepository, llmCallRepository, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public EvalService evalService(
            @NonNull DatasetRepository datasetRepository,
            @NonNull DatasetItemRepository datasetItemRepository,
            @NonNull EvalRunRepository evalRunRepository,
            @NonNull EvalResultRepository evalResultRepository,
            @NonNull AgentInvoker agentInvoker,
            @NonNull ObjectMapper mapper,
            ObjectProvider<MetricsService> metricsServiceProvider) {
        MetricsService metricsService = metricsServiceProvider.getIfAvailable();
        return new EvalService(datasetRepository, datasetItemRepository, evalRunRepository, evalResultRepository, agentInvoker, mapper, metricsService);
    }

    @Bean
    @ConditionalOnMissingBean
    public TimeTravelService timeTravelService(
            @NonNull CheckpointRepository checkpointRepository,
            @NonNull ReplayRunRepository replayRunRepository,
            @NonNull BreakpointRepository breakpointRepository) {
        return new TimeTravelService(checkpointRepository, replayRunRepository, breakpointRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedTeamService redTeamService(
            @NonNull RedTeamScenarioRepository redTeamScenarioRepository,
            @NonNull RedTeamRunRepository redTeamRunRepository,
            @NonNull RedTeamResultRepository redTeamResultRepository,
            @NonNull AgentInvoker agentInvoker,
            @NonNull ObjectMapper mapper,
            ObjectProvider<MetricsService> metricsServiceProvider,
            ObjectProvider<com.chorus.engine.guardrails.TieredGuardrailEngine> guardrailEngineProvider) {
        MetricsService metricsService = metricsServiceProvider.getIfAvailable();
        com.chorus.engine.guardrails.TieredGuardrailEngine engine = guardrailEngineProvider.getIfAvailable();
        return new RedTeamService(redTeamScenarioRepository, redTeamRunRepository, redTeamResultRepository, agentInvoker, mapper, metricsService, engine);
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertService alertService(
            @NonNull AlertRuleRepository alertRuleRepository,
            @NonNull AlertEventRepository alertEventRepository,
            ObjectProvider<com.chorus.observe.notification.NotificationService> notificationServiceProvider) {
        com.chorus.observe.notification.NotificationService notificationService = notificationServiceProvider.getIfAvailable();
        return new AlertService(alertRuleRepository, alertEventRepository, notificationService);
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertConditionEvaluator alertConditionEvaluator(@NonNull DataSource chorusObserveDataSource, @NonNull ChorusObserveProperties properties) {
        return new AlertConditionEvaluator(chorusObserveDataSource, properties.getDatabase().getReadOnlyRole());
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertScheduler alertScheduler(
            @NonNull AlertRuleRepository alertRuleRepository,
            @NonNull AlertEventRepository alertEventRepository,
            @NonNull AlertConditionEvaluator alertConditionEvaluator,
            @NonNull AlertService alertService) {
        return new AlertScheduler(alertRuleRepository, alertEventRepository, alertConditionEvaluator, alertService);
    }

    @Bean
    @ConditionalOnMissingBean
    public TraceEmbeddingRepository traceEmbeddingRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new TraceEmbeddingRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public TraceClusteringEngine traceClusteringEngine(
            @NonNull TraceEmbeddingRepository traceEmbeddingRepository,
            @NonNull TraceClusterRepository traceClusterRepository,
            @NonNull LlmCallRepository llmCallRepository,
            @NonNull RunRepository runRepository,
            @NonNull EmbeddingInvoker embeddingInvoker,
            @NonNull ObjectMapper mapper) {
        return new TraceClusteringEngine(traceEmbeddingRepository, traceClusterRepository, llmCallRepository, runRepository, embeddingInvoker, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public TraceClusterService traceClusterService(@NonNull TraceClusterRepository traceClusterRepository, @NonNull TraceClusteringEngine traceClusteringEngine) {
        return new TraceClusterService(traceClusterRepository, traceClusteringEngine);
    }

    @Bean
    @ConditionalOnMissingBean
    public BudgetService budgetService(@NonNull BudgetEnforcementRepository budgetEnforcementRepository) {
        return new BudgetService(budgetEnforcementRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public PromptService promptService(
            @NonNull PromptVersionRepository promptVersionRepository,
            @NonNull PromptTagRepository promptTagRepository,
            @NonNull PromptAbTestRepository promptAbTestRepository) {
        return new PromptService(promptVersionRepository, promptTagRepository, promptAbTestRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public SqlQueryService sqlQueryService(@NonNull DataSource chorusObserveDataSource, @NonNull ChorusObserveProperties properties) {
        return new SqlQueryService(chorusObserveDataSource, properties.getDatabase().getReadOnlyRole());
    }

    @Bean
    @ConditionalOnMissingBean
    public DistributedLockRegistry distributedLockRegistry(@NonNull DataSource chorusObserveDataSource, @NonNull ChorusObserveProperties properties) {
        return new DistributedLockRegistry(
            chorusObserveDataSource,
            Duration.ofSeconds(properties.getLock().getDefaultTtlSeconds()),
            Duration.ofMillis(properties.getLock().getPollIntervalMillis()));
    }

    @Bean
    @ConditionalOnMissingBean
    public DistributedLockReaper distributedLockReaper(@NonNull DataSource chorusObserveDataSource) {
        return new DistributedLockReaper(chorusObserveDataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public MultiTurnScenarioRepository multiTurnScenarioRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new MultiTurnScenarioRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public MultiTurnRunRepository multiTurnRunRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new MultiTurnRunRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public MultiTurnTurnRepository multiTurnTurnRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new MultiTurnTurnRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public MultiTurnTestService multiTurnTestService(
            @NonNull MultiTurnScenarioRepository multiTurnScenarioRepository,
            @NonNull MultiTurnRunRepository multiTurnRunRepository,
            @NonNull MultiTurnTurnRepository multiTurnTurnRepository,
            @NonNull AgentInvoker agentInvoker,
            @NonNull ObjectMapper mapper) {
        return new MultiTurnTestService(multiTurnScenarioRepository, multiTurnRunRepository, multiTurnTurnRepository, agentInvoker, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public MultiTurnController multiTurnController(@NonNull MultiTurnTestService multiTurnTestService) {
        return new MultiTurnController(multiTurnTestService);
    }

    @Bean
    @ConditionalOnMissingBean
    public A2aService a2aService(@NonNull RunRepository runRepository) {
        return new A2aService(runRepository);
    }

    // ============================================================
    // Phase 3-9: Controllers
    // ============================================================

    @Bean
    @ConditionalOnMissingBean
    public DatasetController datasetController(@NonNull DatasetService datasetService) {
        return new DatasetController(datasetService);
    }

    @Bean
    @ConditionalOnMissingBean
    public EvalController evalController(@NonNull EvalService evalService, @NonNull IdempotencyService idempotencyService) {
        return new EvalController(evalService, idempotencyService);
    }

    @Bean
    @ConditionalOnMissingBean
    public TimeTravelController timeTravelController(@NonNull TimeTravelService timeTravelService) {
        return new TimeTravelController(timeTravelService);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedTeamController redTeamController(@NonNull RedTeamService redTeamService, @NonNull IdempotencyService idempotencyService) {
        return new RedTeamController(redTeamService, idempotencyService);
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertController alertController(@NonNull AlertService alertService) {
        return new AlertController(alertService);
    }

    @Bean
    @ConditionalOnMissingBean
    public MonitoringController monitoringController(@NonNull TraceClusterService traceClusterService, @NonNull BudgetService budgetService) {
        return new MonitoringController(traceClusterService, budgetService);
    }

    @Bean
    @ConditionalOnMissingBean
    public PromptAbTestExecutor promptAbTestExecutor(
            @NonNull PromptVersionRepository promptVersionRepository,
            @NonNull PromptAbTestRepository promptAbTestRepository,
            @NonNull DatasetRepository datasetRepository,
            @NonNull DatasetItemRepository datasetItemRepository,
            @NonNull EvalResultRepository evalResultRepository,
            @NonNull AgentInvoker agentInvoker,
            @NonNull ObjectMapper mapper,
            org.springframework.beans.factory.ObjectProvider<DistributedLockRegistry> lockRegistryProvider) {
        DistributedLockRegistry lockRegistry = lockRegistryProvider.getIfAvailable();
        return new PromptAbTestExecutor(promptVersionRepository, promptAbTestRepository, datasetRepository,
            datasetItemRepository, evalResultRepository, agentInvoker, mapper, lockRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public PromptController promptController(@NonNull PromptService promptService, @NonNull PromptAbTestExecutor promptAbTestExecutor) {
        return new PromptController(promptService, promptAbTestExecutor);
    }

    @Bean
    @ConditionalOnMissingBean
    public SqlQueryController sqlQueryController(@NonNull SqlQueryService sqlQueryService) {
        return new SqlQueryController(sqlQueryService);
    }

    @Bean
    @ConditionalOnMissingBean
    public A2aController a2aController(@NonNull A2aService a2aService) {
        return new A2aController(a2aService);
    }

    // ============================================================
    // Security, Rate Limiting, Logging
    // ============================================================

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyService idempotencyService() {
        return new IdempotencyService();
    }

    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilter(@NonNull ApiKeyRepository apiKeyRepository, @NonNull ChorusObserveProperties properties) {
        FilterRegistrationBean<ApiKeyAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ApiKeyAuthFilter(apiKeyRepository, properties.getSecurity().isApiKeyEnabled()));
        registration.addUrlPatterns("/api/*", "/v1/*");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(@NonNull ChorusObserveProperties properties) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RateLimitFilter(properties.getRateLimit().getMaxRequestsPerMinute(), properties.getRateLimit().isEnabled()));
        registration.addUrlPatterns("/api/*", "/v1/*");
        registration.setOrder(2);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenApiConfig openApiConfig() {
        return new OpenApiConfig();
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiVersionInterceptor apiVersionInterceptor() {
        return new ApiVersionInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestLoggingInterceptor requestLoggingInterceptor() {
        return new RequestLoggingInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean
    public MetricsService metricsService(@NonNull MeterRegistry meterRegistry) {
        return new MetricsService(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public TimedAspect timedAspect(@NonNull MeterRegistry meterRegistry) {
        return new TimedAspect(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public CountedAspect countedAspect(@NonNull MeterRegistry meterRegistry) {
        return new CountedAspect(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public WebMvcConfigurer chorusObserveWebMvcConfigurer(@NonNull RequestLoggingInterceptor interceptor, @NonNull ApiVersionInterceptor apiVersionInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(@NonNull InterceptorRegistry registry) {
                registry.addInterceptor(apiVersionInterceptor).addPathPatterns("/api/**", "/v1/**");
                registry.addInterceptor(interceptor).addPathPatterns("/api/**", "/v1/**");
            }
        };
    }

    // ============================================================
    // gRPC Server
    // ============================================================

    @Bean
    @ConditionalOnProperty(prefix = "chorus.observe.grpc", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Server grpcServer(@NonNull OtlpIngestionService ingestionService, @NonNull ChorusObserveProperties properties) {
        int port = properties.getGrpc().getPort();
        Server server = ServerBuilder.forPort(port)
            .addService(new OtlpGrpcService(ingestionService))
            .build();
        try {
            server.start();
            LOG.info("Chorus Observe OTLP gRPC server started on port {}", port);
        } catch (Exception e) {
            LOG.error("Failed to start gRPC server on port {}", port, e);
        }
        return server;
    }

    @Bean
    @ConditionalOnMissingBean
    public MultipartConfigElement multipartConfigElement(@NonNull ChorusObserveProperties properties) {
        long maxFileSize = properties.getServer().getMaxFileSizeMb() * 1024L * 1024L;
        long maxRequestSize = properties.getServer().getMaxRequestSizeMb() * 1024L * 1024L;
        return new MultipartConfigElement("", maxFileSize, maxRequestSize, 0);
    }

    @Bean
    @ConditionalOnProperty(prefix = "chorus.observe.database", name = "migrate-on-startup", havingValue = "true", matchIfMissing = true)
    public Flyway chorusObserveFlyway(@NonNull DataSource chorusObserveDataSource, @NonNull ChorusObserveProperties properties) {
        if (properties.getDatabase().getUrl() == null || properties.getDatabase().getUrl().isBlank()) {
            LOG.warn("Chorus Observe Flyway migrations skipped: no explicit database URL configured. " +
                     "If you are sharing the application DataSource, manage migrations in your application.");
            return Flyway.configure().dataSource(chorusObserveDataSource).load();
        }
        Flyway flyway = Flyway.configure()
            .dataSource(chorusObserveDataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load();
        flyway.migrate();
        LOG.info("Chorus Observe database migrations applied");
        return flyway;
    }

    // ============================================================
    // RBAC & Security
    // ============================================================

    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtTokenService jwtTokenService(@NonNull ChorusObserveProperties properties) {
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.isBlank()) {
            secret = java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID().toString();
            LOG.warn("No JWT secret configured. Using auto-generated secret (will break sessions on restart). Set chorus.observe.jwt.secret.");
        }
        return new JwtTokenService(secret, Duration.ofMinutes(properties.getJwt().getExpiryMinutes()));
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantRepository tenantRepository(@NonNull DataSource dataSource) {
        return new TenantRepository(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public UserRepository userRepository(@NonNull DataSource dataSource) {
        return new UserRepository(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public RoleRepository roleRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new RoleRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public UserRoleRepository userRoleRepository(@NonNull DataSource dataSource) {
        return new UserRoleRepository(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiKeyRepository apiKeyRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new ApiKeyRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantService tenantService(@NonNull TenantRepository tenantRepository) {
        return new TenantService(tenantRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public UserService userService(@NonNull UserRepository userRepository, @NonNull RoleRepository roleRepository,
                                   @NonNull UserRoleRepository userRoleRepository, @NonNull PasswordEncoder passwordEncoder) {
        return new UserService(userRepository, roleRepository, userRoleRepository, passwordEncoder);
    }

    @Bean
    @ConditionalOnMissingBean
    public RoleService roleService(@NonNull RoleRepository roleRepository) {
        return new RoleService(roleRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthenticationService authenticationService(@NonNull UserService userService, @NonNull ApiKeyRepository apiKeyRepository,
                                                       @NonNull JwtTokenService jwtTokenService, @NonNull PasswordEncoder passwordEncoder) {
        return new AuthenticationService(userService, apiKeyRepository, jwtTokenService, passwordEncoder);
    }

    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilter(@NonNull JwtTokenService jwtTokenService, @NonNull ChorusObserveProperties properties) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new JwtAuthFilter(jwtTokenService, true));
        registration.addUrlPatterns("/api/*", "/v1/*");
        registration.setOrder(3);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<RbacAuthorizationFilter> rbacAuthorizationFilter() {
        FilterRegistrationBean<RbacAuthorizationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RbacAuthorizationFilter(true));
        registration.addUrlPatterns("/api/*", "/v1/*");
        registration.setOrder(4);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthController authController(@NonNull AuthenticationService authenticationService, @NonNull UserService userService) {
        return new AuthController(authenticationService, userService);
    }

    @Bean
    @ConditionalOnMissingBean
    public UserController userController(@NonNull UserService userService) {
        return new UserController(userService);
    }

    @Bean
    @ConditionalOnMissingBean
    public RoleController roleController(@NonNull RoleService roleService) {
        return new RoleController(roleService);
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantController tenantController(@NonNull TenantService tenantService) {
        return new TenantController(tenantService);
    }

    // ============================================================
    // Audit Logging
    // ============================================================

    @Bean
    @ConditionalOnMissingBean
    public AuditLogRepository auditLogRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new AuditLogRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditLogService auditLogService(@NonNull AuditLogRepository auditLogRepository) {
        return new AuditLogService(auditLogRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditLogAspect auditLogAspect(@NonNull AuditLogService auditLogService) {
        return new AuditLogAspect(auditLogService);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditLogController auditLogController(@NonNull AuditLogRepository auditLogRepository) {
        return new AuditLogController(auditLogRepository);
    }

    // ============================================================
    // Retention
    // ============================================================

    @Bean
    @ConditionalOnMissingBean
    public RetentionPolicyRepository retentionPolicyRepository(@NonNull DataSource dataSource) {
        return new RetentionPolicyRepository(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public RetentionPolicyService retentionPolicyService(@NonNull RetentionPolicyRepository retentionPolicyRepository) {
        return new RetentionPolicyService(retentionPolicyRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public DataRetentionScheduler dataRetentionScheduler(@NonNull RetentionPolicyService retentionPolicyService,
                                                         @NonNull DataSource chorusObserveDataSource) {
        return new DataRetentionScheduler(retentionPolicyService, chorusObserveDataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public RetentionPolicyController retentionPolicyController(@NonNull RetentionPolicyService retentionPolicyService) {
        return new RetentionPolicyController(retentionPolicyService);
    }

    // ============================================================
    // Export
    // ============================================================

    @Bean
    @ConditionalOnMissingBean
    public ExportJobRepository exportJobRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new ExportJobRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExportService exportService(@NonNull ExportJobRepository exportJobRepository, @NonNull DataSource chorusObserveDataSource) {
        return new ExportService(exportJobRepository, chorusObserveDataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExportController exportController(@NonNull ExportService exportService, @NonNull ExportJobRepository exportJobRepository) {
        return new ExportController(exportService, exportJobRepository);
    }

    // ============================================================
    // Notifications
    // ============================================================

    @Bean
    @ConditionalOnMissingBean
    public NotificationChannelRepository notificationChannelRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new NotificationChannelRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertRuleChannelRepository alertRuleChannelRepository(@NonNull DataSource dataSource) {
        return new AlertRuleChannelRepository(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public SlackDispatcher slackDispatcher(@NonNull ObjectMapper mapper) {
        return new SlackDispatcher(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public PagerDutyDispatcher pagerDutyDispatcher(@NonNull ObjectMapper mapper) {
        return new PagerDutyDispatcher(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public EmailDispatcher emailDispatcher() {
        return new EmailDispatcher();
    }

    @Bean
    @ConditionalOnMissingBean
    public WebhookDispatcher webhookDispatcher(@NonNull ObjectMapper mapper) {
        return new WebhookDispatcher(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public NotificationService notificationService(@NonNull NotificationChannelRepository notificationChannelRepository,
                                                   @NonNull AlertRuleChannelRepository alertRuleChannelRepository,
                                                   @NonNull List<NotificationDispatcher> dispatchers) {
        return new NotificationService(notificationChannelRepository, alertRuleChannelRepository, dispatchers);
    }

    @Bean
    @ConditionalOnMissingBean
    public NotificationChannelController notificationChannelController(@NonNull NotificationService notificationService) {
        return new NotificationChannelController(notificationService);
    }

    // ============================================================
    // Dashboards
    // ============================================================

    @Bean
    @ConditionalOnMissingBean
    public DashboardRepository dashboardRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new DashboardRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public DashboardWidgetRepository dashboardWidgetRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new DashboardWidgetRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public CustomDashboardService customDashboardService(@NonNull DashboardRepository dashboardRepository,
                                                         @NonNull DashboardWidgetRepository dashboardWidgetRepository,
                                                         @NonNull DataSource chorusObserveDataSource) {
        return new CustomDashboardService(dashboardRepository, dashboardWidgetRepository, chorusObserveDataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public DashboardController dashboardController(@NonNull CustomDashboardService customDashboardService) {
        return new DashboardController(customDashboardService);
    }

    // ============================================================
    // Sampling
    // ============================================================

    @Bean
    @ConditionalOnMissingBean
    public Sampler sampler(@NonNull ChorusObserveProperties properties) {
        if (!properties.getSampling().isEnabled()) {
            return new RandomSampler(1.0);
        }
        double rate = properties.getSampling().getRate();
        return switch (properties.getSampling().getStrategy()) {
            case "head_based" -> new HeadBasedSampler(rate);
            case "tail_based" -> new TailBasedSampler(rate, 5000);
            default -> new RandomSampler(rate);
        };
    }

    // ============================================================
    // Embedding
    // ============================================================

    @Bean
    @ConditionalOnMissingBean
    public EmbeddingInvoker embeddingInvoker(@NonNull ObjectMapper mapper, @NonNull ChorusObserveProperties properties) {
        String endpoint = properties.getEval().getAgentEndpoint();
        // Default to OpenAI-style embeddings endpoint if the agent endpoint looks like a base URL
        String embeddingEndpoint = endpoint.replace("/invoke", "/embeddings");
        return new HttpEmbeddingInvoker(mapper, embeddingEndpoint);
    }
}

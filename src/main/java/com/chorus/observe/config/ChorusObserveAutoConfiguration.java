package com.chorus.observe.config;

import com.chorus.observe.api.*;
import com.chorus.observe.audit.*;
import com.chorus.observe.config.ChorusObserveHealthIndicator;
import com.chorus.observe.dashboard.CustomDashboardService;
import com.chorus.observe.embedding.EmbeddingInvoker;
import com.chorus.observe.embedding.HttpEmbeddingInvoker;
import com.chorus.observe.eval.*;
import com.chorus.observe.event.*;
import com.chorus.observe.export.*;
import com.chorus.observe.notification.*;
import com.chorus.observe.persistence.*;
import com.chorus.observe.security.LoginAttemptService;
import com.chorus.observe.retention.*;
import com.chorus.observe.sampling.*;
import com.chorus.observe.security.*;
import com.chorus.observe.security.oauth2.*;
import com.chorus.observe.security.saml2.*;
import com.chorus.observe.security.scim.*;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.sql.DataSource;
import com.chorus.observe.budget.BudgetAwareAgentInvoker;
import com.chorus.observe.budget.PricingTable;
import com.chorus.observe.budget.DynamicPricingService;
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
@EnableMethodSecurity
@EnableTransactionManagement
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
    @ConditionalOnMissingBean
    public org.springframework.transaction.PlatformTransactionManager chorusObserveTransactionManager(@NonNull DataSource chorusObserveDataSource) {
        return new org.springframework.jdbc.datasource.DataSourceTransactionManager(chorusObserveDataSource);
    }

    @Bean
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
    public AgentRepository agentRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new AgentRepository(dataSource, mapper);
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
    @Conditional(ClickHouseEnabledCondition.class)
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
    public EvaluatorRepository evaluatorRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new EvaluatorRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RunEvaluationRepository runEvaluationRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new RunEvaluationRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public EvalResultRunRepository evalResultRunRepository(@NonNull DataSource dataSource) {
        return new EvalResultRunRepository(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public GeneratedEvalCaseRepository generatedEvalCaseRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new GeneratedEvalCaseRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public OtlpIngestionService otlpIngestionService(
            @NonNull RunRepository runRepository,
            @NonNull SpanStore spanStore,
            @NonNull ObjectMapper mapper,
            @NonNull ChorusObserveProperties properties,
            @NonNull IngestionQueueRepository ingestionQueueRepository,
            ObjectProvider<SpanStreamService> streamServiceProvider,
            ObjectProvider<MetricsService> metricsServiceProvider,
            ObjectProvider<AgentRepository> agentRepositoryProvider,
            ObjectProvider<ApplicationEventPublisher> eventPublisherProvider) {
        SpanStreamService streamService = streamServiceProvider.getIfAvailable();
        MetricsService metricsService = metricsServiceProvider.getIfAvailable();
        AgentRepository agentRepository = agentRepositoryProvider.getIfAvailable();
        ApplicationEventPublisher eventPublisher = eventPublisherProvider.getIfAvailable();
        return new OtlpIngestionService(runRepository, spanStore, mapper, properties, ingestionQueueRepository, streamService, metricsService, agentRepository, eventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean
    public NgramHallucinationScorer ngramHallucinationScorer() {
        return new NgramHallucinationScorer();
    }

    @Bean
    @ConditionalOnMissingBean
    public LlmJudgeHallucinationScorer llmJudgeHallucinationScorer(@NonNull ObjectMapper mapper) {
        return new LlmJudgeHallucinationScorer(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public EvaluatorService evaluatorService(
            @NonNull EvaluatorRepository evaluatorRepository,
            @NonNull RunEvaluationRepository runEvaluationRepository,
            @NonNull LlmCallRepository llmCallRepository,
            @NonNull RunRepository runRepository,
            @NonNull AgentInvoker agentInvoker,
            @NonNull NgramHallucinationScorer ngramScorer,
            @NonNull LlmJudgeHallucinationScorer llmJudgeScorer) {
        return new EvaluatorService(evaluatorRepository, runEvaluationRepository, llmCallRepository, runRepository, agentInvoker, ngramScorer, llmJudgeScorer);
    }

    @Bean
    @ConditionalOnMissingBean
    public EvaluatorController evaluatorController(@NonNull EvaluatorService evaluatorService) {
        return new EvaluatorController(evaluatorService);
    }

    @Bean
    @ConditionalOnMissingBean
    public EvalLoopRepository evalLoopRepository(@NonNull DataSource dataSource) {
        return new EvalLoopRepository(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public EvalLoopService evalLoopService(@NonNull EvalLoopRepository evalLoopRepository) {
        return new EvalLoopService(evalLoopRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public EvalLoopController evalLoopController(@NonNull EvalLoopService evalLoopService) {
        return new EvalLoopController(evalLoopService);
    }

    @Bean
    @ConditionalOnMissingBean
    public RunCompletedEventListener runCompletedEventListener(
            @NonNull EvaluatorRepository evaluatorRepository,
            @NonNull EvaluatorService evaluatorService,
            @NonNull RunRepository runRepository,
            @NonNull EvalLoopRepository evalLoopRepository) {
        return new RunCompletedEventListener(evaluatorRepository, evaluatorService, runRepository, evalLoopRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public EvalGenerationService evalGenerationService(
            @NonNull RunRepository runRepository,
            @NonNull LlmCallRepository llmCallRepository,
            @NonNull SpanRepository spanRepository,
            @NonNull GeneratedEvalCaseRepository generatedEvalCaseRepository) {
        return new EvalGenerationService(runRepository, llmCallRepository, spanRepository, generatedEvalCaseRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public EvalReviewService evalReviewService(
            @NonNull GeneratedEvalCaseRepository generatedEvalCaseRepository,
            @NonNull DatasetRepository datasetRepository,
            @NonNull DatasetItemRepository datasetItemRepository) {
        return new EvalReviewService(generatedEvalCaseRepository, datasetRepository, datasetItemRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public EvalGenerationController evalGenerationController(@NonNull EvalGenerationService evalGenerationService) {
        return new EvalGenerationController(evalGenerationService);
    }

    @Bean
    @ConditionalOnMissingBean
    public EvalReviewController evalReviewController(@NonNull EvalReviewService evalReviewService) {
        return new EvalReviewController(evalReviewService);
    }

    @Bean
    @ConditionalOnMissingBean
    public RunService runService(@NonNull RunRepository runRepository, @NonNull EvalResultRepository evalResultRepository) {
        return new RunService(runRepository, evalResultRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentService agentService(@NonNull AgentRepository agentRepository,
                                     @NonNull RunRepository runRepository,
                                     @NonNull DataSource chorusObserveDataSource) {
        return new AgentService(agentRepository, runRepository, chorusObserveDataSource);
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
    public ModelService modelService(@NonNull DataSource chorusObserveDataSource) {
        return new ModelService(chorusObserveDataSource);
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
    public AgentController agentController(@NonNull AgentService agentService) {
        return new AgentController(agentService);
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
    public ModelController modelController(@NonNull ModelService modelService) {
        return new ModelController(modelService);
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
    public ChorusObserveHealthIndicator chorusObserveHealthIndicator(
            @NonNull DataSource chorusObserveDataSource,
            @NonNull SpanStore spanStore,
            @NonNull ObjectProvider<Server> grpcServerProvider) {
        return new ChorusObserveHealthIndicator(chorusObserveDataSource, spanStore, grpcServerProvider);
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
    public DynamicPricingService dynamicPricingService(
            @NonNull ChorusObserveProperties properties,
            @NonNull PricingTable pricingTable,
            @NonNull ObjectMapper mapper) {
        return new DynamicPricingService(properties, pricingTable, mapper);
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
            ObjectProvider<EvalResultRunRepository> evalResultRunRepositoryProvider,
            @NonNull AgentInvoker agentInvoker,
            @NonNull ObjectMapper mapper,
            ObjectProvider<MetricsService> metricsServiceProvider) {
        EvalResultRunRepository evalResultRunRepository = evalResultRunRepositoryProvider.getIfAvailable();
        MetricsService metricsService = metricsServiceProvider.getIfAvailable();
        return new EvalService(datasetRepository, datasetItemRepository, evalRunRepository, evalResultRepository, evalResultRunRepository, agentInvoker, mapper, metricsService);
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
    public IngestionQueueRepository ingestionQueueRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new IngestionQueueRepository(dataSource, mapper);
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
    public FilterRegistrationBean<TracingFilter> tracingFilter() {
        FilterRegistrationBean<TracingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TracingFilter());
        registration.addUrlPatterns("/api/*", "/v1/*");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(@NonNull ChorusObserveProperties properties) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RateLimitFilter(properties.getRateLimit().getMaxRequestsPerMinute(), properties.getRateLimit().isEnabled()));
        registration.addUrlPatterns("/api/*", "/v1/*", "/scim/*", "/auth/*");
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
    public PermissionInterceptor permissionInterceptor() {
        return new PermissionInterceptor();
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
    public WebMvcConfigurer chorusObserveWebMvcConfigurer(
            @NonNull RequestLoggingInterceptor interceptor,
            @NonNull ApiVersionInterceptor apiVersionInterceptor,
            @NonNull PermissionInterceptor permissionInterceptor,
            @NonNull ChorusObserveProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@NonNull CorsRegistry registry) {
                String frontendUrl = properties.getFrontend().getUrl();
                registry.addMapping("/api/**")
                    .allowedOrigins(frontendUrl)
                    .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                    .allowedHeaders("Authorization", "Content-Type", "X-API-Key", "X-Tenant-Id", "X-Trace-Id")
                    .allowCredentials(true)
                    .maxAge(3600);
                registry.addMapping("/v1/**")
                    .allowedOrigins(frontendUrl)
                    .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                    .allowedHeaders("Authorization", "Content-Type", "X-API-Key", "X-Tenant-Id", "X-Trace-Id")
                    .allowCredentials(true)
                    .maxAge(3600);
            }

            @Override
            public void addInterceptors(@NonNull InterceptorRegistry registry) {
                registry.addInterceptor(permissionInterceptor).addPathPatterns("/api/**", "/v1/**");
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
    public GrpcServerLifecycle grpcServerLifecycle(@NonNull OtlpIngestionService ingestionService, @NonNull ChorusObserveProperties properties) {
        int port = properties.getGrpc().getPort();
        Server server = ServerBuilder.forPort(port)
            .addService(new OtlpGrpcService(ingestionService))
            .build();
        try {
            server.start();
            LOG.info("Chorus Observe OTLP gRPC server started on port {}", port);
        } catch (Exception e) {
            LOG.error("Failed to start gRPC server on port {}", port, e);
            throw new IllegalStateException("Failed to start gRPC server on port " + port, e);
        }
        return new GrpcServerLifecycle(server);
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
            .validateOnMigrate(false)
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
            throw new IllegalStateException(
                "JWT secret is not configured. Set chorus.observe.jwt.secret or the JWT_SECRET environment variable. " +
                "This is required for session persistence across restarts."
            );
        }
        if (secret.length() < 32) {
            throw new IllegalStateException(
                "JWT secret must be at least 32 characters. Current length: " + secret.length()
            );
        }
        return new JwtTokenService(secret, Duration.ofMinutes(properties.getJwt().getExpiryMinutes()));
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenBlacklistRepository tokenBlacklistRepository(@NonNull DataSource chorusObserveDataSource) {
        return new TokenBlacklistRepository(chorusObserveDataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public RefreshTokenRepository refreshTokenRepository(@NonNull DataSource chorusObserveDataSource) {
        return new RefreshTokenRepository(chorusObserveDataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public PasswordResetRepository passwordResetRepository(@NonNull DataSource chorusObserveDataSource) {
        return new PasswordResetRepository(chorusObserveDataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public LoginAttemptService loginAttemptService(@NonNull DataSource chorusObserveDataSource) {
        return new LoginAttemptService(chorusObserveDataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityFilterChain chorusObserveSecurityFilterChain(
            @NonNull HttpSecurity http,
            @NonNull JwtTokenService jwtTokenService,
            @NonNull TokenBlacklistRepository tokenBlacklistRepository,
            @NonNull ApiKeyRepository apiKeyRepository,
            @NonNull TenantOauthConfigClientRegistrationRepository clientRegistrationRepository,
            @NonNull ChorusOauth2AuthenticationSuccessHandler oauth2SuccessHandler,
            @NonNull TenantSamlConfigRelyingPartyRegistrationRepository relyingPartyRegistrationRepository,
            @NonNull ChorusSaml2AuthenticationSuccessHandler saml2SuccessHandler,
            @NonNull ScimTokenAuthFilter scimTokenAuthFilter,
            @NonNull ChorusObserveProperties properties) throws Exception {

        JwtAuthFilter jwtAuthFilter = new JwtAuthFilter(jwtTokenService, tokenBlacklistRepository, true);
        ApiKeyAuthFilter apiKeyAuthFilter = new ApiKeyAuthFilter(apiKeyRepository, properties.getSecurity().isApiKeyEnabled());

        http
            .securityMatcher("/api/**", "/v1/**", "/actuator/**", "/oauth2/**", "/login/oauth2/**", "/saml2/**", "/login/saml2/**", "/scim/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(contentType -> {})
                .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31536000).includeSubDomains(true))
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self';"))
                .referrerPolicy(referrer -> referrer.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/actuator/health", "/actuator/info", "/actuator/prometheus", "/actuator/metrics",
                    "/v3/api-docs", "/swagger-ui", "/swagger-ui.html", "/webjars/**",
                    "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/logout",
                    "/api/v1/auth/refresh", "/api/v1/auth/forgot-password",
                    "/api/v1/auth/reset-password", "/api/v1/auth/verify-email",
                    "/oauth2/**", "/login/oauth2/**", "/saml2/**", "/login/saml2/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .clientRegistrationRepository(clientRegistrationRepository)
                .successHandler(oauth2SuccessHandler)
            )
            .saml2Login(saml2 -> saml2
                .relyingPartyRegistrationRepository(relyingPartyRegistrationRepository)
                .successHandler(saml2SuccessHandler)
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(scimTokenAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
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
    public TenantOauthConfigRepository tenantOauthConfigRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new TenantOauthConfigRepository(dataSource, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantSamlConfigRepository tenantSamlConfigRepository(@NonNull DataSource dataSource) {
        return new TenantSamlConfigRepository(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public ScimTokenRepository scimTokenRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        return new ScimTokenRepository(dataSource, mapper);
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
    public AuthenticationService authenticationService(@NonNull UserService userService,
                                                       @NonNull ApiKeyRepository apiKeyRepository,
                                                       @NonNull JwtTokenService jwtTokenService,
                                                       @NonNull RefreshTokenRepository refreshTokenRepository,
                                                       @NonNull PasswordEncoder passwordEncoder,
                                                       @NonNull ChorusObserveProperties properties) {
        return new AuthenticationService(userService, apiKeyRepository, jwtTokenService,
            refreshTokenRepository, passwordEncoder, properties.getJwt().getRefreshExpiryDays());
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthController authController(@NonNull AuthenticationService authenticationService,
                                         @NonNull UserService userService,
                                         @NonNull TenantRepository tenantRepository,
                                         @NonNull RoleService roleService,
                                         @NonNull TokenBlacklistRepository tokenBlacklistRepository,
                                         @NonNull RefreshTokenRepository refreshTokenRepository,
                                         @NonNull PasswordResetRepository passwordResetRepository,
                                         @NonNull LoginAttemptService loginAttemptService,
                                         @NonNull JwtTokenService jwtTokenService) {
        return new AuthController(authenticationService, userService, tenantRepository, roleService,
            tokenBlacklistRepository, refreshTokenRepository, passwordResetRepository, loginAttemptService, jwtTokenService);
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

    @Bean
    @ConditionalOnMissingBean
    public JitProvisioningService jitProvisioningService(@NonNull UserRepository userRepository,
                                                         @NonNull UserRoleRepository userRoleRepository,
                                                         @NonNull RoleRepository roleRepository) {
        return new JitProvisioningService(userRepository, userRoleRepository, roleRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantOauthConfigClientRegistrationRepository clientRegistrationRepository(
            @NonNull TenantOauthConfigRepository configRepository) {
        return new TenantOauthConfigClientRegistrationRepository(configRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public ChorusOauth2AuthenticationSuccessHandler oauth2SuccessHandler(
            @NonNull JitProvisioningService jitProvisioningService,
            @NonNull JwtTokenService jwtTokenService,
            @NonNull TenantOauthConfigRepository configRepository,
            @NonNull ChorusObserveProperties properties) {
        return new ChorusOauth2AuthenticationSuccessHandler(
            jitProvisioningService, jwtTokenService, configRepository, properties.getFrontend().getUrl());
    }

    @Bean
    @ConditionalOnMissingBean
    public Oauth2ConfigService oauth2ConfigService(@NonNull TenantOauthConfigRepository configRepository,
                                                   @NonNull UserRepository userRepository,
                                                   @NonNull UserRoleRepository userRoleRepository,
                                                   @NonNull RoleRepository roleRepository) {
        return new Oauth2ConfigService(configRepository, userRepository, userRoleRepository, roleRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public Oauth2ConfigController oauth2ConfigController(@NonNull Oauth2ConfigService oauth2ConfigService) {
        return new Oauth2ConfigController(oauth2ConfigService);
    }

    @Bean
    @ConditionalOnMissingBean
    public MetadataResolver metadataResolver() {
        return new MetadataResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public AssertionIdCache assertionIdCache() {
        return new AssertionIdCache();
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantSamlConfigRelyingPartyRegistrationRepository relyingPartyRegistrationRepository(
            @NonNull TenantSamlConfigRepository configRepository,
            @NonNull MetadataResolver metadataResolver) {
        return new TenantSamlConfigRelyingPartyRegistrationRepository(configRepository, metadataResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public ChorusSaml2AuthenticationSuccessHandler saml2SuccessHandler(
            @NonNull JitProvisioningService jitProvisioningService,
            @NonNull JwtTokenService jwtTokenService,
            @NonNull AssertionIdCache assertionIdCache,
            @NonNull TenantSamlConfigRepository configRepository,
            @NonNull ChorusObserveProperties properties) {
        return new ChorusSaml2AuthenticationSuccessHandler(
            jitProvisioningService, jwtTokenService, assertionIdCache, configRepository,
            properties.getFrontend().getUrl());
    }

    @Bean
    @ConditionalOnMissingBean
    public SamlConfigService samlConfigService(@NonNull TenantSamlConfigRepository configRepository,
                                               @NonNull UserRepository userRepository,
                                               @NonNull UserRoleRepository userRoleRepository,
                                               @NonNull RoleRepository roleRepository) {
        return new SamlConfigService(configRepository, userRepository, userRoleRepository, roleRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public SamlConfigController samlConfigController(@NonNull SamlConfigService samlConfigService) {
        return new SamlConfigController(samlConfigService);
    }

    @Bean
    @ConditionalOnMissingBean
    public ScimTokenAuthFilter scimTokenAuthFilter(@NonNull ScimTokenRepository tokenRepository) {
        return new ScimTokenAuthFilter(tokenRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public ScimUserService scimUserService(@NonNull UserRepository userRepository,
                                           @NonNull UserRoleRepository userRoleRepository,
                                           @NonNull RoleRepository roleRepository) {
        return new ScimUserService(userRepository, userRoleRepository, roleRepository);
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
    public SchemaRegistry schemaRegistry() {
        SchemaRegistry registry = new SchemaRegistry();
        registry.register("SpanExport", 1, SpanExportRecord.class, SchemaRegistry.Compatibility.BACKWARD);
        registry.register("MetricExport", 1, MetricExportRecord.class, SchemaRegistry.Compatibility.BACKWARD);
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public ParquetExportWriter parquetExportWriter(@NonNull SchemaRegistry schemaRegistry) {
        return new ParquetExportWriter(schemaRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExportQueryBuilder exportQueryBuilder() {
        return new ExportQueryBuilder();
    }

    @Bean
    @ConditionalOnMissingBean
    public CredentialEncryptionService credentialEncryptionService(@NonNull ChorusObserveProperties properties) {
        return new CredentialEncryptionService(properties.getExport().getEncryptionMasterKey());
    }

    @Bean
    @ConditionalOnMissingBean
    public S3ExportClient s3ExportClient(@NonNull CredentialEncryptionService encryptionService) {
        return new S3ExportClient(encryptionService);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExportConfigRepository exportConfigRepository(@NonNull DataSource dataSource) {
        return new ExportConfigRepository(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExportService exportService(
            @NonNull ExportJobRepository exportJobRepository,
            @NonNull DataSource chorusObserveDataSource,
            ObjectProvider<DataSource> clickHouseDataSourceProvider,
            @NonNull ParquetExportWriter parquetExportWriter,
            @NonNull ExportQueryBuilder exportQueryBuilder,
            @NonNull ExportConfigRepository exportConfigRepository,
            @NonNull S3ExportClient s3ExportClient,
            @NonNull ObjectMapper chorusObserveObjectMapper) {
        DataSource chDs = clickHouseDataSourceProvider.getIfAvailable();
        return new ExportService(exportJobRepository, chorusObserveDataSource, chDs,
            parquetExportWriter, exportQueryBuilder, exportConfigRepository, s3ExportClient, chorusObserveObjectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExportController exportController(@NonNull ExportService exportService, @NonNull ExportJobRepository exportJobRepository) {
        return new ExportController(exportService, exportJobRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExportConfigController exportConfigController(
            @NonNull ExportConfigRepository exportConfigRepository,
            @NonNull CredentialEncryptionService credentialEncryptionService) {
        return new ExportConfigController(exportConfigRepository, credentialEncryptionService);
    }

    @Bean
    @ConditionalOnMissingBean
    public PlaygroundController playgroundController(@NonNull ObjectMapper mapper) {
        return new PlaygroundController(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public InsightsController insightsController(@NonNull TraceClusterService traceClusterService) {
        return new InsightsController(traceClusterService);
    }

    @Bean
    @ConditionalOnMissingBean
    public FeedbackQueueController feedbackQueueController(@NonNull FeedbackRepository feedbackRepository) {
        return new FeedbackQueueController(feedbackRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public RagController ragController() {
        return new RagController();
    }

    @Bean
    @ConditionalOnMissingBean
    public ExportJobScheduler exportJobScheduler(@NonNull ExportJobRepository exportJobRepository, @NonNull ExportService exportService) {
        return new ExportJobScheduler(exportJobRepository, exportService);
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
    public TeamsDispatcher teamsDispatcher(@NonNull ObjectMapper mapper) {
        return new TeamsDispatcher(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public NotificationService notificationService(@NonNull NotificationChannelRepository notificationChannelRepository,
                                                   @NonNull AlertRuleChannelRepository alertRuleChannelRepository,
                                                   @NonNull AlertEventRepository alertEventRepository,
                                                   @NonNull List<NotificationDispatcher> dispatchers) {
        return new NotificationService(notificationChannelRepository, alertRuleChannelRepository, alertEventRepository, dispatchers);
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertRetryScheduler alertRetryScheduler(@NonNull AlertEventRepository alertEventRepository,
                                                   @NonNull AlertRuleRepository alertRuleRepository,
                                                   @NonNull NotificationService notificationService) {
        return new AlertRetryScheduler(alertEventRepository, alertRuleRepository, notificationService);
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

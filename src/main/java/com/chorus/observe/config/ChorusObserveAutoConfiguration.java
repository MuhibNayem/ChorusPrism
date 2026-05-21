package com.chorus.observe.config;

import com.chorus.observe.api.*;
import com.chorus.observe.config.ChorusObserveHealthIndicator;
import com.chorus.observe.persistence.*;
import com.chorus.observe.service.*;
import com.chorus.observe.store.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Spring Boot auto-configuration for Chorus Observe Server.
 */
@Configuration
@EnableConfigurationProperties(ChorusObserveProperties.class)
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
            @NonNull ChorusObserveProperties properties) {
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
            return new ClickHouseSpanStore(chDataSource);
        } else if ("dual".equalsIgnoreCase(storeType)) {
            if (chDataSource == null) {
                throw new IllegalStateException(
                    "Chorus Observe span store is set to 'dual' but no ClickHouse DataSource is configured. " +
                    "Set chorus.observe.clickhouse.url (and username/password)."
                );
            }
            LOG.info("Chorus Observe using dual-write span store (PostgreSQL + ClickHouse)");
            SpanStore clickHouseStore = new ClickHouseSpanStore(chDataSource);
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
            ObjectProvider<SpanStreamService> streamServiceProvider) {
        SpanStreamService streamService = streamServiceProvider.getIfAvailable();
        return new OtlpIngestionService(runRepository, spanStore, mapper, streamService);
    }

    @Bean
    @ConditionalOnMissingBean
    public RunService runService(@NonNull RunRepository runRepository) {
        return new RunService(runRepository);
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
}

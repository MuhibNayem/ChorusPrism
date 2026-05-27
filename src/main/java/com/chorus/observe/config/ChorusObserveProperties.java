package com.chorus.observe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Chorus Observe Server.
 */
@ConfigurationProperties(prefix = "chorus.observe")
public class ChorusObserveProperties {

    private boolean enabled = true;
    private Server server = new Server();
    private Database database = new Database();
    private Storage storage = new Storage();
    private ClickHouse clickhouse = new ClickHouse();
    private Grpc grpc = new Grpc();
    private Eval eval = new Eval();
    private Security security = new Security();
    private RateLimit rateLimit = new RateLimit();
    private Lock lock = new Lock();
    private Jwt jwt = new Jwt();
    private Sampling sampling = new Sampling();
    private Frontend frontend = new Frontend();
    private Smtp smtp = new Smtp();
    private Export export = new Export();
    private Pricing pricing = new Pricing();
    private IngestionQueue ingestionQueue = new IngestionQueue();

    public Export getExport() { return export; }
    public void setExport(Export export) { this.export = export; }

    public Pricing getPricing() { return pricing; }
    public void setPricing(Pricing pricing) { this.pricing = pricing; }

    public IngestionQueue getIngestionQueue() { return ingestionQueue; }
    public void setIngestionQueue(IngestionQueue ingestionQueue) { this.ingestionQueue = ingestionQueue; }

    public Lock getLock() { return lock; }

    public Jwt getJwt() { return jwt; }
    public void setJwt(Jwt jwt) { this.jwt = jwt; }

    public Sampling getSampling() { return sampling; }
    public void setSampling(Sampling sampling) { this.sampling = sampling; }
    public void setLock(Lock lock) { this.lock = lock; }

    public Frontend getFrontend() { return frontend; }
    public void setFrontend(Frontend frontend) { this.frontend = frontend; }

    public Smtp getSmtp() { return smtp; }
    public void setSmtp(Smtp smtp) { this.smtp = smtp; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Server getServer() { return server; }
    public void setServer(Server server) { this.server = server; }

    public Database getDatabase() { return database; }
    public void setDatabase(Database database) { this.database = database; }

    public Storage getStorage() { return storage; }
    public void setStorage(Storage storage) { this.storage = storage; }

    public ClickHouse getClickhouse() { return clickhouse; }
    public void setClickhouse(ClickHouse clickhouse) { this.clickhouse = clickhouse; }

    public Grpc getGrpc() { return grpc; }
    public void setGrpc(Grpc grpc) { this.grpc = grpc; }

    public Eval getEval() { return eval; }
    public void setEval(Eval eval) { this.eval = eval; }

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    public RateLimit getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimit rateLimit) { this.rateLimit = rateLimit; }

    public static class Server {
        private int port = 8080;
        private String contextPath = "";
        private int maxRequestSizeMb = 10;
        private int maxFileSizeMb = 10;

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getContextPath() { return contextPath; }
        public void setContextPath(String contextPath) { this.contextPath = contextPath; }
        public int getMaxRequestSizeMb() { return maxRequestSizeMb; }
        public void setMaxRequestSizeMb(int maxRequestSizeMb) { this.maxRequestSizeMb = maxRequestSizeMb; }
        public int getMaxFileSizeMb() { return maxFileSizeMb; }
        public void setMaxFileSizeMb(int maxFileSizeMb) { this.maxFileSizeMb = maxFileSizeMb; }
    }

    public static class Database {
        private String url = "";
        private String username = "";
        private String password = "";
        private int maxPoolSize = 20;
        private boolean migrateOnStartup = true;
        private String readOnlyRole = ""; // e.g. "chorus_readonly"; empty = skip SET ROLE

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        public boolean isMigrateOnStartup() { return migrateOnStartup; }
        public void setMigrateOnStartup(boolean migrateOnStartup) { this.migrateOnStartup = migrateOnStartup; }
        public String getReadOnlyRole() { return readOnlyRole; }
        public void setReadOnlyRole(String readOnlyRole) { this.readOnlyRole = readOnlyRole; }
    }

    public static class Storage {
        private String spanStore = "postgresql"; // postgresql | clickhouse | dual

        public String getSpanStore() { return spanStore; }
        public void setSpanStore(String spanStore) { this.spanStore = spanStore; }
    }

    public static class ClickHouse {
        private String url = "";
        private String username = "";
        private String password = "";
        private int maxPoolSize = 20;

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
    }

    public static class Grpc {
        private boolean enabled = true;
        private int port = 4317;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
    }

    public static class Eval {
        private String agentEndpoint = "http://localhost:8080/invoke";
        private int defaultParallelism = 8;
        private int maxParallelism = 32;
        private String llmJudgeUrl;
        private String embeddingModel = "text-embedding-3-small";
        /** Explicit OpenAI-compatible embeddings endpoint. When set, takes precedence over the derived agentEndpoint. */
        private String embeddingEndpoint;

        public String getAgentEndpoint() { return agentEndpoint; }
        public void setAgentEndpoint(String agentEndpoint) { this.agentEndpoint = agentEndpoint; }
        public int getDefaultParallelism() { return defaultParallelism; }
        public void setDefaultParallelism(int defaultParallelism) { this.defaultParallelism = defaultParallelism; }
        public int getMaxParallelism() { return maxParallelism; }
        public void setMaxParallelism(int maxParallelism) { this.maxParallelism = maxParallelism; }
        public String getLlmJudgeUrl() { return llmJudgeUrl; }
        public void setLlmJudgeUrl(String llmJudgeUrl) { this.llmJudgeUrl = llmJudgeUrl; }
        public String getEmbeddingModel() { return embeddingModel; }
        public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
        public String getEmbeddingEndpoint() { return embeddingEndpoint; }
        public void setEmbeddingEndpoint(String embeddingEndpoint) { this.embeddingEndpoint = embeddingEndpoint; }
    }

    public static class Security {
        private boolean apiKeyEnabled = false;
        private java.util.Set<String> apiKeys = java.util.Set.of();

        public boolean isApiKeyEnabled() { return apiKeyEnabled; }
        public void setApiKeyEnabled(boolean apiKeyEnabled) { this.apiKeyEnabled = apiKeyEnabled; }
        public java.util.Set<String> getApiKeys() { return apiKeys; }
        public void setApiKeys(java.util.Set<String> apiKeys) { this.apiKeys = apiKeys; }
    }

    public static class RateLimit {
        private boolean enabled = false;
        private int maxRequestsPerMinute = 100;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxRequestsPerMinute() { return maxRequestsPerMinute; }
        public void setMaxRequestsPerMinute(int maxRequestsPerMinute) { this.maxRequestsPerMinute = maxRequestsPerMinute; }
    }

    public static class Lock {
        private long defaultTtlSeconds = 300;
        private long pollIntervalMillis = 500;

        public long getDefaultTtlSeconds() { return defaultTtlSeconds; }
        public void setDefaultTtlSeconds(long defaultTtlSeconds) { this.defaultTtlSeconds = defaultTtlSeconds; }
        public long getPollIntervalMillis() { return pollIntervalMillis; }
        public void setPollIntervalMillis(long pollIntervalMillis) { this.pollIntervalMillis = pollIntervalMillis; }
    }

    public static class Jwt {
        private String secret = "";
        private long expiryMinutes = 15; // short-lived; refresh token extends session
        private long refreshExpiryDays = 7;

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public long getExpiryMinutes() { return expiryMinutes; }
        public void setExpiryMinutes(long expiryMinutes) { this.expiryMinutes = expiryMinutes; }
        public long getRefreshExpiryDays() { return refreshExpiryDays; }
        public void setRefreshExpiryDays(long refreshExpiryDays) { this.refreshExpiryDays = refreshExpiryDays; }
    }

    public static class Sampling {
        private boolean enabled = false;
        private double rate = 1.0;
        private String strategy = "random"; // random, head_based, tail_based

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public double getRate() { return rate; }
        public void setRate(double rate) { this.rate = rate; }
        public String getStrategy() { return strategy; }
        public void setStrategy(String strategy) { this.strategy = strategy; }
    }

    public static class Frontend {
        private String url = "http://localhost:3000";

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }

    public static class Smtp {
        private String host = "";
        private int port = 587;
        private String username = "";
        private String password = "";
        private String from = "noreply@chorus.observe";
        private boolean useTls = true;

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public boolean isUseTls() { return useTls; }
        public void setUseTls(boolean useTls) { this.useTls = useTls; }
        public boolean isConfigured() { return host != null && !host.isBlank(); }
    }

    public static class Export {
        private String encryptionMasterKey = "";

        public String getEncryptionMasterKey() { return encryptionMasterKey; }
        public void setEncryptionMasterKey(String encryptionMasterKey) { this.encryptionMasterKey = encryptionMasterKey; }
    }

    public static class Pricing {
        private boolean dynamicEnabled = true;
        private String url = "https://raw.githubusercontent.com/BerriAI/litellm/main/model_prices_and_context_window.json";
        private long refreshIntervalHours = 24;

        public boolean isDynamicEnabled() { return dynamicEnabled; }
        public void setDynamicEnabled(boolean dynamicEnabled) { this.dynamicEnabled = dynamicEnabled; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public long getRefreshIntervalHours() { return refreshIntervalHours; }
        public void setRefreshIntervalHours(long refreshIntervalHours) { this.refreshIntervalHours = refreshIntervalHours; }
    }

    public static class IngestionQueue {
        private boolean enabled = true;
        private int batchSize = 500;
        private long pollIntervalMillis = 1000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public long getPollIntervalMillis() { return pollIntervalMillis; }
        public void setPollIntervalMillis(long pollIntervalMillis) { this.pollIntervalMillis = pollIntervalMillis; }
    }
}

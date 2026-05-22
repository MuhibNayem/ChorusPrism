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

    public Lock getLock() { return lock; }

    public Jwt getJwt() { return jwt; }
    public void setJwt(Jwt jwt) { this.jwt = jwt; }

    public Sampling getSampling() { return sampling; }
    public void setSampling(Sampling sampling) { this.sampling = sampling; }
    public void setLock(Lock lock) { this.lock = lock; }

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

        public String getAgentEndpoint() { return agentEndpoint; }
        public void setAgentEndpoint(String agentEndpoint) { this.agentEndpoint = agentEndpoint; }
        public int getDefaultParallelism() { return defaultParallelism; }
        public void setDefaultParallelism(int defaultParallelism) { this.defaultParallelism = defaultParallelism; }
        public int getMaxParallelism() { return maxParallelism; }
        public void setMaxParallelism(int maxParallelism) { this.maxParallelism = maxParallelism; }
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
        private long expiryMinutes = 60;

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public long getExpiryMinutes() { return expiryMinutes; }
        public void setExpiryMinutes(long expiryMinutes) { this.expiryMinutes = expiryMinutes; }
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
}

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

    public static class Server {
        private int port = 8080;
        private String contextPath = "";

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getContextPath() { return contextPath; }
        public void setContextPath(String contextPath) { this.contextPath = contextPath; }
    }

    public static class Database {
        private String url = "";
        private String username = "";
        private String password = "";
        private int maxPoolSize = 20;
        private boolean migrateOnStartup = true;

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
}

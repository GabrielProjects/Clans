package dev.clans.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.clans.ClansPlugin;
import dev.clans.config.ConfigManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DatabaseManager {

    private final ClansPlugin plugin;
    private final ConfigManager configManager;
    private HikariDataSource dataSource;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public DatabaseManager(ClansPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void init() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(String.format(
                "jdbc:mariadb://%s:%d/%s",
                configManager.getDbHost(),
                configManager.getDbPort(),
                configManager.getDbName()
        ));
        config.setUsername(configManager.getDbUsername());
        config.setPassword(configManager.getDbPassword());
        config.setMaximumPoolSize(configManager.getDbPoolSize());
        config.setPoolName("ClansPool");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");

        this.dataSource = new HikariDataSource(config);
        migrate();
    }

    private void migrate() throws SQLException {
        try (Connection connection = getConnection()) {
            int version = getSchemaVersion(connection);
            if (version < 1) {
                applyMigrationV1(connection);
                setSchemaVersion(connection, 1);
            }
        }
    }

    private int getSchemaVersion(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS schema_version (version INT NOT NULL)"
        )) {
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement("SELECT version FROM schema_version LIMIT 1");
             ResultSet rs = statement.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("version");
            }
        }

        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO schema_version (version) VALUES (0)")) {
            statement.executeUpdate();
        }
        return 0;
    }

    private void setSchemaVersion(Connection connection, int version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE schema_version SET version = ?")) {
            statement.setInt(1, version);
            statement.executeUpdate();
        }
    }

    private void applyMigrationV1(Connection connection) throws SQLException {
        try (PreparedStatement clans = connection.prepareStatement("""
                CREATE TABLE IF NOT EXISTS clans (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(32) NOT NULL UNIQUE,
                    tag VARCHAR(8) NOT NULL UNIQUE,
                    leader_uuid CHAR(36) NOT NULL,
                    home_world VARCHAR(64) NULL,
                    home_x DOUBLE NULL,
                    home_y DOUBLE NULL,
                    home_z DOUBLE NULL,
                    home_yaw FLOAT NULL,
                    home_pitch FLOAT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """)) {
            clans.executeUpdate();
        }

        try (PreparedStatement members = connection.prepareStatement("""
                CREATE TABLE IF NOT EXISTS clan_members (
                    clan_id BIGINT NOT NULL,
                    player_uuid CHAR(36) NOT NULL PRIMARY KEY,
                    role ENUM('LEADER','OFFICER','MEMBER') NOT NULL,
                    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                )
                """)) {
            members.executeUpdate();
        }

        try (PreparedStatement invites = connection.prepareStatement("""
                CREATE TABLE IF NOT EXISTS clan_invites (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    clan_id BIGINT NOT NULL,
                    inviter_uuid CHAR(36) NOT NULL,
                    invitee_uuid CHAR(36) NOT NULL,
                    expires_at TIMESTAMP NOT NULL,
                    UNIQUE KEY unique_invite (clan_id, invitee_uuid),
                    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                )
                """)) {
            invites.executeUpdate();
        }

        try (PreparedStatement claims = connection.prepareStatement("""
                CREATE TABLE IF NOT EXISTS clan_claims (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    clan_id BIGINT NOT NULL,
                    world VARCHAR(64) NOT NULL,
                    chunk_x INT NOT NULL,
                    chunk_z INT NOT NULL,
                    claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY unique_chunk (world, chunk_x, chunk_z),
                    INDEX idx_clan_id (clan_id),
                    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                )
                """)) {
            claims.executeUpdate();
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public <T> CompletableFuture<T> supplyAsync(SqlFunction<T> function) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection()) {
                return function.apply(connection);
            } catch (SQLException e) {
                plugin.getLogger().severe("Database error: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, executor);
    }

    public CompletableFuture<Void> runAsync(SqlRunnable runnable) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = getConnection()) {
                runnable.run(connection);
            } catch (SQLException e) {
                plugin.getLogger().severe("Database error: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, executor);
    }

    public void shutdown() {
        executor.shutdown();
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlRunnable {
        void run(Connection connection) throws SQLException;
    }
}

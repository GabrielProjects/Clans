package dev.clans.database.repository;

import dev.clans.database.DatabaseManager;
import dev.clans.model.Clan;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ClanRepository {

    private final DatabaseManager databaseManager;

    public ClanRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public CompletableFuture<Optional<Clan>> findById(long id) {
        return databaseManager.supplyAsync(connection -> findByIdSync(connection, id));
    }

    public Optional<Clan> findByIdSync(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM clans WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapClan(rs));
                }
            }
        }
        return Optional.empty();
    }

    public CompletableFuture<Optional<Clan>> findByName(String name) {
        return databaseManager.supplyAsync(connection -> findByNameSync(connection, name));
    }

    public Optional<Clan> findByNameSync(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM clans WHERE LOWER(name) = LOWER(?)")) {
            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapClan(rs));
                }
            }
        }
        return Optional.empty();
    }

    public CompletableFuture<Optional<Clan>> findByTag(String tag) {
        return databaseManager.supplyAsync(connection -> findByTagSync(connection, tag));
    }

    public Optional<Clan> findByTagSync(Connection connection, String tag) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM clans WHERE LOWER(tag) = LOWER(?)")) {
            statement.setString(1, tag);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapClan(rs));
                }
            }
        }
        return Optional.empty();
    }

    public CompletableFuture<List<Clan>> findAll() {
        return databaseManager.supplyAsync(connection -> {
            List<Clan> clans = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM clans ORDER BY name");
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    clans.add(mapClan(rs));
                }
            }
            return clans;
        });
    }

    public CompletableFuture<Long> create(String name, String tag, UUID leaderUuid) {
        return databaseManager.supplyAsync(connection -> createSync(connection, name, tag, leaderUuid));
    }

    public long createSync(Connection connection, String name, String tag, UUID leaderUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO clans (name, tag, leader_uuid) VALUES (?, ?, ?)",
                PreparedStatement.RETURN_GENERATED_KEYS
        )) {
            statement.setString(1, name);
            statement.setString(2, tag);
            statement.setString(3, leaderUuid.toString());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to create clan");
    }

    public CompletableFuture<Void> delete(long clanId) {
        return databaseManager.runAsync(connection -> deleteSync(connection, clanId));
    }

    public void deleteSync(Connection connection, long clanId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM clans WHERE id = ?")) {
            statement.setLong(1, clanId);
            statement.executeUpdate();
        }
    }

    public CompletableFuture<Void> updateHome(long clanId, String world, double x, double y, double z, float yaw, float pitch) {
        return databaseManager.runAsync(connection -> updateHomeSync(connection, clanId, world, x, y, z, yaw, pitch));
    }

    public void updateHomeSync(Connection connection, long clanId, String world, double x, double y, double z, float yaw, float pitch) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE clans SET home_world = ?, home_x = ?, home_y = ?, home_z = ?, home_yaw = ?, home_pitch = ? WHERE id = ?"
        )) {
            statement.setString(1, world);
            statement.setDouble(2, x);
            statement.setDouble(3, y);
            statement.setDouble(4, z);
            statement.setFloat(5, yaw);
            statement.setFloat(6, pitch);
            statement.setLong(7, clanId);
            statement.executeUpdate();
        }
    }

    public CompletableFuture<Void> updateLeader(long clanId, UUID leaderUuid) {
        return databaseManager.runAsync(connection -> updateLeaderSync(connection, clanId, leaderUuid));
    }

    public void updateLeaderSync(Connection connection, long clanId, UUID leaderUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE clans SET leader_uuid = ? WHERE id = ?")) {
            statement.setString(1, leaderUuid.toString());
            statement.setLong(2, clanId);
            statement.executeUpdate();
        }
    }

    private Clan mapClan(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new Clan(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("tag"),
                UUID.fromString(rs.getString("leader_uuid")),
                rs.getString("home_world"),
                (Double) rs.getObject("home_x"),
                (Double) rs.getObject("home_y"),
                (Double) rs.getObject("home_z"),
                (Float) rs.getObject("home_yaw"),
                (Float) rs.getObject("home_pitch"),
                createdAt != null ? createdAt.toInstant() : Instant.now()
        );
    }
}

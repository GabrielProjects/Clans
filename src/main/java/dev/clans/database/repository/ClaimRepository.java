package dev.clans.database.repository;

import dev.clans.database.DatabaseManager;
import dev.clans.model.ClanClaim;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class ClaimRepository {

    private final DatabaseManager databaseManager;

    public ClaimRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public CompletableFuture<Optional<ClanClaim>> findByChunk(String world, int chunkX, int chunkZ) {
        return databaseManager.supplyAsync(connection -> findByChunkSync(connection, world, chunkX, chunkZ));
    }

    public Optional<ClanClaim> findByChunkSync(Connection connection, String world, int chunkX, int chunkZ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM clan_claims WHERE world = ? AND chunk_x = ? AND chunk_z = ?"
        )) {
            statement.setString(1, world);
            statement.setInt(2, chunkX);
            statement.setInt(3, chunkZ);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapClaim(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<ClanClaim> findByChunkNow(String world, int chunkX, int chunkZ) {
        try (java.sql.Connection connection = databaseManager.getConnection()) {
            return findByChunkSync(connection, world, chunkX, chunkZ);
        } catch (java.sql.SQLException e) {
            return Optional.empty();
        }
    }

    public CompletableFuture<List<ClanClaim>> findByClan(long clanId) {
        return databaseManager.supplyAsync(connection -> findByClanSync(connection, clanId));
    }

    public List<ClanClaim> findByClanSync(Connection connection, long clanId) throws SQLException {
        List<ClanClaim> claims = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM clan_claims WHERE clan_id = ?")) {
            statement.setLong(1, clanId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    claims.add(mapClaim(rs));
                }
            }
        }
        return claims;
    }

    public CompletableFuture<Integer> countByClan(long clanId) {
        return databaseManager.supplyAsync(connection -> countByClanSync(connection, clanId));
    }

    public int countByClanSync(Connection connection, long clanId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM clan_claims WHERE clan_id = ?")) {
            statement.setLong(1, clanId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public CompletableFuture<Long> create(long clanId, String world, int chunkX, int chunkZ) {
        return databaseManager.supplyAsync(connection -> createSync(connection, clanId, world, chunkX, chunkZ));
    }

    public long createSync(Connection connection, long clanId, String world, int chunkX, int chunkZ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO clan_claims (clan_id, world, chunk_x, chunk_z) VALUES (?, ?, ?, ?)",
                PreparedStatement.RETURN_GENERATED_KEYS
        )) {
            statement.setLong(1, clanId);
            statement.setString(2, world);
            statement.setInt(3, chunkX);
            statement.setInt(4, chunkZ);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to create claim");
    }

    public CompletableFuture<Void> delete(String world, int chunkX, int chunkZ) {
        return databaseManager.runAsync(connection -> deleteSync(connection, world, chunkX, chunkZ));
    }

    public void deleteSync(Connection connection, String world, int chunkX, int chunkZ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM clan_claims WHERE world = ? AND chunk_x = ? AND chunk_z = ?"
        )) {
            statement.setString(1, world);
            statement.setInt(2, chunkX);
            statement.setInt(3, chunkZ);
            statement.executeUpdate();
        }
    }

    public CompletableFuture<List<ClanClaim>> findAll() {
        return databaseManager.supplyAsync(connection -> {
            List<ClanClaim> claims = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM clan_claims");
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    claims.add(mapClaim(rs));
                }
            }
            return claims;
        });
    }

    public CompletableFuture<Void> deleteByClan(long clanId) {
        return databaseManager.runAsync(connection -> deleteByClanSync(connection, clanId));
    }

    public CompletableFuture<List<ClanClaim>> findInArea(String world, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
        return databaseManager.supplyAsync(connection ->
                findInAreaSync(connection, world, minChunkX, maxChunkX, minChunkZ, maxChunkZ));
    }

    public List<ClanClaim> findInAreaSync(Connection connection, String world,
                                            int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) throws SQLException {
        List<ClanClaim> claims = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT cc.* FROM clan_claims cc
                INNER JOIN clans c ON c.id = cc.clan_id
                WHERE cc.world = ? AND cc.chunk_x BETWEEN ? AND ? AND cc.chunk_z BETWEEN ? AND ?
                """)) {
            statement.setString(1, world);
            statement.setInt(2, minChunkX);
            statement.setInt(3, maxChunkX);
            statement.setInt(4, minChunkZ);
            statement.setInt(5, maxChunkZ);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    claims.add(mapClaim(rs));
                }
            }
        }
        return claims;
    }

    public void deleteByClanSync(Connection connection, long clanId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM clan_claims WHERE clan_id = ?")) {
            statement.setLong(1, clanId);
            statement.executeUpdate();
        }
    }

    private ClanClaim mapClaim(ResultSet rs) throws SQLException {
        Timestamp claimedAt = rs.getTimestamp("claimed_at");
        return new ClanClaim(
                rs.getLong("id"),
                rs.getLong("clan_id"),
                rs.getString("world"),
                rs.getInt("chunk_x"),
                rs.getInt("chunk_z"),
                claimedAt != null ? claimedAt.toInstant() : Instant.now()
        );
    }
}

package dev.clans.database.repository;

import dev.clans.database.DatabaseManager;
import dev.clans.model.ClanMember;
import dev.clans.model.ClanRole;

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

public final class MemberRepository {

    private final DatabaseManager databaseManager;

    public MemberRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public CompletableFuture<Optional<ClanMember>> findByPlayer(UUID playerUuid) {
        return databaseManager.supplyAsync(connection -> findByPlayerSync(connection, playerUuid));
    }

    public Optional<ClanMember> findByPlayerSync(Connection connection, UUID playerUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM clan_members WHERE player_uuid = ?")) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapMember(rs));
                }
            }
        }
        return Optional.empty();
    }

    public CompletableFuture<List<ClanMember>> findByClan(long clanId) {
        return databaseManager.supplyAsync(connection -> findByClanSync(connection, clanId));
    }

    public List<ClanMember> findByClanSync(Connection connection, long clanId) throws SQLException {
        List<ClanMember> members = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM clan_members WHERE clan_id = ? ORDER BY FIELD(role, 'LEADER', 'OFFICER', 'MEMBER'), joined_at"
        )) {
            statement.setLong(1, clanId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    members.add(mapMember(rs));
                }
            }
        }
        return members;
    }

    public CompletableFuture<Void> addMember(long clanId, UUID playerUuid, ClanRole role) {
        return databaseManager.runAsync(connection -> addMemberSync(connection, clanId, playerUuid, role));
    }

    public void addMemberSync(Connection connection, long clanId, UUID playerUuid, ClanRole role) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO clan_members (clan_id, player_uuid, role) VALUES (?, ?, ?)"
        )) {
            statement.setLong(1, clanId);
            statement.setString(2, playerUuid.toString());
            statement.setString(3, role.name());
            statement.executeUpdate();
        }
    }

    public CompletableFuture<Void> removeMember(UUID playerUuid) {
        return databaseManager.runAsync(connection -> removeMemberSync(connection, playerUuid));
    }

    public void removeMemberSync(Connection connection, UUID playerUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM clan_members WHERE player_uuid = ?")) {
            statement.setString(1, playerUuid.toString());
            statement.executeUpdate();
        }
    }

    public CompletableFuture<Void> updateRole(UUID playerUuid, ClanRole role) {
        return databaseManager.runAsync(connection -> updateRoleSync(connection, playerUuid, role));
    }

    public void updateRoleSync(Connection connection, UUID playerUuid, ClanRole role) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE clan_members SET role = ? WHERE player_uuid = ?")) {
            statement.setString(1, role.name());
            statement.setString(2, playerUuid.toString());
            statement.executeUpdate();
        }
    }

    public CompletableFuture<Integer> countOfficers(long clanId) {
        return databaseManager.supplyAsync(connection -> countOfficersSync(connection, clanId));
    }

    public int countOfficersSync(Connection connection, long clanId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM clan_members WHERE clan_id = ? AND role = 'OFFICER'"
        )) {
            statement.setLong(1, clanId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public CompletableFuture<Integer> countMembers(long clanId) {
        return databaseManager.supplyAsync(connection -> countMembersSync(connection, clanId));
    }

    public int countMembersSync(Connection connection, long clanId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM clan_members WHERE clan_id = ?")) {
            statement.setLong(1, clanId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    private ClanMember mapMember(ResultSet rs) throws SQLException {
        Timestamp joinedAt = rs.getTimestamp("joined_at");
        return new ClanMember(
                rs.getLong("clan_id"),
                UUID.fromString(rs.getString("player_uuid")),
                ClanRole.valueOf(rs.getString("role")),
                joinedAt != null ? joinedAt.toInstant() : Instant.now()
        );
    }
}

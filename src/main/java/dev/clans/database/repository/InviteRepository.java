package dev.clans.database.repository;

import dev.clans.database.DatabaseManager;
import dev.clans.model.ClanInvite;

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

public final class InviteRepository {

    private final DatabaseManager databaseManager;

    public InviteRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public CompletableFuture<Optional<ClanInvite>> findPendingForInvitee(UUID inviteeUuid) {
        return databaseManager.supplyAsync(connection -> findPendingForInviteeSync(connection, inviteeUuid));
    }

    public Optional<ClanInvite> findPendingForInviteeSync(Connection connection, UUID inviteeUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM clan_invites WHERE invitee_uuid = ? AND expires_at > NOW() ORDER BY expires_at DESC LIMIT 1"
        )) {
            statement.setString(1, inviteeUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapInvite(rs));
                }
            }
        }
        return Optional.empty();
    }

    public CompletableFuture<Optional<ClanInvite>> findPending(long clanId, UUID inviteeUuid) {
        return databaseManager.supplyAsync(connection -> findPendingSync(connection, clanId, inviteeUuid));
    }

    public Optional<ClanInvite> findPendingSync(Connection connection, long clanId, UUID inviteeUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM clan_invites WHERE clan_id = ? AND invitee_uuid = ? AND expires_at > NOW()"
        )) {
            statement.setLong(1, clanId);
            statement.setString(2, inviteeUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapInvite(rs));
                }
            }
        }
        return Optional.empty();
    }

    public CompletableFuture<Void> create(long clanId, UUID inviterUuid, UUID inviteeUuid, Instant expiresAt) {
        return databaseManager.runAsync(connection -> createSync(connection, clanId, inviterUuid, inviteeUuid, expiresAt));
    }

    public void createSync(Connection connection, long clanId, UUID inviterUuid, UUID inviteeUuid, Instant expiresAt) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM clan_invites WHERE clan_id = ? AND invitee_uuid = ?"
        )) {
            delete.setLong(1, clanId);
            delete.setString(2, inviteeUuid.toString());
            delete.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO clan_invites (clan_id, inviter_uuid, invitee_uuid, expires_at) VALUES (?, ?, ?, ?)"
        )) {
            statement.setLong(1, clanId);
            statement.setString(2, inviterUuid.toString());
            statement.setString(3, inviteeUuid.toString());
            statement.setTimestamp(4, Timestamp.from(expiresAt));
            statement.executeUpdate();
        }
    }

    public CompletableFuture<Void> delete(long inviteId) {
        return databaseManager.runAsync(connection -> deleteSync(connection, inviteId));
    }

    public void deleteSync(Connection connection, long inviteId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM clan_invites WHERE id = ?")) {
            statement.setLong(1, inviteId);
            statement.executeUpdate();
        }
    }

    public CompletableFuture<Void> deleteForInvitee(UUID inviteeUuid) {
        return databaseManager.runAsync(connection -> deleteForInviteeSync(connection, inviteeUuid));
    }

    public void deleteForInviteeSync(Connection connection, UUID inviteeUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM clan_invites WHERE invitee_uuid = ?")) {
            statement.setString(1, inviteeUuid.toString());
            statement.executeUpdate();
        }
    }

    public CompletableFuture<List<ClanInvite>> findExpired() {
        return databaseManager.supplyAsync(connection -> {
            List<ClanInvite> invites = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM clan_invites WHERE expires_at <= NOW()");
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    invites.add(mapInvite(rs));
                }
            }
            return invites;
        });
    }

    private ClanInvite mapInvite(ResultSet rs) throws SQLException {
        return new ClanInvite(
                rs.getLong("id"),
                rs.getLong("clan_id"),
                UUID.fromString(rs.getString("inviter_uuid")),
                UUID.fromString(rs.getString("invitee_uuid")),
                rs.getTimestamp("expires_at").toInstant()
        );
    }
}

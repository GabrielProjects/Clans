package dev.clans.model;

import java.time.Instant;
import java.util.UUID;

public final class ClanMember {

    private final long clanId;
    private final UUID playerUuid;
    private final ClanRole role;
    private final Instant joinedAt;

    public ClanMember(long clanId, UUID playerUuid, ClanRole role, Instant joinedAt) {
        this.clanId = clanId;
        this.playerUuid = playerUuid;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public long getClanId() {
        return clanId;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public ClanRole getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}

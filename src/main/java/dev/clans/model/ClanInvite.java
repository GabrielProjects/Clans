package dev.clans.model;

import java.time.Instant;
import java.util.UUID;

public final class ClanInvite {

    private final long id;
    private final long clanId;
    private final UUID inviterUuid;
    private final UUID inviteeUuid;
    private final Instant expiresAt;

    public ClanInvite(long id, long clanId, UUID inviterUuid, UUID inviteeUuid, Instant expiresAt) {
        this.id = id;
        this.clanId = clanId;
        this.inviterUuid = inviterUuid;
        this.inviteeUuid = inviteeUuid;
        this.expiresAt = expiresAt;
    }

    public long getId() {
        return id;
    }

    public long getClanId() {
        return clanId;
    }

    public UUID getInviterUuid() {
        return inviterUuid;
    }

    public UUID getInviteeUuid() {
        return inviteeUuid;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}

package dev.clans.model;

import java.time.Instant;

public final class ClanClaim {

    private final long id;
    private final long clanId;
    private final String world;
    private final int chunkX;
    private final int chunkZ;
    private final Instant claimedAt;

    public ClanClaim(long id, long clanId, String world, int chunkX, int chunkZ, Instant claimedAt) {
        this.id = id;
        this.clanId = clanId;
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.claimedAt = claimedAt;
    }

    public long getId() {
        return id;
    }

    public long getClanId() {
        return clanId;
    }

    public String getWorld() {
        return world;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }
}

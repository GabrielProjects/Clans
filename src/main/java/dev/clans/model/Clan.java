package dev.clans.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.time.Instant;
import java.util.UUID;

public final class Clan {

    private final long id;
    private final String name;
    private final String tag;
    private final UUID leaderUuid;
    private final String homeWorld;
    private final Double homeX;
    private final Double homeY;
    private final Double homeZ;
    private final Float homeYaw;
    private final Float homePitch;
    private final Instant createdAt;

    public Clan(long id, String name, String tag, UUID leaderUuid,
                String homeWorld, Double homeX, Double homeY, Double homeZ,
                Float homeYaw, Float homePitch, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.leaderUuid = leaderUuid;
        this.homeWorld = homeWorld;
        this.homeX = homeX;
        this.homeY = homeY;
        this.homeZ = homeZ;
        this.homeYaw = homeYaw;
        this.homePitch = homePitch;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    public UUID getLeaderUuid() {
        return leaderUuid;
    }

    public boolean hasHome() {
        return homeWorld != null && homeX != null && homeY != null && homeZ != null;
    }

    public Location getHomeLocation() {
        if (!hasHome()) {
            return null;
        }
        World world = Bukkit.getWorld(homeWorld);
        if (world == null) {
            return null;
        }
        Location location = new Location(world, homeX, homeY, homeZ);
        if (homeYaw != null) {
            location.setYaw(homeYaw);
        }
        if (homePitch != null) {
            location.setPitch(homePitch);
        }
        return location;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

package dev.clans.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import dev.clans.ClansPlugin;
import dev.clans.config.ConfigManager;
import dev.clans.database.repository.ClaimRepository;
import dev.clans.model.ClanClaim;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class WorldGuardIntegration {

    private final ClansPlugin plugin;
    private final ClaimRepository claimRepository;

    public WorldGuardIntegration(ClansPlugin plugin) {
        this.plugin = plugin;
        this.claimRepository = plugin.getClaimRepository();
    }

    public String regionName(long clanId, int chunkX, int chunkZ) {
        return "clan_" + clanId + "_" + chunkX + "_" + chunkZ;
    }

    public boolean createClaimRegion(long clanId, Chunk chunk, List<UUID> memberUuids) {
        ConfigManager config = plugin.getConfigManager();
        World world = chunk.getWorld();
        RegionManager manager = getRegionManager(world);
        if (manager == null) {
            return false;
        }

        String regionId = regionName(clanId, chunk.getX(), chunk.getZ());
        if (manager.hasRegion(regionId)) {
            syncMembersOnRegion(manager.getRegion(regionId), memberUuids);
            return true;
        }

        int minX = chunk.getX() << 4;
        int minZ = chunk.getZ() << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        BlockVector3 min = BlockVector3.at(minX, config.getClaimMinY(), minZ);
        BlockVector3 max = BlockVector3.at(maxX, config.getClaimMaxY(), maxZ);

        ProtectedCuboidRegion region = new ProtectedCuboidRegion(regionId, min, max);
        applyFlags(region, config.getClaimFlags());
        syncMembersOnRegion(region, memberUuids);

        try {
            manager.addRegion(region);
            manager.saveChanges();
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Errore creazione regione WG: " + e.getMessage(), e);
            return false;
        }
    }

    public void removeClaimRegion(ClanClaim claim) {
        World world = plugin.getServer().getWorld(claim.getWorld());
        if (world == null) {
            return;
        }
        RegionManager manager = getRegionManager(world);
        if (manager == null) {
            return;
        }

        String regionId = regionName(claim.getClanId(), claim.getChunkX(), claim.getChunkZ());
        try {
            manager.removeRegion(regionId);
            manager.saveChanges();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Errore rimozione regione WG: " + e.getMessage(), e);
        }
    }

    public void removeAllClaimRegions(long clanId, List<ClanClaim> claims) {
        for (ClanClaim claim : claims) {
            removeClaimRegion(claim);
        }
    }

    public void syncClanMembers(long clanId, List<UUID> memberUuids) {
        List<ClanClaim> claims = claimRepository.findByClan(clanId).join();
        for (ClanClaim claim : claims) {
            World world = plugin.getServer().getWorld(claim.getWorld());
            if (world == null) {
                continue;
            }
            RegionManager manager = getRegionManager(world);
            if (manager == null) {
                continue;
            }
            ProtectedRegion region = manager.getRegion(regionName(clanId, claim.getChunkX(), claim.getChunkZ()));
            if (region != null) {
                syncMembersOnRegion(region, memberUuids);
                try {
                    manager.saveChanges();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Errore sync membri WG: " + e.getMessage(), e);
                }
            }
        }
    }

    public void addMemberToClanRegions(long clanId, UUID playerUuid) {
        updateMemberOnAllRegions(clanId, playerUuid, true);
    }

    public void removeMemberFromClanRegions(long clanId, UUID playerUuid) {
        updateMemberOnAllRegions(clanId, playerUuid, false);
    }

    private void updateMemberOnAllRegions(long clanId, UUID playerUuid, boolean add) {
        claimRepository.findByClan(clanId).thenAccept(claims -> {
            for (ClanClaim claim : claims) {
                World world = plugin.getServer().getWorld(claim.getWorld());
                if (world == null) {
                    continue;
                }
                RegionManager manager = getRegionManager(world);
                if (manager == null) {
                    continue;
                }
                ProtectedRegion region = manager.getRegion(regionName(clanId, claim.getChunkX(), claim.getChunkZ()));
                if (region == null) {
                    continue;
                }
                DefaultDomain members = new DefaultDomain(region.getMembers());
                if (add) {
                    members.addPlayer(playerUuid);
                } else {
                    members.removePlayer(playerUuid);
                }
                region.setMembers(members);
                try {
                    manager.saveChanges();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Errore aggiornamento membro WG: " + e.getMessage(), e);
                }
            }
        });
    }

    private void syncMembersOnRegion(ProtectedRegion region, List<UUID> memberUuids) {
        DefaultDomain members = new DefaultDomain();
        for (UUID uuid : memberUuids) {
            members.addPlayer(uuid);
        }
        region.setMembers(members);
    }

    private void applyFlags(ProtectedRegion region, Map<String, String> flags) {
        for (Map.Entry<String, String> entry : flags.entrySet()) {
            StateFlag flag = resolveFlag(entry.getKey());
            if (flag == null) {
                continue;
            }
            StateFlag.State state = "allow".equalsIgnoreCase(entry.getValue())
                    ? StateFlag.State.ALLOW
                    : StateFlag.State.DENY;
            region.setFlag(flag, state);
        }
    }

    private StateFlag resolveFlag(String name) {
        return switch (name.toLowerCase()) {
            case "build" -> Flags.BUILD;
            case "block-break" -> Flags.BLOCK_BREAK;
            case "block-place" -> Flags.BLOCK_PLACE;
            case "pvp" -> Flags.PVP;
            case "mob-spawning" -> Flags.MOB_SPAWNING;
            default -> null;
        };
    }

    private RegionManager getRegionManager(World world) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        return container.get(BukkitAdapter.adapt(world));
    }

    public boolean isInOwnClaim(Player player, long clanId) {
        Chunk chunk = player.getLocation().getChunk();
        RegionManager manager = getRegionManager(chunk.getWorld());
        if (manager == null) {
            return false;
        }
        String regionId = regionName(clanId, chunk.getX(), chunk.getZ());
        ProtectedRegion region = manager.getRegion(regionId);
        return region != null;
    }
}

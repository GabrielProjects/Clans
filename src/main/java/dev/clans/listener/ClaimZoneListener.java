package dev.clans.listener;

import dev.clans.ClansPlugin;
import dev.clans.config.ConfigManager;
import dev.clans.database.repository.ClaimRepository;
import dev.clans.database.repository.ClanRepository;
import dev.clans.model.Clan;
import dev.clans.model.ClanClaim;
import dev.clans.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ClaimZoneListener implements Listener {

    private final ClansPlugin plugin;
    private final ConfigManager configManager;
    private final ClaimRepository claimRepository;
    private final ClanRepository clanRepository;

    public ClaimZoneListener(ClansPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.claimRepository = plugin.getClaimRepository();
        this.clanRepository = plugin.getClanRepository();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        handleChunkChange(event.getPlayer(), event.getFrom(), to);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        handleChunkChange(event.getPlayer(), event.getFrom(), event.getTo());
    }

    private void handleChunkChange(Player player, Location from, Location to) {
        if (!configManager.isClaimZoneNotificationsEnabled()) {
            return;
        }
        if (!Objects.equals(from.getWorld(), to.getWorld())) {
            notifyZoneChange(player, null, resolveZoneAt(to));
            return;
        }
        if (from.getChunk().getX() == to.getChunk().getX() && from.getChunk().getZ() == to.getChunk().getZ()) {
            return;
        }

        notifyZoneChange(player, resolveZoneAt(from), resolveZoneAt(to));
    }

    private void notifyZoneChange(Player player, ZoneInfo fromZone, ZoneInfo toZone) {
        Long fromClanId = fromZone != null ? fromZone.clanId() : null;
        Long toClanId = toZone != null ? toZone.clanId() : null;
        if (Objects.equals(fromClanId, toClanId)) {
            return;
        }

        if (fromZone != null && toZone != null) {
            sendZoneMessage(player, "claim.zone-transition", Map.of(
                    "from", fromZone.clanName(),
                    "to", toZone.clanName()
            ));
        } else if (toZone != null) {
            sendZoneMessage(player, "claim.zone-enter", Map.of("clan", toZone.clanName()));
        } else if (fromZone != null) {
            sendZoneMessage(player, "claim.zone-exit", Map.of("clan", fromZone.clanName()));
        }
    }

    private void sendZoneMessage(Player player, String path, Map<String, String> placeholders) {
        String message = plugin.getMessages().get(path, placeholders);
        if (message.isEmpty()) {
            return;
        }
        player.sendActionBar(MessageUtil.toComponent(message));
    }

    private ZoneInfo resolveZoneAt(Location location) {
        if (location.getWorld() == null) {
            return null;
        }
        Optional<ClanClaim> claimOpt = claimRepository.findByChunkNow(
                location.getWorld().getName(),
                location.getChunk().getX(),
                location.getChunk().getZ()
        );
        if (claimOpt.isEmpty()) {
            return null;
        }

        long clanId = claimOpt.get().getClanId();
        Optional<Clan> cached = plugin.getClanService().getCachedClan(clanId);
        if (cached.isPresent()) {
            return new ZoneInfo(clanId, cached.get().getName());
        }

        Optional<Clan> clanOpt = clanRepository.findByIdNow(clanId);
        if (clanOpt.isPresent()) {
            plugin.getClanService().cacheClan(clanOpt.get());
            return new ZoneInfo(clanId, clanOpt.get().getName());
        }
        return null;
    }

    private record ZoneInfo(long clanId, String clanName) {}
}

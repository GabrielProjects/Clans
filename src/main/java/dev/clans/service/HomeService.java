package dev.clans.service;

import dev.clans.ClansPlugin;
import dev.clans.config.ConfigManager;
import dev.clans.database.repository.ClaimRepository;
import dev.clans.database.repository.ClanRepository;
import dev.clans.model.Clan;
import dev.clans.model.ClanMember;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class HomeService {

    public enum SetHomeResult {
        SUCCESS,
        NOT_IN_CLAN,
        NO_PERMISSION,
        NOT_IN_CLAIM
    }

    public enum HomeResult {
        SUCCESS,
        NOT_IN_CLAN,
        NOT_SET,
        COOLDOWN
    }

    private final ClansPlugin plugin;
    private final ClanRepository clanRepository;
    private final ClaimRepository claimRepository;
    private final ConfigManager configManager;
    private final PermissionService permissionService;

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> warmupTasks = new ConcurrentHashMap<>();

    public HomeService(ClansPlugin plugin, ClanRepository clanRepository, ClaimRepository claimRepository,
                       ConfigManager configManager, PermissionService permissionService) {
        this.plugin = plugin;
        this.clanRepository = clanRepository;
        this.claimRepository = claimRepository;
        this.configManager = configManager;
        this.permissionService = permissionService;
    }

    public CompletableFuture<SetHomeResult> setHome(Player player) {
        return plugin.getMemberRepository().findByPlayer(player.getUniqueId()).thenCompose(memberOpt -> {
            if (memberOpt.isEmpty()) {
                return CompletableFuture.completedFuture(SetHomeResult.NOT_IN_CLAN);
            }
            ClanMember member = memberOpt.get();
            if (!permissionService.canSetHome(member)) {
                return CompletableFuture.completedFuture(SetHomeResult.NO_PERMISSION);
            }

            Location location = player.getLocation();
            return claimRepository.findByChunk(location.getWorld().getName(),
                    location.getChunk().getX(), location.getChunk().getZ()).thenCompose(claimOpt -> {
                if (claimOpt.isEmpty() || claimOpt.get().getClanId() != member.getClanId()) {
                    return CompletableFuture.completedFuture(SetHomeResult.NOT_IN_CLAIM);
                }
                return clanRepository.updateHome(
                        member.getClanId(),
                        location.getWorld().getName(),
                        location.getX(),
                        location.getY(),
                        location.getZ(),
                        location.getYaw(),
                        location.getPitch()
                ).thenCompose(v -> clanRepository.findById(member.getClanId()).thenApply(clanOpt -> {
                    clanOpt.ifPresent(plugin.getClanService()::cacheClan);
                    return SetHomeResult.SUCCESS;
                }));
            });
        });
    }

    public void teleportHome(Player player) {
        Optional<ClanMember> memberOpt = plugin.getClanService().getCachedMember(player.getUniqueId());
        if (memberOpt.isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return;
        }

        long now = System.currentTimeMillis();
        Long lastUse = cooldowns.get(player.getUniqueId());
        int cooldownSeconds = configManager.getHomeCooldownSeconds();
        if (lastUse != null && now - lastUse < cooldownSeconds * 1000L) {
            long remaining = (cooldownSeconds * 1000L - (now - lastUse)) / 1000;
            plugin.getMessages().send(player, "home.cooldown", Map.of("seconds", String.valueOf(remaining)));
            return;
        }

        plugin.getClanService().getClan(memberOpt.get().getClanId()).thenAccept(clanOpt -> {
            if (clanOpt.isEmpty() || !clanOpt.get().hasHome()) {
                plugin.getMessages().send(player, "home.not-set");
                return;
            }
            startWarmup(player, clanOpt.get());
        });
    }

    private void startWarmup(Player player, Clan clan) {
        cancelWarmup(player.getUniqueId());

        int warmupSeconds = configManager.getHomeWarmupSeconds();
        if (warmupSeconds <= 0) {
            executeTeleport(player, clan);
            return;
        }

        Location start = player.getLocation().clone();
        plugin.getMessages().send(player, "home.warmup", Map.of("seconds", String.valueOf(warmupSeconds)));

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            warmupTasks.remove(player.getUniqueId());
            if (!player.isOnline()) {
                return;
            }
            if (player.getLocation().distanceSquared(start) > 0.5) {
                plugin.getMessages().send(player, "home.warmup-cancelled");
                return;
            }
            executeTeleport(player, clan);
        }, warmupSeconds * 20L);

        warmupTasks.put(player.getUniqueId(), task);
    }

    private void executeTeleport(Player player, Clan clan) {
        Location home = clan.getHomeLocation();
        if (home == null) {
            plugin.getMessages().send(player, "home.not-set");
            return;
        }
        player.teleport(home);
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        plugin.getMessages().send(player, "home.teleport-success");
    }

    public void cancelWarmup(UUID playerUuid) {
        BukkitTask task = warmupTasks.remove(playerUuid);
        if (task != null) {
            task.cancel();
        }
    }
}

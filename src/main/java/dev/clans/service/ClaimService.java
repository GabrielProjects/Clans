package dev.clans.service;

import dev.clans.ClansPlugin;
import dev.clans.config.ConfigManager;
import dev.clans.database.repository.ClaimRepository;
import dev.clans.database.repository.ClanRepository;
import dev.clans.database.repository.MemberRepository;
import dev.clans.integration.WorldGuardIntegration;
import dev.clans.model.ClanClaim;
import dev.clans.model.ClanMember;
import dev.clans.util.ChunkUtils;
import dev.clans.util.MessageUtil;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ClaimService {

    public enum ClaimResult {
        SUCCESS,
        NOT_IN_CLAN,
        NO_PERMISSION,
        WORLD_NOT_ALLOWED,
        ALREADY_CLAIMED,
        LIMIT_REACHED,
        WG_FAILED
    }

    public enum UnclaimResult {
        SUCCESS,
        NOT_IN_CLAN,
        NO_PERMISSION,
        NOT_CLAIMED,
        NOT_OWN_CLAIM
    }

    private final ClansPlugin plugin;
    private final ClaimRepository claimRepository;
    private final MemberRepository memberRepository;
    private final WorldGuardIntegration worldGuardIntegration;
    private final ConfigManager configManager;
    private final PermissionService permissionService;

    private final ConcurrentHashMap<String, Object> claimLocks = new ConcurrentHashMap<>();

    public ClaimService(ClansPlugin plugin, ClaimRepository claimRepository, ClanRepository clanRepository,
                        MemberRepository memberRepository, WorldGuardIntegration worldGuardIntegration,
                        ConfigManager configManager, PermissionService permissionService) {
        this.plugin = plugin;
        this.claimRepository = claimRepository;
        this.memberRepository = memberRepository;
        this.worldGuardIntegration = worldGuardIntegration;
        this.configManager = configManager;
        this.permissionService = permissionService;
    }

    public CompletableFuture<ClaimResult> claim(Player player) {
        return memberRepository.findByPlayer(player.getUniqueId()).thenCompose(memberOpt -> {
            if (memberOpt.isEmpty()) {
                return CompletableFuture.completedFuture(ClaimResult.NOT_IN_CLAN);
            }
            ClanMember member = memberOpt.get();
            if (!permissionService.canClaim(member) && !player.hasPermission("clans.bypass.claim")) {
                return CompletableFuture.completedFuture(ClaimResult.NO_PERMISSION);
            }

            Chunk chunk = player.getLocation().getChunk();
            String worldName = chunk.getWorld().getName();
            if (!configManager.isWorldAllowed(worldName)) {
                return CompletableFuture.completedFuture(ClaimResult.WORLD_NOT_ALLOWED);
            }

            String lockKey = ChunkUtils.chunkKey(chunk);
            Object lock = claimLocks.computeIfAbsent(lockKey, k -> new Object());

            synchronized (lock) {
                return claimRepository.findByChunk(worldName, chunk.getX(), chunk.getZ()).thenCompose(existing -> {
                    if (existing.isPresent()) {
                        claimLocks.remove(lockKey);
                        return CompletableFuture.completedFuture(ClaimResult.ALREADY_CLAIMED);
                    }
                    return claimRepository.countByClan(member.getClanId()).thenCompose(count -> {
                        if (count >= configManager.getMaxClaimsPerClan()) {
                            claimLocks.remove(lockKey);
                            return CompletableFuture.completedFuture(ClaimResult.LIMIT_REACHED);
                        }
                        return memberRepository.findByClan(member.getClanId()).thenCompose(members -> {
                            List<java.util.UUID> uuids = members.stream()
                                    .map(ClanMember::getPlayerUuid)
                                    .toList();
                            return createRegionOnMainThread(member.getClanId(), chunk, uuids).thenCompose(created -> {
                                if (!created) {
                                    return CompletableFuture.completedFuture(ClaimResult.WG_FAILED);
                                }
                                return claimRepository.create(member.getClanId(), worldName, chunk.getX(), chunk.getZ())
                                        .thenApply(id -> ClaimResult.SUCCESS)
                                        .exceptionally(ex -> {
                                            MessageUtil.runSync(() -> worldGuardIntegration.removeClaimRegion(
                                                    new ClanClaim(0, member.getClanId(), worldName, chunk.getX(), chunk.getZ(), java.time.Instant.now())
                                            ));
                                            return ClaimResult.ALREADY_CLAIMED;
                                        });
                            });
                        }).whenComplete((r, e) -> claimLocks.remove(lockKey));
                    });
                });
            }
        });
    }

    private CompletableFuture<Boolean> createRegionOnMainThread(long clanId, Chunk chunk, List<java.util.UUID> memberUuids) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        MessageUtil.runSync(() -> future.complete(worldGuardIntegration.createClaimRegion(clanId, chunk, memberUuids)));
        return future;
    }

    public CompletableFuture<UnclaimResult> unclaim(Player player) {
        return memberRepository.findByPlayer(player.getUniqueId()).thenCompose(memberOpt -> {
            if (memberOpt.isEmpty()) {
                return CompletableFuture.completedFuture(UnclaimResult.NOT_IN_CLAN);
            }
            ClanMember member = memberOpt.get();
            if (!permissionService.canClaim(member) && !player.hasPermission("clans.bypass.claim")) {
                return CompletableFuture.completedFuture(UnclaimResult.NO_PERMISSION);
            }

            Chunk chunk = player.getLocation().getChunk();
            return claimRepository.findByChunk(chunk.getWorld().getName(), chunk.getX(), chunk.getZ()).thenCompose(claimOpt -> {
                if (claimOpt.isEmpty()) {
                    return CompletableFuture.completedFuture(UnclaimResult.NOT_CLAIMED);
                }
                ClanClaim claim = claimOpt.get();
                if (claim.getClanId() != member.getClanId()) {
                    return CompletableFuture.completedFuture(UnclaimResult.NOT_OWN_CLAIM);
                }
                return claimRepository.delete(claim.getWorld(), claim.getChunkX(), claim.getChunkZ()).thenApply(v -> {
                    MessageUtil.runSync(() -> worldGuardIntegration.removeClaimRegion(claim));
                    return UnclaimResult.SUCCESS;
                });
            });
        });
    }

    public CompletableFuture<Optional<ClanClaim>> getClaimAt(Chunk chunk) {
        return claimRepository.findByChunk(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public CompletableFuture<Boolean> isClanClaim(Chunk chunk, long clanId) {
        return getClaimAt(chunk).thenApply(opt -> opt.map(c -> c.getClanId() == clanId).orElse(false));
    }
}

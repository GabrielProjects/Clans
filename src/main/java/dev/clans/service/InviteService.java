package dev.clans.service;

import dev.clans.ClansPlugin;
import dev.clans.database.repository.ClanRepository;
import dev.clans.database.repository.InviteRepository;
import dev.clans.database.repository.MemberRepository;
import dev.clans.integration.WorldGuardIntegration;
import dev.clans.model.ClanInvite;
import dev.clans.model.ClanMember;
import dev.clans.model.ClanRole;
import dev.clans.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class InviteService {

    public enum InviteResult {
        SUCCESS,
        NO_PERMISSION,
        TARGET_IN_CLAN,
        ALREADY_INVITED,
        TARGET_NOT_FOUND,
        ACTOR_NOT_IN_CLAN
    }

    public enum AcceptResult {
        SUCCESS,
        NO_PENDING,
        EXPIRED,
        CLAN_FULL,
        ALREADY_IN_CLAN
    }

    public enum KickResult {
        SUCCESS,
        NO_PERMISSION,
        NOT_MEMBER,
        CANNOT_KICK_LEADER,
        CONFIRM_PENDING,
        CONFIRM_REQUIRED,
        TARGET_NOT_FOUND
    }

    private record PendingKick(UUID targetUuid, long expiresAtMillis) {}

    private final ClansPlugin plugin;
    private final InviteRepository inviteRepository;
    private final MemberRepository memberRepository;
    private final ClanRepository clanRepository;
    private final PermissionService permissionService;
    private final WorldGuardIntegration worldGuardIntegration;

    private final Map<UUID, PendingKick> pendingKicks = new ConcurrentHashMap<>();

    public InviteService(ClansPlugin plugin, InviteRepository inviteRepository, MemberRepository memberRepository,
                         ClanRepository clanRepository, PermissionService permissionService,
                         WorldGuardIntegration worldGuardIntegration) {
        this.plugin = plugin;
        this.inviteRepository = inviteRepository;
        this.memberRepository = memberRepository;
        this.clanRepository = clanRepository;
        this.permissionService = permissionService;
        this.worldGuardIntegration = worldGuardIntegration;
    }

    public CompletableFuture<InviteResult> invite(Player inviter, String targetName) {
        return memberRepository.findByPlayer(inviter.getUniqueId()).thenCompose(inviterMemberOpt -> {
            if (inviterMemberOpt.isEmpty()) {
                return CompletableFuture.completedFuture(InviteResult.ACTOR_NOT_IN_CLAN);
            }
            ClanMember inviterMember = inviterMemberOpt.get();
            if (!permissionService.canInvite(inviterMember)) {
                return CompletableFuture.completedFuture(InviteResult.NO_PERMISSION);
            }

            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                return CompletableFuture.completedFuture(InviteResult.TARGET_NOT_FOUND);
            }

            return memberRepository.findByPlayer(target.getUniqueId()).thenCompose(targetMemberOpt -> {
                if (targetMemberOpt.isPresent()) {
                    return CompletableFuture.completedFuture(InviteResult.TARGET_IN_CLAN);
                }
                return inviteRepository.findPending(inviterMember.getClanId(), target.getUniqueId()).thenCompose(existing -> {
                    if (existing.isPresent()) {
                        return CompletableFuture.completedFuture(InviteResult.ALREADY_INVITED);
                    }
                    Instant expires = Instant.now().plus(plugin.getConfigManager().getInviteExpireMinutes(), ChronoUnit.MINUTES);
                    return inviteRepository.create(inviterMember.getClanId(), inviter.getUniqueId(), target.getUniqueId(), expires)
                            .thenApply(v -> {
                                clanRepository.findById(inviterMember.getClanId()).thenAccept(clanOpt -> clanOpt.ifPresent(clan ->
                                        MessageUtil.runSync(() -> plugin.getMessages().send(target, "invite.received", Map.of(
                                                "inviter", inviter.getName(),
                                                "clan", clan.getName()
                                        )))));
                                return InviteResult.SUCCESS;
                            });
                });
            });
        });
    }

    public CompletableFuture<AcceptResult> accept(Player player) {
        return inviteRepository.findPendingForInvitee(player.getUniqueId()).thenCompose(inviteOpt -> {
            if (inviteOpt.isEmpty()) {
                return CompletableFuture.completedFuture(AcceptResult.NO_PENDING);
            }
            ClanInvite invite = inviteOpt.get();
            if (invite.isExpired()) {
                return inviteRepository.delete(invite.getId()).thenApply(v -> AcceptResult.EXPIRED);
            }

            return memberRepository.findByPlayer(player.getUniqueId()).thenCompose(existing -> {
                if (existing.isPresent()) {
                    return CompletableFuture.completedFuture(AcceptResult.ALREADY_IN_CLAN);
                }
                return memberRepository.countMembers(invite.getClanId()).thenCompose(count -> {
                    if (count >= permissionService.getMaxMembers()) {
                        return CompletableFuture.completedFuture(AcceptResult.CLAN_FULL);
                    }
                    return memberRepository.addMember(invite.getClanId(), player.getUniqueId(), ClanRole.MEMBER)
                            .thenCompose(v -> inviteRepository.deleteForInvitee(player.getUniqueId()))
                            .thenApply(v -> {
                                ClanMember member = new ClanMember(invite.getClanId(), player.getUniqueId(), ClanRole.MEMBER, Instant.now());
                                plugin.getClanService().cacheMember(member);
                                MessageUtil.runSync(() -> worldGuardIntegration.addMemberToClanRegions(invite.getClanId(), player.getUniqueId()));
                                return AcceptResult.SUCCESS;
                            });
                });
            });
        });
    }

    public CompletableFuture<Boolean> deny(Player player) {
        return inviteRepository.findPendingForInvitee(player.getUniqueId()).thenCompose(inviteOpt -> {
            if (inviteOpt.isEmpty()) {
                return CompletableFuture.completedFuture(false);
            }
            return inviteRepository.delete(inviteOpt.get().getId()).thenApply(v -> true);
        });
    }

    public CompletableFuture<KickResult> kick(Player actor, String targetName, boolean confirm) {
        return memberRepository.findByPlayer(actor.getUniqueId()).thenCompose(actorMemberOpt -> {
            if (actorMemberOpt.isEmpty()) {
                return CompletableFuture.completedFuture(KickResult.NO_PERMISSION);
            }
            ClanMember actorMember = actorMemberOpt.get();

            Player targetPlayer = Bukkit.getPlayer(targetName);
            UUID targetUuid;
            if (targetPlayer != null) {
                targetUuid = targetPlayer.getUniqueId();
            } else {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
                if (!offline.hasPlayedBefore()) {
                    return CompletableFuture.completedFuture(KickResult.TARGET_NOT_FOUND);
                }
                targetUuid = offline.getUniqueId();
            }

            final UUID finalTargetUuid = targetUuid;
            return memberRepository.findByPlayer(finalTargetUuid).thenCompose(targetMemberOpt -> {
                if (targetMemberOpt.isEmpty() || targetMemberOpt.get().getClanId() != actorMember.getClanId()) {
                    return CompletableFuture.completedFuture(KickResult.NOT_MEMBER);
                }
                ClanMember targetMember = targetMemberOpt.get();
                if (!permissionService.canKick(actorMember, targetMember)) {
                    if (targetMember.getRole() == ClanRole.LEADER) {
                        return CompletableFuture.completedFuture(KickResult.CANNOT_KICK_LEADER);
                    }
                    return CompletableFuture.completedFuture(KickResult.NO_PERMISSION);
                }

                if (!confirm) {
                    long expires = System.currentTimeMillis() + plugin.getConfigManager().getKickConfirmTimeoutSeconds() * 1000L;
                    pendingKicks.put(actor.getUniqueId(), new PendingKick(finalTargetUuid, expires));
                    return CompletableFuture.completedFuture(KickResult.CONFIRM_PENDING);
                }

                PendingKick pending = pendingKicks.get(actor.getUniqueId());
                if (pending == null || !pending.targetUuid().equals(finalTargetUuid)) {
                    return CompletableFuture.completedFuture(KickResult.CONFIRM_REQUIRED);
                }
                if (System.currentTimeMillis() > pending.expiresAtMillis()) {
                    pendingKicks.remove(actor.getUniqueId());
                    return CompletableFuture.completedFuture(KickResult.CONFIRM_REQUIRED);
                }

                pendingKicks.remove(actor.getUniqueId());
                long clanId = targetMember.getClanId();
                return memberRepository.removeMember(finalTargetUuid).thenApply(v -> {
                    plugin.getClanService().unloadPlayer(finalTargetUuid);
                    MessageUtil.runSync(() -> worldGuardIntegration.removeMemberFromClanRegions(clanId, finalTargetUuid));
                    Player online = Bukkit.getPlayer(finalTargetUuid);
                    if (online != null) {
                        plugin.getMessages().send(online, "kick.kicked");
                    }
                    return KickResult.SUCCESS;
                });
            });
        });
    }

    public void clearPending(UUID playerUuid) {
        pendingKicks.remove(playerUuid);
    }
}

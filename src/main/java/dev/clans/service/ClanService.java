package dev.clans.service;

import dev.clans.ClansPlugin;
import dev.clans.config.ConfigManager;
import dev.clans.database.repository.ClaimRepository;
import dev.clans.database.repository.ClanRepository;
import dev.clans.database.repository.MemberRepository;
import dev.clans.integration.WorldGuardIntegration;
import dev.clans.model.Clan;
import dev.clans.model.ClanClaim;
import dev.clans.model.ClanMember;
import dev.clans.model.ClanRole;
import dev.clans.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ClanService {

    public enum CreateResult {
        SUCCESS,
        ALREADY_IN_CLAN,
        NAME_INVALID,
        TAG_INVALID,
        NAME_TAKEN,
        TAG_TAKEN,
        FAILED
    }

    private final ClansPlugin plugin;
    private final ClanRepository clanRepository;
    private final MemberRepository memberRepository;
    private final ClaimRepository claimRepository;
    private final WorldGuardIntegration worldGuardIntegration;
    private final ConfigManager configManager;

    private final Map<UUID, ClanMember> memberCache = new ConcurrentHashMap<>();
    private final Map<Long, Clan> clanCache = new ConcurrentHashMap<>();

    public ClanService(ClansPlugin plugin, ClanRepository clanRepository, MemberRepository memberRepository,
                       ClaimRepository claimRepository, WorldGuardIntegration worldGuardIntegration) {
        this.plugin = plugin;
        this.clanRepository = clanRepository;
        this.memberRepository = memberRepository;
        this.claimRepository = claimRepository;
        this.worldGuardIntegration = worldGuardIntegration;
        this.configManager = plugin.getConfigManager();
    }

    public CompletableFuture<CreateResult> createClan(Player player, String name, String tag) {
        if (memberCache.containsKey(player.getUniqueId())) {
            return CompletableFuture.completedFuture(CreateResult.ALREADY_IN_CLAN);
        }

        if (!isValidName(name)) {
            return CompletableFuture.completedFuture(CreateResult.NAME_INVALID);
        }
        if (!isValidTag(tag)) {
            return CompletableFuture.completedFuture(CreateResult.TAG_INVALID);
        }

        return clanRepository.findByName(name).thenCompose(nameResult -> {
            if (nameResult.isPresent()) {
                return CompletableFuture.completedFuture(CreateResult.NAME_TAKEN);
            }
            return clanRepository.findByTag(tag).thenCompose(tagResult -> {
                if (tagResult.isPresent()) {
                    return CompletableFuture.completedFuture(CreateResult.TAG_TAKEN);
                }
                return memberRepository.findByPlayer(player.getUniqueId()).thenCompose(existing -> {
                    if (existing.isPresent()) {
                        cacheMember(existing.get());
                        return CompletableFuture.completedFuture(CreateResult.ALREADY_IN_CLAN);
                    }
                    return plugin.getDatabaseManager().supplyAsync(connection -> {
                        try {
                            connection.setAutoCommit(false);
                            long clanId = clanRepository.createSync(connection, name, tag, player.getUniqueId());
                            memberRepository.addMemberSync(connection, clanId, player.getUniqueId(), ClanRole.LEADER);
                            connection.commit();

                            Clan clan = clanRepository.findByIdSync(connection, clanId).orElseThrow();
                            ClanMember member = memberRepository.findByPlayerSync(connection, player.getUniqueId()).orElseThrow();
                            cacheClan(clan);
                            cacheMember(member);
                            return CreateResult.SUCCESS;
                        } catch (Exception e) {
                            try {
                                connection.rollback();
                            } catch (Exception ignored) {
                            }
                            plugin.getLogger().severe("Errore creazione clan: " + e.getMessage());
                            return CreateResult.FAILED;
                        } finally {
                            try {
                                connection.setAutoCommit(true);
                            } catch (Exception ignored) {
                            }
                        }
                    });
                });
            });
        });
    }

    public enum DisbandResult {
        SUCCESS,
        NOT_IN_CLAN,
        NOT_LEADER,
        NOT_YOUR_CLAN,
        CLAN_NOT_FOUND
    }

    public CompletableFuture<DisbandResult> disbandClan(Player player, String targetClan) {
        return getMember(player.getUniqueId()).thenCompose(memberOpt -> {
            if (memberOpt.isEmpty()) {
                return CompletableFuture.completedFuture(DisbandResult.NOT_IN_CLAN);
            }

            ClanMember member = memberOpt.get();
            if (targetClan != null && !targetClan.isBlank()) {
                return resolveClanByNameOrTag(targetClan).thenCompose(clanOpt -> {
                    if (clanOpt.isEmpty()) {
                        return CompletableFuture.completedFuture(DisbandResult.CLAN_NOT_FOUND);
                    }
                    if (clanOpt.get().getId() != member.getClanId()) {
                        return CompletableFuture.completedFuture(DisbandResult.NOT_YOUR_CLAN);
                    }
                    return disbandMemberClan(member);
                });
            }

            if (member.getRole() != ClanRole.LEADER) {
                return CompletableFuture.completedFuture(DisbandResult.NOT_LEADER);
            }
            return disbandMemberClan(member);
        });
    }

    private CompletableFuture<Optional<Clan>> resolveClanByNameOrTag(String value) {
        return clanRepository.findByName(value).thenCompose(nameResult -> {
            if (nameResult.isPresent()) {
                return CompletableFuture.completedFuture(nameResult);
            }
            return clanRepository.findByTag(value);
        });
    }

    private CompletableFuture<DisbandResult> disbandMemberClan(ClanMember member) {
        if (member.getRole() != ClanRole.LEADER) {
            return CompletableFuture.completedFuture(DisbandResult.NOT_LEADER);
        }

        long clanId = member.getClanId();
        return claimRepository.findByClan(clanId).thenCompose(claims -> {
            MessageUtil.runSync(() -> worldGuardIntegration.removeAllClaimRegions(clanId, claims));
            return clanRepository.delete(clanId).thenApply(v -> {
                memberCache.entrySet().stream()
                        .filter(e -> e.getValue().getClanId() == clanId)
                        .map(e -> Bukkit.getPlayer(e.getKey()))
                        .filter(java.util.Objects::nonNull)
                        .forEach(online -> MessageUtil.runSync(() -> {
                            if (plugin.getClanDisplayService() != null) {
                                plugin.getClanDisplayService().clear(online);
                            }
                        }));
                invalidateClanCache(clanId);
                memberCache.entrySet().removeIf(e -> e.getValue().getClanId() == clanId);
                return DisbandResult.SUCCESS;
            });
        });
    }

    public CompletableFuture<Boolean> promote(Player actor, String targetName) {
        return modifyRole(actor, targetName, ClanRole.OFFICER, true);
    }

    public CompletableFuture<Boolean> demote(Player actor, String targetName) {
        return modifyRole(actor, targetName, ClanRole.MEMBER, false);
    }

    private CompletableFuture<Boolean> modifyRole(Player actor, String targetName, ClanRole newRole, boolean promote) {
        return getMember(actor.getUniqueId()).thenCompose(actorMemberOpt -> {
            if (actorMemberOpt.isEmpty() || actorMemberOpt.get().getRole() != ClanRole.LEADER) {
                return CompletableFuture.completedFuture(false);
            }
            ClanMember actorMember = actorMemberOpt.get();

            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                return CompletableFuture.completedFuture(false);
            }

            return memberRepository.findByPlayer(target.getUniqueId()).thenCompose(targetMemberOpt -> {
                if (targetMemberOpt.isEmpty()) {
                    return CompletableFuture.completedFuture(false);
                }
                ClanMember targetMember = targetMemberOpt.get();
                if (targetMember.getClanId() != actorMember.getClanId()) {
                    return CompletableFuture.completedFuture(false);
                }

                if (promote) {
                    if (targetMember.getRole() != ClanRole.MEMBER) {
                        return CompletableFuture.completedFuture(false);
                    }
                    return memberRepository.countOfficers(actorMember.getClanId()).thenCompose(count -> {
                        if (count >= configManager.getMaxOfficers()) {
                            return CompletableFuture.completedFuture(false);
                        }
                        return applyRoleChange(targetMember, newRole);
                    });
                } else {
                    if (targetMember.getRole() != ClanRole.OFFICER) {
                        return CompletableFuture.completedFuture(false);
                    }
                    return applyRoleChange(targetMember, newRole);
                }
            });
        });
    }

    private CompletableFuture<Boolean> applyRoleChange(ClanMember targetMember, ClanRole newRole) {
        return memberRepository.updateRole(targetMember.getPlayerUuid(), newRole).thenApply(v -> {
            ClanMember updated = new ClanMember(targetMember.getClanId(), targetMember.getPlayerUuid(), newRole, targetMember.getJoinedAt());
            cacheMember(updated);
            return true;
        });
    }

    public void sendClanChat(Player sender, String message) {
        Optional<ClanMember> memberOpt = getCachedMember(sender.getUniqueId());
        if (memberOpt.isEmpty()) {
            return;
        }

        getCachedClan(memberOpt.get().getClanId()).ifPresent(clan -> {
            String formatted = configManager.getChatFormat()
                    .replace("{tag}", clan.getTag())
                    .replace("{player}", sender.getName())
                    .replace("{message}", message);

            String colored = MessageUtil.colorize(formatted);
            for (ClanMember member : getOnlineMembers(memberOpt.get().getClanId())) {
                Player online = Bukkit.getPlayer(member.getPlayerUuid());
                if (online != null) {
                    online.sendMessage(colored);
                }
            }
        });
    }

    public List<ClanMember> getOnlineMembers(long clanId) {
        return memberCache.values().stream()
                .filter(m -> m.getClanId() == clanId && Bukkit.getPlayer(m.getPlayerUuid()) != null)
                .collect(Collectors.toList());
    }

    public int countOnlineMembers(long clanId) {
        return (int) memberCache.values().stream()
                .filter(m -> m.getClanId() == clanId && Bukkit.getPlayer(m.getPlayerUuid()) != null)
                .count();
    }

    public CompletableFuture<Void> loadPlayer(UUID playerUuid) {
        return memberRepository.findByPlayer(playerUuid).thenCompose(memberOpt -> {
            if (memberOpt.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            cacheMember(memberOpt.get());
            return clanRepository.findById(memberOpt.get().getClanId()).thenAccept(clanOpt -> clanOpt.ifPresent(this::cacheClan));
        });
    }

    public void unloadPlayer(UUID playerUuid) {
        memberCache.remove(playerUuid);
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && plugin.getClanDisplayService() != null) {
            MessageUtil.runSync(() -> plugin.getClanDisplayService().clear(player));
        }
    }

    public Optional<ClanMember> getCachedMember(UUID playerUuid) {
        ClanMember cached = memberCache.get(playerUuid);
        if (cached != null) {
            return Optional.of(cached);
        }
        return Optional.empty();
    }

    public CompletableFuture<Optional<ClanMember>> getMember(UUID playerUuid) {
        Optional<ClanMember> cached = getCachedMember(playerUuid);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached);
        }
        return memberRepository.findByPlayer(playerUuid).thenApply(opt -> {
            opt.ifPresent(this::cacheMember);
            return opt;
        });
    }

    public Optional<Clan> getCachedClan(long clanId) {
        return Optional.ofNullable(clanCache.get(clanId));
    }

    public CompletableFuture<Optional<Clan>> getClan(long clanId) {
        Optional<Clan> cached = getCachedClan(clanId);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached);
        }
        return clanRepository.findById(clanId).thenApply(opt -> {
            opt.ifPresent(this::cacheClan);
            return opt;
        });
    }

    public CompletableFuture<Optional<Clan>> findClanByName(String name) {
        return clanRepository.findByName(name).thenApply(opt -> {
            opt.ifPresent(this::cacheClan);
            return opt;
        });
    }

    public CompletableFuture<List<ClanMember>> getMembers(long clanId) {
        return memberRepository.findByClan(clanId).thenApply(members -> {
            members.forEach(this::cacheMember);
            return members;
        });
    }

    public void cacheMember(ClanMember member) {
        memberCache.put(member.getPlayerUuid(), member);
        Player player = Bukkit.getPlayer(member.getPlayerUuid());
        if (player != null && plugin.getClanDisplayService() != null) {
            MessageUtil.runSync(() -> plugin.getClanDisplayService().update(player));
        }
    }

    public void cacheClan(Clan clan) {
        clanCache.put(clan.getId(), clan);
    }

    public void invalidateClanCache(long clanId) {
        clanCache.remove(clanId);
    }

    private boolean isValidName(String name) {
        if (name == null) {
            return false;
        }
        int len = name.length();
        return len >= configManager.getNameMinLength()
                && len <= configManager.getNameMaxLength()
                && Pattern.compile(configManager.getNamePattern()).matcher(name).matches();
    }

    private boolean isValidTag(String tag) {
        if (tag == null) {
            return false;
        }
        int len = tag.length();
        return len >= configManager.getTagMinLength()
                && len <= configManager.getTagMaxLength()
                && Pattern.compile(configManager.getTagPattern()).matcher(tag).matches();
    }
}

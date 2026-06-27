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
                            worldGuardIntegration.syncClanMembers(clanId, List.of(player.getUniqueId()));
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

    public CompletableFuture<Boolean> disbandClan(Player player) {
        Optional<ClanMember> memberOpt = getCachedMember(player.getUniqueId());
        if (memberOpt.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        ClanMember member = memberOpt.get();
        if (member.getRole() != ClanRole.LEADER) {
            return CompletableFuture.completedFuture(false);
        }

        long clanId = member.getClanId();
        return claimRepository.findByClan(clanId).thenCompose(claims -> {
            MessageUtil.runSync(() -> worldGuardIntegration.removeAllClaimRegions(clanId, claims));
            return clanRepository.delete(clanId).thenApply(v -> {
                invalidateClanCache(clanId);
                memberCache.entrySet().removeIf(e -> e.getValue().getClanId() == clanId);
                return true;
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
        Optional<ClanMember> actorMember = getCachedMember(actor.getUniqueId());
        if (actorMember.isEmpty() || actorMember.get().getRole() != ClanRole.LEADER) {
            return CompletableFuture.completedFuture(false);
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            return CompletableFuture.completedFuture(false);
        }

        return memberRepository.findByPlayer(target.getUniqueId()).thenCompose(targetMemberOpt -> {
            if (targetMemberOpt.isEmpty()) {
                return CompletableFuture.completedFuture(false);
            }
            ClanMember targetMember = targetMemberOpt.get();
            if (targetMember.getClanId() != actorMember.get().getClanId()) {
                return CompletableFuture.completedFuture(false);
            }

            if (promote) {
                if (targetMember.getRole() != ClanRole.MEMBER) {
                    return CompletableFuture.completedFuture(false);
                }
                return memberRepository.countOfficers(actorMember.get().getClanId()).thenCompose(count -> {
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

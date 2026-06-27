package dev.clans.command;

import dev.clans.ClansPlugin;
import dev.clans.model.Clan;
import dev.clans.model.ClanMember;
import dev.clans.model.ClanRole;
import dev.clans.service.ClaimService;
import dev.clans.service.ClanService;
import dev.clans.service.HomeService;
import dev.clans.service.InviteService;
import dev.clans.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class ClanCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "disband", "invite", "accept", "deny", "kick", "promote", "demote",
            "chat", "claim", "unclaim", "home", "sethome", "info", "help"
    );

    private final ClansPlugin plugin;

    public ClanCommand(ClansPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "disband" -> handleDisband(sender);
            case "invite" -> handleInvite(sender, args);
            case "accept" -> handleAccept(sender);
            case "deny" -> handleDeny(sender);
            case "kick" -> handleKick(sender, args);
            case "promote" -> handlePromote(sender, args);
            case "demote" -> handleDemote(sender, args);
            case "chat", "c" -> handleChat(sender, args);
            case "claim" -> handleClaim(sender);
            case "unclaim" -> handleUnclaim(sender);
            case "home" -> handleHome(sender);
            case "sethome" -> handleSetHome(sender);
            case "info" -> handleInfo(sender, args);
            case "help" -> {
                sendHelp(sender);
                yield true;
            }
            default -> {
                plugin.getMessages().send(sender, "general.unknown-command");
                yield true;
            }
        };
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("clans.create")) {
            plugin.getMessages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 3) {
            plugin.getMessages().send(sender, "general.usage", Map.of("usage", "/clan create <nome> <tag>"));
            return true;
        }

        String name = args[1];
        String tag = args[2];
        plugin.getClanService().createClan(player, name, tag).thenAccept(result -> MessageUtil.runSync(() -> {
            switch (result) {
                case SUCCESS -> plugin.getMessages().send(player, "clan.create-success", Map.of("name", name, "tag", tag));
                case ALREADY_IN_CLAN -> plugin.getMessages().send(player, "clan.already-in-clan");
                case NAME_INVALID, TAG_INVALID -> plugin.getMessages().send(player, result == ClanService.CreateResult.NAME_INVALID ? "clan.name-invalid" : "clan.tag-invalid");
                case NAME_TAKEN -> plugin.getMessages().send(player, "clan.name-taken");
                case TAG_TAKEN -> plugin.getMessages().send(player, "clan.tag-taken");
                default -> plugin.getMessages().send(player, "clan.create-failed");
            }
        }));
        return true;
    }

    private boolean handleDisband(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "general.player-only");
            return true;
        }
        plugin.getClanService().disbandClan(player).thenAccept(success -> MessageUtil.runSync(() -> {
            if (success) {
                plugin.getMessages().send(player, "clan.disband-success");
            } else {
                plugin.getMessages().send(player, "clan.disband-not-leader");
            }
        }));
        return true;
    }

    private boolean handleInvite(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("clans.invite")) {
            plugin.getMessages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessages().send(sender, "general.usage", Map.of("usage", "/clan invite <player>"));
            return true;
        }

        plugin.getInviteService().invite(player, args[1]).thenAccept(result -> MessageUtil.runSync(() -> {
            switch (result) {
                case SUCCESS -> plugin.getMessages().send(player, "invite.sent", Map.of("player", args[1]));
                case NO_PERMISSION -> plugin.getMessages().send(player, "invite.cannot-invite");
                case TARGET_IN_CLAN -> plugin.getMessages().send(player, "invite.target-in-clan");
                case ALREADY_INVITED -> plugin.getMessages().send(player, "invite.already-invited");
                case TARGET_NOT_FOUND -> plugin.getMessages().send(player, "general.player-not-found");
                case ACTOR_NOT_IN_CLAN -> plugin.getMessages().send(player, "clan.not-in-clan");
            }
        }));
        return true;
    }

    private boolean handleAccept(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "general.player-only");
            return true;
        }
        plugin.getInviteService().accept(player).thenAccept(result -> MessageUtil.runSync(() -> {
            switch (result) {
                case SUCCESS -> plugin.getClanService().getMember(player.getUniqueId()).thenAccept(memberOpt ->
                        memberOpt.ifPresent(m -> plugin.getClanService().getClan(m.getClanId()).thenAccept(clanOpt ->
                                clanOpt.ifPresent(clan -> plugin.getMessages().send(player, "invite.accepted", Map.of("clan", clan.getName()))))));
                case NO_PENDING -> plugin.getMessages().send(player, "invite.no-pending");
                case EXPIRED -> plugin.getMessages().send(player, "invite.expired");
                case CLAN_FULL -> plugin.getMessages().send(player, "clan.create-failed");
                case ALREADY_IN_CLAN -> plugin.getMessages().send(player, "clan.already-in-clan");
            }
        }));
        return true;
    }

    private boolean handleDeny(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "general.player-only");
            return true;
        }
        plugin.getInviteService().deny(player).thenAccept(success -> MessageUtil.runSync(() -> {
            if (success) {
                plugin.getMessages().send(player, "invite.denied", Map.of("clan", ""));
            } else {
                plugin.getMessages().send(player, "invite.no-pending");
            }
        }));
        return true;
    }

    private boolean handleKick(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("clans.kick")) {
            plugin.getMessages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessages().send(sender, "general.usage", Map.of("usage", "/clan kick <player> [confirm]"));
            return true;
        }

        boolean confirm = args.length >= 3 && "confirm".equalsIgnoreCase(args[2]);
        String target = args[1];
        plugin.getInviteService().kick(player, target, confirm).thenAccept(result -> MessageUtil.runSync(() -> {
            switch (result) {
                case SUCCESS -> plugin.getMessages().send(player, "kick.success", Map.of("player", target));
                case CONFIRM_PENDING -> plugin.getMessages().send(player, "kick.confirm-pending", Map.of("player", target));
                case CONFIRM_REQUIRED -> plugin.getMessages().send(player, "kick.confirm-expired");
                case NOT_MEMBER -> plugin.getMessages().send(player, "kick.not-member");
                case CANNOT_KICK_LEADER -> plugin.getMessages().send(player, "kick.cannot-kick-leader");
                case TARGET_NOT_FOUND -> plugin.getMessages().send(player, "general.player-not-found");
                default -> plugin.getMessages().send(player, "kick.cannot-kick");
            }
        }));
        return true;
    }

    private boolean handlePromote(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("clans.promote")) {
            plugin.getMessages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessages().send(sender, "general.usage", Map.of("usage", "/clan promote <player>"));
            return true;
        }
        plugin.getClanService().promote(player, args[1]).thenAccept(success -> MessageUtil.runSync(() -> {
            if (success) {
                plugin.getMessages().send(player, "promote.success", Map.of("player", args[1]));
                Player target = Bukkit.getPlayer(args[1]);
                if (target != null) {
                    plugin.getMessages().send(target, "promote.promoted");
                }
            } else {
                plugin.getMessages().send(player, "promote.cannot-promote");
            }
        }));
        return true;
    }

    private boolean handleDemote(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("clans.demote")) {
            plugin.getMessages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessages().send(sender, "general.usage", Map.of("usage", "/clan demote <player>"));
            return true;
        }
        plugin.getClanService().demote(player, args[1]).thenAccept(success -> MessageUtil.runSync(() -> {
            if (success) {
                plugin.getMessages().send(player, "demote.success", Map.of("player", args[1]));
                Player target = Bukkit.getPlayer(args[1]);
                if (target != null) {
                    plugin.getMessages().send(target, "demote.demoted");
                }
            } else {
                plugin.getMessages().send(player, "demote.cannot-demote");
            }
        }));
        return true;
    }

    private boolean handleChat(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "general.player-only");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessages().send(sender, "chat.usage");
            return true;
        }
        if (plugin.getClanService().getCachedMember(player.getUniqueId()).isEmpty()) {
            plugin.getMessages().send(player, "clan.not-in-clan");
            return true;
        }
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (message.isBlank()) {
            plugin.getMessages().send(player, "chat.empty");
            return true;
        }
        plugin.getClanService().sendClanChat(player, message);
        return true;
    }

    private boolean handleClaim(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("clans.claim")) {
            plugin.getMessages().send(sender, "general.no-permission");
            return true;
        }
        plugin.getClaimService().claim(player).thenAccept(result -> MessageUtil.runSync(() -> {
            switch (result) {
                case SUCCESS -> plugin.getMessages().send(player, "claim.success", Map.of(
                        "x", String.valueOf(player.getLocation().getChunk().getX()),
                        "z", String.valueOf(player.getLocation().getChunk().getZ())));
                case NOT_IN_CLAN -> plugin.getMessages().send(player, "clan.not-in-clan");
                case NO_PERMISSION -> plugin.getMessages().send(player, "claim.no-permission");
                case WORLD_NOT_ALLOWED -> plugin.getMessages().send(player, "claim.world-not-allowed");
                case ALREADY_CLAIMED -> plugin.getMessages().send(player, "claim.already-claimed");
                case LIMIT_REACHED -> plugin.getMessages().send(player, "claim.limit-reached",
                        Map.of("max", String.valueOf(plugin.getConfigManager().getMaxClaimsPerClan())));
                case WG_FAILED -> plugin.getMessages().send(player, "clan.create-failed");
            }
        }));
        return true;
    }

    private boolean handleUnclaim(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "general.player-only");
            return true;
        }
        plugin.getClaimService().unclaim(player).thenAccept(result -> MessageUtil.runSync(() -> {
            switch (result) {
                case SUCCESS -> plugin.getMessages().send(player, "claim.unclaim-success", Map.of(
                        "x", String.valueOf(player.getLocation().getChunk().getX()),
                        "z", String.valueOf(player.getLocation().getChunk().getZ())));
                case NOT_IN_CLAN -> plugin.getMessages().send(player, "clan.not-in-clan");
                case NO_PERMISSION -> plugin.getMessages().send(player, "claim.no-permission");
                case NOT_CLAIMED -> plugin.getMessages().send(player, "claim.not-claimed");
                case NOT_OWN_CLAIM -> plugin.getMessages().send(player, "claim.not-in-claim");
            }
        }));
        return true;
    }

    private boolean handleHome(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "general.player-only");
            return true;
        }
        plugin.getHomeService().teleportHome(player);
        return true;
    }

    private boolean handleSetHome(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("clans.sethome")) {
            plugin.getMessages().send(sender, "general.no-permission");
            return true;
        }
        plugin.getHomeService().setHome(player).thenAccept(result -> MessageUtil.runSync(() -> {
            switch (result) {
                case SUCCESS -> plugin.getMessages().send(player, "home.set-success");
                case NOT_IN_CLAN -> plugin.getMessages().send(player, "clan.not-in-clan");
                case NO_PERMISSION -> plugin.getMessages().send(player, "home.no-permission");
                case NOT_IN_CLAIM -> plugin.getMessages().send(player, "home.must-be-in-claim");
            }
        }));
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            plugin.getClanService().findClanByName(args[1]).thenAccept(clanOpt -> MessageUtil.runSync(() ->
                    clanOpt.ifPresentOrElse(clan -> showClanInfo(sender, clan),
                            () -> plugin.getMessages().send(sender, "clan.not-found"))));
            return true;
        }

        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "general.usage", Map.of("usage", "/clan info [clan]"));
            return true;
        }

        plugin.getClanService().getMember(player.getUniqueId()).thenAccept(memberOpt -> MessageUtil.runSync(() -> {
            if (memberOpt.isEmpty()) {
                plugin.getMessages().send(player, "clan.not-in-clan");
                return;
            }
            plugin.getClanService().getClan(memberOpt.get().getClanId()).thenAccept(clanOpt ->
                    clanOpt.ifPresent(clan -> showClanInfo(sender, clan)));
        }));
        return true;
    }

    private void showClanInfo(CommandSender sender, Clan clan) {
        plugin.getMessages().send(sender, "clan.info-header", Map.of("name", clan.getName(), "tag", clan.getTag()));

        OfflinePlayer leader = Bukkit.getOfflinePlayer(clan.getLeaderUuid());
        String leaderName = leader.getName() != null ? leader.getName() : clan.getLeaderUuid().toString();

        plugin.getClaimRepository().countByClan(clan.getId()).thenAccept(claims ->
                plugin.getMemberRepository().countMembers(clan.getId()).thenAccept(members -> MessageUtil.runSync(() -> {
                    plugin.getMessages().send(sender, "clan.info-leader", Map.of("leader", leaderName));
                    plugin.getMessages().send(sender, "clan.info-members", Map.of(
                            "count", String.valueOf(members),
                            "max", String.valueOf(plugin.getConfigManager().getMaxMembers())));
                    plugin.getMessages().send(sender, "clan.info-claims", Map.of(
                            "claims", String.valueOf(claims),
                            "max", String.valueOf(plugin.getConfigManager().getMaxClaimsPerClan())));
                    plugin.getMessages().send(sender, "clan.info-home", Map.of(
                            "status", clan.hasHome()
                                    ? plugin.getMessages().get("clan.home-set")
                                    : plugin.getMessages().get("clan.home-not-set")));

                    plugin.getClanService().getMembers(clan.getId()).thenAccept(memberList -> MessageUtil.runSync(() -> {
                        for (ClanMember member : memberList) {
                            OfflinePlayer offline = Bukkit.getOfflinePlayer(member.getPlayerUuid());
                            String name = offline.getName() != null ? offline.getName() : member.getPlayerUuid().toString();
                            String role = formatRole(member.getRole());
                            plugin.getMessages().send(sender, "clan.info-member-line", Map.of("player", name, "role", role));
                        }
                    }));
                })));
    }

    private String formatRole(ClanRole role) {
        return switch (role) {
            case LEADER -> plugin.getMessages().get("roles.leader");
            case OFFICER -> plugin.getMessages().get("roles.officer");
            case MEMBER -> plugin.getMessages().get("roles.member");
        };
    }

    private void sendHelp(CommandSender sender) {
        plugin.getMessages().send(sender, "help.header");
        List<String> lines = List.of(
                "create <nome> <tag> - Crea un nuovo clan",
                "disband - Sciogli il clan",
                "invite <player> - Invita un giocatore",
                "accept/deny - Gestisci inviti",
                "kick <player> [confirm] - Espelli un membro",
                "promote/demote <player> - Gestisci ruoli",
                "chat <msg> - Chat clan",
                "claim/unclaim - Gestisci territori",
                "sethome/home - Home del clan",
                "info [clan] - Informazioni clan"
        );
        for (String line : lines) {
            String[] parts = line.split(" - ", 2);
            plugin.getMessages().send(sender, "help.line", Map.of(
                    "command", parts[0],
                    "description", parts.length > 1 ? parts[1] : ""
            ));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "invite", "kick", "promote", "demote" -> filterOnlinePlayers(args[1]);
                case "info" -> filterClanNames(args[1]);
                default -> Collections.emptyList();
            };
        }

        if (args.length == 3 && "kick".equals(sub)) {
            return filter(List.of("confirm"), args[2]);
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toList());
    }

    private List<String> filterOnlinePlayers(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toList());
    }

    private List<String> filterClanNames(String input) {
        try {
            List<Clan> clans = plugin.getClanRepository().findAll().get(250, TimeUnit.MILLISECONDS);
            String lower = input.toLowerCase(Locale.ROOT);
            return clans.stream()
                    .map(Clan::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}

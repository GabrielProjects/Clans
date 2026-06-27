package dev.clans.integration;

import dev.clans.ClansPlugin;
import dev.clans.model.Clan;
import dev.clans.model.ClanMember;
import dev.clans.model.ClanRole;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import java.util.Optional;

public final class ClansPlaceholderExpansion extends PlaceholderExpansion {

    private final ClansPlugin plugin;

    public ClansPlaceholderExpansion(ClansPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "clans";
    }

    @Override
    public String getAuthor() {
        return "ClansDev";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return "";
        }

        Optional<ClanMember> memberOpt = plugin.getClanService().getCachedMember(player.getUniqueId());
        if (memberOpt.isEmpty()) {
            return switch (params.toLowerCase()) {
                case "player_clan" -> plugin.getConfigManager().getPlaceholderNoClan();
                case "player_tag" -> plugin.getConfigManager().getPlaceholderNoTag();
                case "player_role" -> "";
                case "clan_members_online" -> "0";
                default -> null;
            };
        }

        ClanMember member = memberOpt.get();
        Optional<Clan> clanOpt = plugin.getClanService().getCachedClan(member.getClanId());

        return switch (params.toLowerCase()) {
            case "player_clan" -> clanOpt.map(Clan::getName).orElse(plugin.getConfigManager().getPlaceholderNoClan());
            case "player_tag" -> clanOpt.map(Clan::getTag).orElse(plugin.getConfigManager().getPlaceholderNoTag());
            case "player_role" -> formatRole(member.getRole());
            case "clan_members_online" -> String.valueOf(plugin.getClanService().countOnlineMembers(member.getClanId()));
            default -> null;
        };
    }

    private String formatRole(ClanRole role) {
        return switch (role) {
            case LEADER -> plugin.getMessages().get("roles.leader");
            case OFFICER -> plugin.getMessages().get("roles.officer");
            case MEMBER -> plugin.getMessages().get("roles.member");
        };
    }
}

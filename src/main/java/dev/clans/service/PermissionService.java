package dev.clans.service;

import dev.clans.config.ConfigManager;
import dev.clans.model.ClanMember;
import dev.clans.model.ClanRole;
import org.bukkit.entity.Player;

public final class PermissionService {

    private final ConfigManager configManager;

    public PermissionService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public boolean isAdmin(Player player) {
        return player.hasPermission("clans.admin");
    }

    public boolean canDisband(ClanMember member) {
        return member.getRole() == ClanRole.LEADER;
    }

    public boolean canInvite(ClanMember member) {
        return member.getRole().isAtLeast(ClanRole.OFFICER);
    }

    public boolean canKick(ClanMember actor, ClanMember target) {
        if (target.getRole() == ClanRole.LEADER) {
            return false;
        }
        if (actor.getRole() == ClanRole.LEADER) {
            return true;
        }
        return actor.getRole() == ClanRole.OFFICER && target.getRole() == ClanRole.MEMBER;
    }

    public boolean canPromote(ClanMember actor) {
        return actor.getRole() == ClanRole.LEADER;
    }

    public boolean canDemote(ClanMember actor, ClanMember target) {
        return actor.getRole() == ClanRole.LEADER && target.getRole() == ClanRole.OFFICER;
    }

    public boolean canClaim(ClanMember member) {
        return member.getRole().isAtLeast(ClanRole.OFFICER);
    }

    public boolean canSetHome(ClanMember member) {
        return member.getRole().isAtLeast(ClanRole.OFFICER);
    }

    public int getMaxOfficers() {
        return configManager.getMaxOfficers();
    }

    public int getMaxMembers() {
        return configManager.getMaxMembers();
    }
}

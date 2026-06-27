package dev.clans.config;

import dev.clans.ClansPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ConfigManager {

    private final ClansPlugin plugin;

    public ConfigManager(ClansPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }

    public String getDbHost() {
        return config().getString("database.host", "localhost");
    }

    public int getDbPort() {
        return config().getInt("database.port", 3306);
    }

    public String getDbName() {
        return config().getString("database.database", "clans");
    }

    public String getDbUsername() {
        return config().getString("database.username", "clans");
    }

    public String getDbPassword() {
        return config().getString("database.password", "");
    }

    public int getDbPoolSize() {
        return config().getInt("database.pool-size", 10);
    }

    public int getMaxClaimsPerClan() {
        return config().getInt("claims.max-per-clan", 10);
    }

    public int getClaimMinY() {
        return config().getInt("claims.min-y", -64);
    }

    public int getClaimMaxY() {
        return config().getInt("claims.max-y", 320);
    }

    public List<String> getAllowedWorlds() {
        return config().getStringList("claims.allowed-worlds");
    }

    public boolean isWorldAllowed(String worldName) {
        List<String> allowed = getAllowedWorlds();
        return allowed.isEmpty() || allowed.contains(worldName);
    }

    public Map<String, String> getClaimFlags() {
        var section = config().getConfigurationSection("claims.flags");
        if (section == null) {
            return Collections.emptyMap();
        }
        return section.getValues(false).entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> String.valueOf(e.getValue())
                ));
    }

    public int getHomeCooldownSeconds() {
        return config().getInt("home.cooldown-seconds", 60);
    }

    public int getHomeWarmupSeconds() {
        return config().getInt("home.warmup-seconds", 3);
    }

    public int getInviteExpireMinutes() {
        return config().getInt("invites.expire-minutes", 5);
    }

    public int getKickConfirmTimeoutSeconds() {
        return config().getInt("kick.confirm-timeout-seconds", 30);
    }

    public int getMaxOfficers() {
        return config().getInt("limits.max-officers", 3);
    }

    public int getNameMinLength() {
        return config().getInt("limits.name-min-length", 3);
    }

    public int getNameMaxLength() {
        return config().getInt("limits.name-max-length", 32);
    }

    public int getTagMinLength() {
        return config().getInt("limits.tag-min-length", 2);
    }

    public int getTagMaxLength() {
        return config().getInt("limits.tag-max-length", 8);
    }

    public String getNamePattern() {
        return config().getString("limits.name-pattern", "^[a-zA-Z0-9_]+$");
    }

    public String getTagPattern() {
        return config().getString("limits.tag-pattern", "^[a-zA-Z0-9]+$");
    }

    public int getMaxMembers() {
        return config().getInt("limits.max-members", 50);
    }

    public String getChatFormat() {
        return config().getString("chat.format", "&8[&6{tag}&8] &7{player}&8: &f{message}");
    }

    public String getPlaceholderNoClan() {
        return config().getString("placeholders.no-clan", "Nessuno");
    }

    public String getPlaceholderNoTag() {
        return config().getString("placeholders.no-tag", "");
    }
}

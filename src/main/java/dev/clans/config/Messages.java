package dev.clans.config;

import dev.clans.ClansPlugin;
import dev.clans.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class Messages {

    private final ClansPlugin plugin;
    private FileConfiguration messages;

    public Messages(ClansPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.messages = YamlConfiguration.loadConfiguration(file);
    }

    public void send(CommandSender sender, String path) {
        send(sender, path, Map.of());
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        String message = get(path, placeholders);
        if (!message.isEmpty()) {
            sender.sendMessage(MessageUtil.colorize(message));
        }
    }

    public String get(String path) {
        return get(path, Map.of());
    }

    public String get(String path, Map<String, String> placeholders) {
        String raw = messages.getString(path, "");
        if (raw.isEmpty()) {
            return "";
        }

        Map<String, String> all = new HashMap<>(placeholders);
        all.putIfAbsent("prefix", messages.getString("prefix", plugin.getConfig().getString("messages.prefix", "")));

        String result = raw;
        for (Map.Entry<String, String> entry : all.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    public String getColored(String path, Map<String, String> placeholders) {
        return MessageUtil.colorize(get(path, placeholders));
    }
}

package dev.clans.service;

import dev.clans.ClansPlugin;
import dev.clans.config.ConfigManager;
import dev.clans.model.Clan;
import dev.clans.model.ClanMember;
import dev.clans.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;

public final class ClanDisplayService {

    private final ClansPlugin plugin;
    private final ClanService clanService;
    private final ConfigManager configManager;

    public ClanDisplayService(ClansPlugin plugin, ClanService clanService) {
        this.plugin = plugin;
        this.clanService = clanService;
        this.configManager = plugin.getConfigManager();
    }

    public void refreshOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clanService.loadPlayer(player.getUniqueId()).thenRun(() -> MessageUtil.runSync(() -> update(player)));
        }
    }

    public void update(Player player) {
        if (!configManager.isDisplayEnabled()) {
            clear(player);
            return;
        }

        Optional<ClanMember> cachedMember = clanService.getCachedMember(player.getUniqueId());
        if (cachedMember.isPresent()) {
            Optional<Clan> cachedClan = clanService.getCachedClan(cachedMember.get().getClanId());
            if (cachedClan.isPresent()) {
                applyTab(player, cachedClan.get().getTag());
                return;
            }
        }

        clanService.getMember(player.getUniqueId()).thenAccept(memberOpt -> {
            if (memberOpt.isEmpty()) {
                MessageUtil.runSync(() -> clear(player));
                return;
            }
            clanService.getClan(memberOpt.get().getClanId()).thenAccept(clanOpt -> MessageUtil.runSync(() -> {
                if (clanOpt.isPresent()) {
                    applyTab(player, clanOpt.get().getTag());
                } else {
                    clear(player);
                }
            }));
        });
    }

    public void clear(Player player) {
        player.playerListName(Component.text(player.getName()));
    }

    public Optional<String> resolveTag(Player player) {
        Optional<ClanMember> member = clanService.getCachedMember(player.getUniqueId());
        if (member.isEmpty()) {
            return Optional.empty();
        }
        return clanService.getCachedClan(member.get().getClanId()).map(Clan::getTag);
    }

    public Component formatGlobalChatMessage(Player player, String tag, Component message) {
        String format = configManager.getGlobalChatFormat();
        int messageIndex = format.indexOf("{message}");
        String prefix = messageIndex >= 0 ? format.substring(0, messageIndex) : format;
        String resolved = prefix
                .replace("{tag}", tag)
                .replace("{player}", player.getName());
        return MessageUtil.toComponent(resolved).append(message);
    }

    private void applyTab(Player player, String tag) {
        String formatted = configManager.getTabFormat()
                .replace("{tag}", tag)
                .replace("{player}", player.getName());
        player.playerListName(MessageUtil.toComponent(formatted));
    }
}

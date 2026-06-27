package dev.clans.listener;

import dev.clans.ClansPlugin;
import dev.clans.config.ConfigManager;
import dev.clans.model.Clan;
import dev.clans.model.ClanMember;
import dev.clans.service.ClanDisplayService;
import dev.clans.util.MessageUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class PlayerChatListener implements Listener {

    private final ClansPlugin plugin;
    private final ConfigManager configManager;
    private final ClanDisplayService displayService;

    public PlayerChatListener(ClansPlugin plugin, ClanDisplayService displayService) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.displayService = displayService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        if (!configManager.isDisplayEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        Optional<String> tag = displayService.resolveTag(player);
        if (tag.isEmpty()) {
            tag = loadTag(player);
        }
        if (tag.isEmpty()) {
            return;
        }

        String clanTag = tag.get();
        event.renderer((source, sourceDisplayName, message, viewer) -> {
            if (!source.getUniqueId().equals(player.getUniqueId())) {
                return sourceDisplayName.append(Component.text(": ")).append(message);
            }
            return displayService.formatGlobalChatMessage(player, clanTag, message);
        });
    }

    private Optional<String> loadTag(Player player) {
        try {
            return plugin.getClanService().getMember(player.getUniqueId())
                    .thenCompose(memberOpt -> {
                        if (memberOpt.isEmpty()) {
                            return java.util.concurrent.CompletableFuture.completedFuture(Optional.<String>empty());
                        }
                        ClanMember member = memberOpt.get();
                        return plugin.getClanService().getClan(member.getClanId())
                                .thenApply(clanOpt -> clanOpt.map(Clan::getTag));
                    })
                    .get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            return Optional.empty();
        } finally {
            MessageUtil.runSync(() -> displayService.update(player));
        }
    }
}

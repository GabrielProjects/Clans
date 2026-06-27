package dev.clans.listener;

import dev.clans.ClansPlugin;
import dev.clans.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PlayerJoinListener implements Listener {

    private final ClansPlugin plugin;

    public PlayerJoinListener(ClansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getClanService().loadPlayer(player.getUniqueId()).thenRun(() ->
                MessageUtil.runSync(() -> {
                    if (plugin.getClanDisplayService() != null) {
                        plugin.getClanDisplayService().update(player);
                    }
                }));
    }
}

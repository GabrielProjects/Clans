package dev.clans.listener;

import dev.clans.ClansPlugin;
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
        plugin.getClanService().loadPlayer(event.getPlayer().getUniqueId());
    }
}

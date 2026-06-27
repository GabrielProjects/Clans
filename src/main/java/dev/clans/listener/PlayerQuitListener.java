package dev.clans.listener;

import dev.clans.service.HomeService;
import dev.clans.service.InviteService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerQuitListener implements Listener {

    private final InviteService inviteService;
    private final HomeService homeService;

    public PlayerQuitListener(InviteService inviteService, HomeService homeService) {
        this.inviteService = inviteService;
        this.homeService = homeService;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        inviteService.clearPending(event.getPlayer().getUniqueId());
        homeService.cancelWarmup(event.getPlayer().getUniqueId());
    }
}

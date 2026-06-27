package dev.clans.service;

import dev.clans.ClansPlugin;
import dev.clans.config.ConfigManager;
import dev.clans.database.repository.ClaimRepository;
import dev.clans.database.repository.ClanRepository;
import dev.clans.database.repository.MemberRepository;
import dev.clans.model.Clan;
import dev.clans.model.ClanClaim;
import dev.clans.model.ClanMember;
import dev.clans.util.ChunkUtils;
import dev.clans.util.MessageUtil;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class ClaimMapService {

    private static final String GRID_SIDE_PADDING = "  ";
    private static final String COMPASS_ROW_PADDING = " ";

    private final ClansPlugin plugin;
    private final ClaimRepository claimRepository;
    private final MemberRepository memberRepository;
    private final ClanRepository clanRepository;
    private final ConfigManager configManager;

    public ClaimMapService(ClansPlugin plugin, ClaimRepository claimRepository,
                           MemberRepository memberRepository, ClanRepository clanRepository) {
        this.plugin = plugin;
        this.claimRepository = claimRepository;
        this.memberRepository = memberRepository;
        this.clanRepository = clanRepository;
        this.configManager = plugin.getConfigManager();
    }

    public CompletableFuture<Void> showMap(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        String world = chunk.getWorld().getName();
        int centerX = chunk.getX();
        int centerZ = chunk.getZ();
        int radius = configManager.getMapRadius();

        return memberRepository.findByPlayer(player.getUniqueId()).thenCompose(memberOpt -> {
            Long ownClanId = memberOpt.map(ClanMember::getClanId).orElse(null);
            return claimRepository.findInArea(
                    world,
                    centerX - radius,
                    centerX + radius,
                    centerZ - radius,
                    centerZ + radius
            ).thenCompose(claims -> loadClanNames(claims).thenAccept(clanNames ->
                    MessageUtil.runSync(() -> renderMap(player, centerX, centerZ, ownClanId, claims, clanNames))));
        });
    }

    private CompletableFuture<Map<Long, String>> loadClanNames(List<ClanClaim> claims) {
        List<Long> clanIds = claims.stream()
                .map(ClanClaim::getClanId)
                .distinct()
                .sorted()
                .toList();

        if (clanIds.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }

        Map<Long, String> clanNames = new LinkedHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Long clanId : clanIds) {
            futures.add(clanRepository.findById(clanId).thenAccept(clanOpt ->
                    clanNames.put(clanId, clanOpt.map(Clan::getName).orElse("Clan #" + clanId))));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenApply(ignored -> clanNames);
    }

    private void renderMap(Player player, int centerX, int centerZ, Long ownClanId,
                           List<ClanClaim> claims, Map<Long, String> clanNames) {
        int radius = configManager.getMapRadius();

        Map<String, Long> chunkToClan = new HashMap<>();
        for (ClanClaim claim : claims) {
            chunkToClan.put(ChunkUtils.chunkKey(claim.getWorld(), claim.getChunkX(), claim.getChunkZ()), claim.getClanId());
        }

        Map<Long, EnemyStyle> enemyStyles = buildEnemyStyles(chunkToClan, ownClanId);
        Set<Long> visibleClanIds = new HashSet<>(chunkToClan.values());
        String world = player.getWorld().getName();
        String compassSpacing = buildCompassSpacing(radius);

        player.sendMessage("");
        plugin.getMessages().send(player, "map.separator");
        plugin.getMessages().send(player, "map.title");
        plugin.getMessages().send(player, "map.direction-north", Map.of("spacing", compassSpacing));

        for (int dz = -radius; dz <= radius; dz++) {
            StringBuilder row = new StringBuilder();
            boolean centerRow = dz == 0;

            row.append(centerRow ? plugin.getMessages().get("map.direction-west") + COMPASS_ROW_PADDING : GRID_SIDE_PADDING);

            for (int dx = -radius; dx <= radius; dx++) {
                boolean isPlayerChunk = dx == 0 && dz == 0;
                row.append(resolveCell(chunkToClan, ownClanId, enemyStyles, world,
                        centerX + dx, centerZ + dz, isPlayerChunk));
                if (dx < radius) {
                    row.append(' ');
                }
            }

            if (centerRow) {
                row.append(COMPASS_ROW_PADDING).append(plugin.getMessages().get("map.direction-east"));
            } else {
                row.append(GRID_SIDE_PADDING);
            }

            player.sendMessage(MessageUtil.colorize(row.toString()));
        }

        plugin.getMessages().send(player, "map.direction-south", Map.of("spacing", compassSpacing));
        sendLegend(player, ownClanId, enemyStyles, clanNames, visibleClanIds);
    }

    private String buildCompassSpacing(int radius) {
        int gridWidth = radius * 2 + 1;
        int spacesBetween = Math.max(0, gridWidth - 1);
        int totalWidth = gridWidth + spacesBetween;
        int padding = GRID_SIDE_PADDING.length() + (radius == 0 ? 0 : COMPASS_ROW_PADDING.length());
        int northPadding = padding + Math.max(0, (totalWidth - 1) / 2);
        return " ".repeat(northPadding);
    }

    private Map<Long, EnemyStyle> buildEnemyStyles(Map<String, Long> chunkToClan, Long ownClanId) {
        List<Long> enemyClanIds = chunkToClan.values().stream()
                .filter(id -> ownClanId == null || !ownClanId.equals(id))
                .distinct()
                .sorted()
                .toList();

        List<String> chars = configManager.getMapEnemyChars();
        List<String> colors = configManager.getMapEnemyColors();
        if (chars.isEmpty()) {
            chars = List.of("#", "@", "$", "%");
        }
        if (colors.isEmpty()) {
            colors = List.of("&c", "&6", "&e", "&d");
        }

        Map<Long, EnemyStyle> styles = new LinkedHashMap<>();
        for (int i = 0; i < enemyClanIds.size(); i++) {
            long clanId = enemyClanIds.get(i);
            String ch = chars.get(Math.min(i, chars.size() - 1));
            String color = colors.get(Math.min(i, colors.size() - 1));
            styles.put(clanId, new EnemyStyle(color, ch));
        }
        return styles;
    }

    private String resolveCell(Map<String, Long> chunkToClan, Long ownClanId,
                             Map<Long, EnemyStyle> enemyStyles, String world,
                             int chunkX, int chunkZ, boolean isPlayerChunk) {
        String key = ChunkUtils.chunkKey(world, chunkX, chunkZ);
        Long clanId = chunkToClan.get(key);

        if (isPlayerChunk) {
            if (clanId == null) {
                return configManager.getMapPlayerColor() + configManager.getMapPlayerChar();
            }
            if (ownClanId != null && ownClanId.equals(clanId)) {
                return configManager.getMapOwnClanColor() + configManager.getMapPlayerChar();
            }
            EnemyStyle style = enemyStyles.get(clanId);
            if (style != null) {
                return style.color() + configManager.getMapPlayerChar();
            }
            return configManager.getMapPlayerColor() + configManager.getMapPlayerChar();
        }

        if (clanId == null) {
            return configManager.getMapUnclaimedColor() + configManager.getMapUnclaimedChar();
        }

        if (ownClanId != null && ownClanId.equals(clanId)) {
            return configManager.getMapOwnClanColor() + configManager.getMapOwnClanChar();
        }

        EnemyStyle style = enemyStyles.get(clanId);
        if (style == null) {
            return "&c#";
        }
        return style.color() + style.symbol();
    }

    private void sendLegend(Player player, Long ownClanId, Map<Long, EnemyStyle> enemyStyles,
                            Map<Long, String> clanNames, Set<Long> visibleClanIds) {
        player.sendMessage("");
        plugin.getMessages().send(player, "map.legend-unclaimed");

        if (ownClanId != null && visibleClanIds.contains(ownClanId)) {
            String ownClanName = clanNames.getOrDefault(ownClanId, "Il tuo clan");
            plugin.getMessages().send(player, "map.legend-own", Map.of("clan", ownClanName));
        }

        for (Map.Entry<Long, EnemyStyle> entry : enemyStyles.entrySet()) {
            if (!visibleClanIds.contains(entry.getKey())) {
                continue;
            }
            String clanName = clanNames.getOrDefault(entry.getKey(), "Clan #" + entry.getKey());
            EnemyStyle style = entry.getValue();
            plugin.getMessages().send(player, "map.legend-clan", Map.of(
                    "color", style.color(),
                    "symbol", style.symbol(),
                    "clan", clanName
            ));
        }
    }

    private record EnemyStyle(String color, String symbol) {}
}

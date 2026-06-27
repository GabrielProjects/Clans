package dev.clans.util;

import org.bukkit.Chunk;

public final class ChunkUtils {

    private ChunkUtils() {
    }

    public static String chunkKey(String world, int chunkX, int chunkZ) {
        return world + ":" + chunkX + ":" + chunkZ;
    }

    public static String chunkKey(Chunk chunk) {
        return chunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }
}

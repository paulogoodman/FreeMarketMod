package com.freemarket.server.data;

import com.freemarket.FreeMarket;
import net.minecraft.server.level.ServerLevel;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages config folder size checking and cleanup for FreeMarket.
 */
public class ConfigFolderManager {
    
    private static final long SIZE_WARNING_THRESHOLD = 100 * 1024 * 1024L; // 100 MB in bytes
    private static boolean configFolderTooLarge = false;
    
    /**
     * Checks the size of the FreeMarket config directories (market and auctions).
     * Sets a flag if the total size exceeds 100 MB.
     * 
     * @param level The server level to check (uses world-specific directory)
     * @return The total size in bytes, or -1 if there was an error
     */
    public static long checkConfigFolderSize(ServerLevel level) {
        try {
            // Use world-specific directory
            Path configDir = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
            Path marketDir = configDir.resolve("freemarket").resolve("market");
            Path auctionsDir = configDir.resolve("freemarket").resolve("auctions");
            
            AtomicLong totalSize = new AtomicLong(0);
            
            // Calculate market directory size
            if (Files.exists(marketDir) && Files.isDirectory(marketDir)) {
                calculateDirectorySize(marketDir, totalSize);
            }
            
            // Calculate auctions directory size
            if (Files.exists(auctionsDir) && Files.isDirectory(auctionsDir)) {
                calculateDirectorySize(auctionsDir, totalSize);
            }
            
            long size = totalSize.get();
            configFolderTooLarge = size > SIZE_WARNING_THRESHOLD;
            
            if (configFolderTooLarge) {
                double sizeMB = size / (1024.0 * 1024.0);
                FreeMarket.LOGGER.warn("WARNING: FreeMarket config folder size ({} MB) exceeds 100 MB threshold!", String.format("%.2f", sizeMB));
                FreeMarket.LOGGER.warn("Consider using /freemarket admin clear_configs to clean up old JSON files.");
            }
            
            return size;
            
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to check config folder size: {}", e.getMessage());
            return -1;
        }
    }
    
    /**
     * Recursively calculates the size of a directory.
     */
    private static void calculateDirectorySize(Path directory, AtomicLong totalSize) {
        try {
            Files.walk(directory)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        long size = Files.size(path);
                        totalSize.addAndGet(size);
                    } catch (Exception e) {
                        FreeMarket.LOGGER.warn("Failed to get size of file {}: {}", path, e.getMessage());
                    }
                });
        } catch (Exception e) {
            FreeMarket.LOGGER.warn("Failed to calculate size of directory {}: {}", directory, e.getMessage());
        }
    }
    
    /**
     * Checks if the config folder is too large.
     * 
     * @return true if the config folder exceeds 100 MB
     */
    public static boolean isConfigFolderTooLarge() {
        return configFolderTooLarge;
    }
    
    /**
     * Clears all JSON files from the market and auctions config directories.
     * 
     * @param level The server level to clear files from (uses world-specific directory)
     * @return The number of files deleted
     */
    public static int clearConfigFiles(ServerLevel level) {
        int deletedCount = 0;
        
        try {
            // Use world-specific directory
            Path configDir = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
            Path marketDir = configDir.resolve("freemarket").resolve("market");
            Path auctionsDir = configDir.resolve("freemarket").resolve("auctions");
            
            // Delete JSON files from market directory
            if (Files.exists(marketDir) && Files.isDirectory(marketDir)) {
                File[] marketFiles = marketDir.toFile().listFiles((dir, name) -> name.endsWith(".json"));
                if (marketFiles != null) {
                    for (File file : marketFiles) {
                        if (file.delete()) {
                            deletedCount++;
                        } else {
                            FreeMarket.LOGGER.warn("Failed to delete file: {}", file.getPath());
                        }
                    }
                }
            }
            
            // Delete JSON files from auctions directory
            if (Files.exists(auctionsDir) && Files.isDirectory(auctionsDir)) {
                File[] auctionFiles = auctionsDir.toFile().listFiles((dir, name) -> name.endsWith(".json"));
                if (auctionFiles != null) {
                    for (File file : auctionFiles) {
                        if (file.delete()) {
                            deletedCount++;
                        } else {
                            FreeMarket.LOGGER.warn("Failed to delete file: {}", file.getPath());
                        }
                    }
                }
            }
            
            // Recheck folder size after cleanup
            checkConfigFolderSize(level);
            
            FreeMarket.LOGGER.info("Cleared {} JSON files from FreeMarket config directories in world {}", deletedCount, level.dimension().location());
            
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to clear config files: {}", e.getMessage());
        }
        
        return deletedCount;
    }
    
    /**
     * Gets the formatted size string for display.
     * 
     * @param sizeBytes Size in bytes
     * @return Formatted string (e.g., "150.5 MB")
     */
    public static String formatSize(long sizeBytes) {
        if (sizeBytes < 1024) {
            return sizeBytes + " B";
        } else if (sizeBytes < 1024 * 1024) {
            return String.format("%.2f KB", sizeBytes / 1024.0);
        } else {
            return String.format("%.2f MB", sizeBytes / (1024.0 * 1024.0));
        }
    }
}


package com.freemarket.server.data;

import com.freemarket.FreeMarket;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Manages pending rewards persistence using world NBT data.
 * Rewards are stored for offline players and distributed when they log in.
 */
public class PendingRewardsManager {
    
    private static final String PENDING_REWARDS_KEY = "freemarket_pending_rewards";
    private static final String REWARDS_LIST_KEY = "rewards";
    private static final String VERSION_KEY = "version";
    private static final String LAST_UPDATED_KEY = "lastUpdated";
    
    /**
     * Gets all pending rewards for a specific player by UUID.
     * Only loads data when explicitly requested.
     */
    public static List<PendingReward> getPlayerRewards(ServerLevel level, String playerUuid) {
        PendingRewardsSavedData savedData = level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(PendingRewardsSavedData::new, PendingRewardsSavedData::load),
            PENDING_REWARDS_KEY
        );
        
        List<PendingReward> playerRewards = new ArrayList<>();
        for (PendingReward reward : savedData.getRewards()) {
            if (reward.getUuid().equals(playerUuid)) {
                playerRewards.add(reward);
            }
        }
        
        return playerRewards;
    }
    
    /**
     * Gets all pending rewards grouped by player UUID.
     */
    public static Map<String, List<PendingReward>> getAllPendingRewards(ServerLevel level) {
        PendingRewardsSavedData savedData = level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(PendingRewardsSavedData::new, PendingRewardsSavedData::load),
            PENDING_REWARDS_KEY
        );
        
        Map<String, List<PendingReward>> rewardsByPlayer = new HashMap<>();
        for (PendingReward reward : savedData.getRewards()) {
            rewardsByPlayer.computeIfAbsent(reward.getUuid(), k -> new ArrayList<>()).add(reward);
        }
        
        return rewardsByPlayer;
    }
    
    /**
     * Adds a pending reward for a player.
     */
    public static void addPendingReward(ServerLevel level, PendingReward reward) {
        PendingRewardsSavedData savedData = level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(PendingRewardsSavedData::new, PendingRewardsSavedData::load),
            PENDING_REWARDS_KEY
        );
        
        savedData.addReward(reward);
        savedData.setDirty();
        
        FreeMarket.LOGGER.info("Added pending reward for player {}: {}", reward.getPlayerName(), reward.getReason());
    }
    
    /**
     * Removes a pending reward (after it has been distributed).
     */
    public static void removePendingReward(ServerLevel level, PendingReward reward) {
        PendingRewardsSavedData savedData = level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(PendingRewardsSavedData::new, PendingRewardsSavedData::load),
            PENDING_REWARDS_KEY
        );
        
        if (savedData.removeReward(reward)) {
            savedData.setDirty();
        }
    }
    
    /**
     * Removes all pending rewards for a specific player.
     */
    public static void clearPlayerRewards(ServerLevel level, String playerUuid) {
        PendingRewardsSavedData savedData = level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(PendingRewardsSavedData::new, PendingRewardsSavedData::load),
            PENDING_REWARDS_KEY
        );
        
        if (savedData.clearPlayerRewards(playerUuid)) {
            savedData.setDirty();
            FreeMarket.LOGGER.info("Cleared all pending rewards for player {}", playerUuid);
        }
    }
    
    /**
     * SavedData implementation for storing pending rewards in world NBT.
     */
    public static class PendingRewardsSavedData extends SavedData {
        private List<PendingReward> rewards = new ArrayList<>();
        private String version = "1.0";
        private long lastUpdated = System.currentTimeMillis();
        
        public PendingRewardsSavedData() {
            // Default constructor
        }
        
        public PendingRewardsSavedData(List<PendingReward> rewards) {
            this.rewards = new ArrayList<>(rewards);
        }
        
        @Override
        public CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
            ListTag rewardsList = new ListTag();
            
            for (PendingReward reward : rewards) {
                CompoundTag rewardTag = new CompoundTag();
                rewardTag.putString("uuid", reward.getUuid());
                rewardTag.putString("playerName", reward.getPlayerName());
                rewardTag.putLong("moneyAmount", reward.getMoneyAmount());
                rewardTag.putString("reason", reward.getReason());
                rewardTag.putLong("timestamp", reward.getTimestamp());
                
                // Save item data if present
                if (reward.hasItem()) {
                    rewardTag.putString("itemId", reward.getItemId());
                    rewardTag.putString("componentData", reward.getComponentData() != null ? reward.getComponentData() : "");
                    rewardTag.putInt("itemCount", reward.getItemCount());
                }
                
                rewardsList.add(rewardTag);
            }
            
            tag.put(REWARDS_LIST_KEY, rewardsList);
            tag.putString(VERSION_KEY, version);
            tag.putLong(LAST_UPDATED_KEY, System.currentTimeMillis());
            
            return tag;
        }
        
        public static PendingRewardsSavedData load(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
            PendingRewardsSavedData data = new PendingRewardsSavedData();
            
            if (tag.contains(REWARDS_LIST_KEY, Tag.TAG_LIST)) {
                ListTag rewardsList = tag.getList(REWARDS_LIST_KEY, Tag.TAG_COMPOUND);
                
                for (int i = 0; i < rewardsList.size(); i++) {
                    CompoundTag rewardTag = rewardsList.getCompound(i);
                    
                    String uuid = rewardTag.getString("uuid");
                    String playerName = rewardTag.getString("playerName");
                    long moneyAmount = rewardTag.getLong("moneyAmount");
                    String reason = rewardTag.getString("reason");
                    long timestamp = rewardTag.getLong("timestamp");
                    
                    PendingReward reward;
                    
                    // Load item data if present
                    if (rewardTag.contains("itemId")) {
                        String itemId = rewardTag.getString("itemId");
                        String componentData = rewardTag.getString("componentData");
                        int itemCount = rewardTag.getInt("itemCount");
                        reward = new PendingReward(uuid, playerName, moneyAmount, itemId, componentData, itemCount, reason, timestamp);
                    } else {
                        // Fallback: old format with ItemStack (for backwards compatibility)
                        reward = new PendingReward(uuid, playerName, moneyAmount, reason);
                    }
                    
                    data.rewards.add(reward);
                }
            }
            
            if (tag.contains(VERSION_KEY)) {
                data.version = tag.getString(VERSION_KEY);
            }
            if (tag.contains(LAST_UPDATED_KEY)) {
                data.lastUpdated = tag.getLong(LAST_UPDATED_KEY);
            }
            
            return data;
        }
        
        public List<PendingReward> getRewards() {
            return new ArrayList<>(rewards);
        }
        
        public void addReward(PendingReward reward) {
            rewards.add(reward);
        }
        
        public boolean removeReward(PendingReward reward) {
            return rewards.remove(reward);
        }
        
        public boolean clearPlayerRewards(String playerUuid) {
            return rewards.removeIf(r -> r.getUuid().equals(playerUuid));
        }
        
        public String getVersion() {
            return version;
        }
        
        public long getLastUpdated() {
            return lastUpdated;
        }
    }
}


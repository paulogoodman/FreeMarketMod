package com.freemarket.server.data;

import com.freemarket.FreeMarket;
import com.freemarket.common.data.PlayerAuction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages auction data persistence using world NBT data.
 * Data is only loaded when needed (render/bid operations) and stored in world save data.
 */
public class AuctionDataManager {
    
    private static final String AUCTION_DATA_KEY = "freemarket_auctions";
    private static final String AUCTIONS_LIST_KEY = "auctions";
    private static final String VERSION_KEY = "version";
    private static final String LAST_UPDATED_KEY = "lastUpdated";
    
    /**
     * Gets the auction data from world save data.
     * Only loads data when explicitly requested.
     */
    public static List<PlayerAuction> loadAuctions(ServerLevel level) {
        AuctionSavedData savedData = level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(AuctionSavedData::new, AuctionSavedData::load),
            AUCTION_DATA_KEY
        );
        
        return savedData.getAuctions();
    }
    
    /**
     * Saves auction data to world save data.
     */
    public static void saveAuctions(ServerLevel level, List<PlayerAuction> auctions) {
        AuctionSavedData savedData = level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(AuctionSavedData::new, AuctionSavedData::load),
            AUCTION_DATA_KEY
        );
        
        savedData.setAuctions(auctions);
        savedData.setDirty();
    }
    
    /**
     * Adds a new auction to world data.
     */
    public static void addAuction(ServerLevel level, PlayerAuction auction) {
        List<PlayerAuction> auctions = loadAuctions(level);
        auctions.add(auction);
        saveAuctions(level, auctions);
    }
    
    /**
     * Removes an auction by ID from world data.
     */
    public static void removeAuction(ServerLevel level, String auctionId) {
        List<PlayerAuction> auctions = loadAuctions(level);
        auctions.removeIf(a -> a.getAuctionId().equals(auctionId));
        saveAuctions(level, auctions);
    }
    
    /**
     * Updates an existing auction in world data.
     */
    public static void updateAuction(ServerLevel level, PlayerAuction updatedAuction) {
        List<PlayerAuction> auctions = loadAuctions(level);
        for (int i = 0; i < auctions.size(); i++) {
            if (auctions.get(i).getAuctionId().equals(updatedAuction.getAuctionId())) {
                auctions.set(i, updatedAuction);
                break;
            }
        }
        saveAuctions(level, auctions);
    }
    
    /**
     * Removes all expired auctions from world data.
     * @return list of expired auctions that were removed
     */
    public static List<PlayerAuction> removeExpiredAuctions(ServerLevel level) {
        List<PlayerAuction> auctions = loadAuctions(level);
        List<PlayerAuction> expired = new ArrayList<>();
        
        auctions.removeIf(auction -> {
            if (auction.isExpired()) {
                expired.add(auction);
                return true;
            }
            return false;
        });
        
        if (!expired.isEmpty()) {
            saveAuctions(level, auctions);
            FreeMarket.LOGGER.info("Removed {} expired auctions from world data", expired.size());
        }
        
        return expired;
    }
    
    /**
     * Generates a unique auction ID.
     */
    public static String generateAuctionId() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Checks if auction data exists for a given world.
     */
    public static boolean hasAuctionData(ServerLevel level) {
        return level.getDataStorage().get(new SavedData.Factory<>(AuctionSavedData::new, AuctionSavedData::load), AUCTION_DATA_KEY) != null;
    }
    
    /**
     * SavedData implementation for storing auction data in world NBT.
     */
    public static class AuctionSavedData extends SavedData {
        private List<PlayerAuction> auctions = new ArrayList<>();
        private String version = "1.0";
        private long lastUpdated = System.currentTimeMillis();
        
        public AuctionSavedData() {
            // Default constructor
        }
        
        public AuctionSavedData(List<PlayerAuction> auctions) {
            this.auctions = new ArrayList<>(auctions);
        }
        
        @Override
        public CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
            ListTag auctionsList = new ListTag();
            
            for (PlayerAuction auction : auctions) {
                CompoundTag auctionTag = new CompoundTag();
                auctionTag.putString("auctionId", auction.getAuctionId());
                auctionTag.putString("itemId", auction.getItemId());
                auctionTag.putString("componentData", auction.getComponentData());
                auctionTag.putInt("quantity", auction.getQuantity());
                auctionTag.putLong("startingPrice", auction.getStartingPrice());
                auctionTag.putLong("currentBid", auction.getCurrentBid());
                auctionTag.putString("sellerUuid", auction.getSellerUuid());
                auctionTag.putString("sellerName", auction.getSellerName());
                auctionTag.putLong("expiryTime", auction.getExpiryTime());
                
                if (auction.getBidderUuid() != null) {
                    auctionTag.putString("bidderUuid", auction.getBidderUuid());
                }
                if (auction.getBidderName() != null) {
                    auctionTag.putString("bidderName", auction.getBidderName());
                }
                
                auctionTag.putLong("createdTime", auction.getCreatedTime());
                auctionsList.add(auctionTag);
            }
            
            tag.put(AUCTIONS_LIST_KEY, auctionsList);
            tag.putString(VERSION_KEY, version);
            tag.putLong(LAST_UPDATED_KEY, System.currentTimeMillis());
            
            return tag;
        }
        
        public static AuctionSavedData load(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
            AuctionSavedData data = new AuctionSavedData();
            
            if (tag.contains(AUCTIONS_LIST_KEY, Tag.TAG_LIST)) {
                ListTag auctionsList = tag.getList(AUCTIONS_LIST_KEY, Tag.TAG_COMPOUND);
                
                for (int i = 0; i < auctionsList.size(); i++) {
                    CompoundTag auctionTag = auctionsList.getCompound(i);
                    
                    PlayerAuction auction = new PlayerAuction();
                    auction.setAuctionId(auctionTag.getString("auctionId"));
                    auction.setItemId(auctionTag.getString("itemId"));
                    auction.setComponentData(auctionTag.getString("componentData"));
                    auction.setQuantity(auctionTag.getInt("quantity"));
                    auction.setStartingPrice(auctionTag.getLong("startingPrice"));
                    auction.setCurrentBid(auctionTag.getLong("currentBid"));
                    auction.setSellerUuid(auctionTag.getString("sellerUuid"));
                    auction.setSellerName(auctionTag.getString("sellerName"));
                    auction.setExpiryTime(auctionTag.getLong("expiryTime"));
                    
                    if (auctionTag.contains("bidderUuid")) {
                        auction.setBidderUuid(auctionTag.getString("bidderUuid"));
                    }
                    if (auctionTag.contains("bidderName")) {
                        auction.setBidderName(auctionTag.getString("bidderName"));
                    }
                    
                    auction.setCreatedTime(auctionTag.getLong("createdTime"));
                    data.auctions.add(auction);
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
        
        public List<PlayerAuction> getAuctions() {
            return new ArrayList<>(auctions);
        }
        
        public void setAuctions(List<PlayerAuction> auctions) {
            this.auctions = new ArrayList<>(auctions);
        }
        
        public String getVersion() {
            return version;
        }
        
        public long getLastUpdated() {
            return lastUpdated;
        }
    }
}
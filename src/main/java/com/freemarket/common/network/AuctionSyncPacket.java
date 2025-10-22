package com.freemarket.common.network;

import com.freemarket.FreeMarket;
import com.freemarket.common.data.PlayerAuction;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Packet for syncing auction data from server to client.
 * Simplified to use custom encoding due to field count limitations.
 */
public record AuctionSyncPacket(List<PlayerAuction> auctions) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<AuctionSyncPacket> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FreeMarket.MODID, "auction_sync"));
    
    // Custom StreamCodec for PlayerAuction
    private static final StreamCodec<ByteBuf, PlayerAuction> AUCTION_CODEC = new StreamCodec<>() {
        @Override
        public PlayerAuction decode(ByteBuf buf) {
            String auctionId = ByteBufCodecs.STRING_UTF8.decode(buf);
            String itemId = ByteBufCodecs.STRING_UTF8.decode(buf);
            String componentData = ByteBufCodecs.STRING_UTF8.decode(buf);
            int quantity = ByteBufCodecs.VAR_INT.decode(buf);
            long startingPrice = ByteBufCodecs.VAR_LONG.decode(buf);
            long currentBid = ByteBufCodecs.VAR_LONG.decode(buf);
            String sellerUuid = ByteBufCodecs.STRING_UTF8.decode(buf);
            String sellerName = ByteBufCodecs.STRING_UTF8.decode(buf);
            long expiryTime = ByteBufCodecs.VAR_LONG.decode(buf);
            boolean hasBidder = buf.readBoolean();
            String bidderUuid = hasBidder ? ByteBufCodecs.STRING_UTF8.decode(buf) : null;
            String bidderName = hasBidder ? ByteBufCodecs.STRING_UTF8.decode(buf) : null;
            long createdTime = ByteBufCodecs.VAR_LONG.decode(buf);
            
            return new PlayerAuction(auctionId, itemId, componentData, quantity, startingPrice, currentBid,
                sellerUuid, sellerName, expiryTime, bidderUuid, bidderName, createdTime);
        }
        
        @Override
        public void encode(ByteBuf buf, PlayerAuction auction) {
            ByteBufCodecs.STRING_UTF8.encode(buf, auction.getAuctionId());
            ByteBufCodecs.STRING_UTF8.encode(buf, auction.getItemId());
            ByteBufCodecs.STRING_UTF8.encode(buf, auction.getComponentData());
            ByteBufCodecs.VAR_INT.encode(buf, auction.getQuantity());
            ByteBufCodecs.VAR_LONG.encode(buf, auction.getStartingPrice());
            ByteBufCodecs.VAR_LONG.encode(buf, auction.getCurrentBid());
            ByteBufCodecs.STRING_UTF8.encode(buf, auction.getSellerUuid());
            ByteBufCodecs.STRING_UTF8.encode(buf, auction.getSellerName());
            ByteBufCodecs.VAR_LONG.encode(buf, auction.getExpiryTime());
            boolean hasBidder = auction.getBidderUuid() != null;
            buf.writeBoolean(hasBidder);
            if (hasBidder) {
                ByteBufCodecs.STRING_UTF8.encode(buf, auction.getBidderUuid());
                ByteBufCodecs.STRING_UTF8.encode(buf, auction.getBidderName());
            }
            ByteBufCodecs.VAR_LONG.encode(buf, auction.getCreatedTime());
        }
    };
    
    public static final StreamCodec<ByteBuf, AuctionSyncPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.collection(ArrayList::new, AUCTION_CODEC),
        AuctionSyncPacket::auctions,
        AuctionSyncPacket::new
    );
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}


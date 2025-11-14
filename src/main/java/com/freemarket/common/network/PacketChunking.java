package com.freemarket.common.network;

import com.freemarket.FreeMarket;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Utility class for handling large packet payloads that exceed the string size limit.
 * Minecraft's ByteBufCodecs.STRING_UTF8 has a maximum length of 32767 characters.
 * This class splits large payloads into chunks and reassembles them on the receiving end.
 */
public class PacketChunking {
    
    /**
     * Maximum string length for a single packet (32767 is the limit for ByteBufCodecs.STRING_UTF8).
     * We use a slightly smaller value to account for overhead and ensure safety.
     */
    private static final int MAX_PACKET_SIZE = 30000;
    
    /**
     * Chunk size for splitting large payloads.
     * Must be significantly smaller than MAX_PACKET_SIZE to account for:
     * 1. JSON metadata overhead (field names, brackets, etc.)
     * 2. JSON string escaping (quotes, backslashes, etc. can expand the string)
     * 3. Safety margin
     * 
     * Using 25000 as a conservative estimate - the iterative adjustment will fine-tune each chunk.
     */
    private static final int CHUNK_SIZE = 25000;
    
    /**
     * Sends a packet, automatically chunking if the payload exceeds the size limit.
     * 
     * @param packetType The original packet type (will be wrapped in chunk packets)
     * @param jsonData The JSON data to send
     * @param sendFunction Function to send a packet (e.g., PacketDistributor.sendToPlayer)
     */
    public static void sendWithChunking(PacketType packetType, String jsonData, Consumer<FreeMarketPacket> sendFunction) {
        if (jsonData == null) {
            jsonData = "";
        }
        
        // If payload is small enough, send normally
        if (jsonData.length() <= MAX_PACKET_SIZE) {
            sendFunction.accept(FreeMarketPacket.withJson(packetType, jsonData));
            return;
        }
        
        // Split data into chunks, accounting for JSON metadata overhead
        // We'll process chunks dynamically to ensure they fit after JSON serialization
        List<ProcessedChunk> processedChunks = new ArrayList<>();
        int offset = 0;
        int chunkIndex = 0;
        
        FreeMarket.LOGGER.debug("Splitting large payload ({} chars) for packet type {}", 
            jsonData.length(), packetType);
        
        // Process chunks dynamically, adjusting sizes to fit after JSON wrapping
        while (offset < jsonData.length()) {
            ProcessedChunk chunk = createChunkThatFits(
                jsonData, offset, chunkIndex, 
                chunkIndex == 0, // isFirst
                packetType, 
                chunkIndex == 0 ? 0 : 0 // totalChunks will be set after we know how many chunks
            );
            
            if (chunk == null) {
                FreeMarket.LOGGER.error("Failed to create chunk {} at offset {}, aborting", chunkIndex, offset);
                return;
            }
            
            processedChunks.add(chunk);
            offset += chunk.actualDataSize;
            chunkIndex++;
        }
        
        // Now we know the total number of chunks, update the first chunk with correct totalChunks
        int totalChunks = processedChunks.size();
        if (!processedChunks.isEmpty()) {
            ProcessedChunk firstChunk = processedChunks.get(0);
            // Rebuild first chunk with correct totalChunks
            JsonObject firstJson = new JsonObject();
            firstJson.addProperty("originalType", packetType.name());
            firstJson.addProperty("totalChunks", totalChunks);
            firstJson.addProperty("chunkIndex", 0);
            firstJson.addProperty("data", firstChunk.data);
            String firstPayload = firstJson.toString();
            
            // Verify it still fits
            if (firstPayload.length() > MAX_PACKET_SIZE) {
                // Need to reduce first chunk data to fit with correct totalChunks
                FreeMarket.LOGGER.warn("First chunk too large with totalChunks={}, reducing data size", totalChunks);
                int excess = firstPayload.length() - MAX_PACKET_SIZE;
                String reducedData = firstChunk.data.substring(0, Math.max(0, firstChunk.data.length() - excess - 50));
                firstJson.addProperty("data", reducedData);
                firstPayload = firstJson.toString();
                
                if (firstPayload.length() > MAX_PACKET_SIZE) {
                    FreeMarket.LOGGER.error("Cannot fit first chunk even after reduction, aborting");
                    return;
                }
                firstChunk.data = reducedData;
                firstChunk.payload = firstPayload;
            } else {
                firstChunk.payload = firstPayload;
            }
        }
        
        // Send all processed chunks
        for (int i = 0; i < processedChunks.size(); i++) {
            ProcessedChunk chunk = processedChunks.get(i);
            PacketType chunkType;
            
            if (i == 0) {
                chunkType = PacketType.CHUNK_START;
            } else if (i == processedChunks.size() - 1) {
                chunkType = PacketType.CHUNK_END;
            } else {
                chunkType = PacketType.CHUNK_DATA;
            }
            
            sendFunction.accept(FreeMarketPacket.withJson(chunkType, chunk.payload));
        }
        
        FreeMarket.LOGGER.debug("Successfully split payload into {} chunks", totalChunks);
    }
    
    /**
     * Sends a chunked packet to a specific player.
     */
    public static void sendToPlayerWithChunking(ServerPlayer player, PacketType packetType, String jsonData) {
        sendWithChunking(packetType, jsonData, packet -> PacketDistributor.sendToPlayer(player, packet));
    }
    
    /**
     * Sends a chunked packet to all players.
     */
    public static void sendToAllPlayersWithChunking(PacketType packetType, String jsonData) {
        sendWithChunking(packetType, jsonData, PacketDistributor::sendToAllPlayers);
    }
    
    /**
     * Sends a chunked packet from client to server.
     */
    public static void sendToServerWithChunking(PacketType packetType, String jsonData) {
        sendWithChunking(packetType, jsonData, PacketDistributor::sendToServer);
    }
    
    /**
     * Helper class to store processed chunk information.
     */
    private static class ProcessedChunk {
        String data;
        String payload;
        int actualDataSize;
        
        ProcessedChunk(String data, String payload, int actualDataSize) {
            this.data = data;
            this.payload = payload;
            this.actualDataSize = actualDataSize;
        }
    }
    
    /**
     * Creates a chunk that fits within the packet size limit after JSON serialization.
     * Iteratively reduces the chunk size until the JSON-wrapped version fits.
     */
    private static ProcessedChunk createChunkThatFits(String jsonData, int offset, int chunkIndex, 
                                                      boolean isFirst, PacketType originalType, int totalChunks) {
        int remainingData = jsonData.length() - offset;
        if (remainingData <= 0) {
            return null;
        }
        
        // Start with a conservative chunk size
        int maxDataSize = CHUNK_SIZE;
        int actualDataSize = Math.min(maxDataSize, remainingData);
        String chunkData = jsonData.substring(offset, offset + actualDataSize);
        
        // Try to find a safe boundary if not the last chunk
        if (offset + actualDataSize < jsonData.length()) {
            int safeBoundary = findSafeBoundary(jsonData, offset, offset + actualDataSize);
            if (safeBoundary > offset) {
                actualDataSize = safeBoundary - offset;
                chunkData = jsonData.substring(offset, offset + actualDataSize);
            }
        }
        
        // Build JSON and iteratively reduce if needed
        int maxAttempts = 20;
        String chunkPayload = null;
        
        while (maxAttempts > 0 && actualDataSize > 0) {
            // Get the actual chunk data for this attempt
            chunkData = jsonData.substring(offset, offset + actualDataSize);
            
            // Build JSON payload
            JsonObject chunkJson = new JsonObject();
            // Include originalType in all chunks so they can determine packet direction
            chunkJson.addProperty("originalType", originalType.name());
            if (isFirst) {
                chunkJson.addProperty("totalChunks", totalChunks); // Will be updated later if needed
            }
            chunkJson.addProperty("chunkIndex", chunkIndex);
            chunkJson.addProperty("data", chunkData);
            chunkPayload = chunkJson.toString();
            
            // Check if it fits
            if (chunkPayload.length() <= MAX_PACKET_SIZE) {
                // Success! This chunk fits
                return new ProcessedChunk(chunkData, chunkPayload, actualDataSize);
            }
            
            // Too large - reduce chunk size
            int excess = chunkPayload.length() - MAX_PACKET_SIZE;
            // Reduce by excess plus 20% buffer for escaping variations
            int reduction = excess + (excess / 5);
            actualDataSize = Math.max(0, actualDataSize - reduction);
            maxAttempts--;
        }
        
        // If we couldn't make it fit, return null
        if (chunkPayload == null || chunkPayload.length() > MAX_PACKET_SIZE) {
            FreeMarket.LOGGER.error("Failed to create chunk {} that fits (attempted size: {} chars)", 
                chunkIndex, actualDataSize);
            return null;
        }
        
        return new ProcessedChunk(chunkData, chunkPayload, actualDataSize);
    }
    
    /**
     * Finds a safe boundary to split the string, avoiding breaking UTF-8 sequences or JSON structures.
     */
    private static int findSafeBoundary(String data, int start, int end) {
        // Look backwards from end for a safe character
        for (int i = end - 1; i >= start + (CHUNK_SIZE * 9 / 10); i--) {
            char c = data.charAt(i);
            // Safe characters: newline, comma, closing brace/bracket, quote (if properly escaped)
            if (c == '\n' || c == ',' || c == '}' || c == ']' || c == '"') {
                // Make sure we're not in the middle of an escape sequence
                if (i > 0 && data.charAt(i - 1) == '\\') {
                    continue;
                }
                return i + 1;
            }
        }
        // If no safe boundary found, split at the original end
        return end;
    }
}


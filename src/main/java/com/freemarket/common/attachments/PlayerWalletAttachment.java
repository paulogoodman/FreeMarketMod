package com.freemarket.common.attachments;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Player wallet attachment for persistent money storage across deaths.
 * Uses NeoForge Data Attachments system for proper persistence.
 */
public class PlayerWalletAttachment {
    
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = 
        DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES, "freemarket");
    
    // Codec for serialization
    public static final Codec<PlayerWalletAttachment> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.LONG.fieldOf("balance").forGetter(PlayerWalletAttachment::getBalance)
        ).apply(instance, PlayerWalletAttachment::new)
    );
    
    // Register the attachment type
    public static final Supplier<AttachmentType<PlayerWalletAttachment>> WALLET = ATTACHMENT_TYPES.register(
        "wallet", 
        () -> AttachmentType.builder(() -> new PlayerWalletAttachment(0))
            .serialize(CODEC)
            .copyOnDeath() // Automatically copy wallet data on death
            .build()
    );
    
    private long balance;
    
    public PlayerWalletAttachment(long balance) {
        this.balance = balance;
    }
    
    public long getBalance() {
        return balance;
    }
    
    public void setBalance(long balance) {
        this.balance = balance;
    }
    
    public void addBalance(long amount) {
        this.balance += amount;
    }
    
    public boolean removeBalance(long amount) {
        if (this.balance >= amount) {
            this.balance -= amount;
            return true;
        }
        return false;
    }
    
    public boolean hasEnoughBalance(long amount) {
        return this.balance >= amount;
    }
}

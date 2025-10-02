package com.servershop;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Semi-transparent dark overlay GUI for the ServerShop mod.
 * Opens with the O keybind and displays a dark overlay with no buttons or items.
 */
public class ShopGuiScreen extends Screen {
    
    public ShopGuiScreen() {
        super(Component.translatable("gui.servershop.shop.title"));
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Render semi-transparent dark background overlay
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        
        // Draw a semi-transparent dark rectangle covering most of the screen
        int overlayAlpha = 150; // Semi-transparent (0-255)
        guiGraphics.fill(0, 0, this.width, this.height, (overlayAlpha << 24) | 0x000000);
        
        // Draw title at the top center
        int titleWidth = this.font.width(this.title);
        int titleX = (this.width - titleWidth) / 2;
        int titleY = 20;
        guiGraphics.drawString(this.font, this.title, titleX, titleY, 0xFFFFFF);
        
        // Draw instruction text
        Component instruction = Component.translatable("gui.servershop.shop.instruction");
        int instructionWidth = this.font.width(instruction);
        int instructionX = (this.width - instructionWidth) / 2;
        int instructionY = titleY + 30;
        guiGraphics.drawString(this.font, instruction, instructionX, instructionY, 0xCCCCCC);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false; // Don't pause the game when GUI is open
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return true; // Close GUI when ESC is pressed
    }
    
    @Override
    public void onClose() {
        super.onClose();
    }
}

package com.servershop.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

import com.servershop.ServerShop;
import com.servershop.common.data.MarketplaceItem;

/**
 * Popup screen for adding items to the marketplace.
 * This screen appears when the '+' button is clicked in the main shop GUI.
 */
public class AddItemPopupScreen extends Screen {
    
    private final ShopGuiScreen parentScreen;
    private EditBox itemNameBox;
    private EditBox buyPriceBox;
    private EditBox sellPriceBox;
    private EditBox quantityBox;
    private Button addButton;
    private Button cancelButton;
    
    // Sample items for demonstration
    private final List<ItemStack> sampleItems = new ArrayList<>();
    private int selectedItemIndex = 0;
    
    public AddItemPopupScreen(ShopGuiScreen parent) {
        super(Component.translatable("gui.servershop.add_item.title"));
        this.parentScreen = parent;
        
        // Initialize sample items
        initializeSampleItems();
    }
    
    private void initializeSampleItems() {
        sampleItems.add(new ItemStack(Items.DIAMOND));
        sampleItems.add(new ItemStack(Items.EMERALD));
        sampleItems.add(new ItemStack(Items.GOLD_INGOT));
        sampleItems.add(new ItemStack(Items.IRON_INGOT));
        sampleItems.add(new ItemStack(Items.COAL));
        sampleItems.add(new ItemStack(Items.REDSTONE));
        sampleItems.add(new ItemStack(Items.LAPIS_LAZULI));
        sampleItems.add(new ItemStack(Items.QUARTZ));
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Calculate popup dimensions and position (centered)
        int popupWidth = 300;
        int popupHeight = 200;
        int popupX = (this.width - popupWidth) / 2;
        int popupY = (this.height - popupHeight) / 2;
        
        // Item name input
        this.itemNameBox = new EditBox(this.font, popupX + 20, popupY + 40, 120, 20, 
            Component.translatable("gui.servershop.add_item.item_name"));
        this.itemNameBox.setValue(sampleItems.get(selectedItemIndex).getItem().getDescription().getString());
        this.addRenderableWidget(this.itemNameBox);
        
        // Buy price input
        this.buyPriceBox = new EditBox(this.font, popupX + 20, popupY + 70, 120, 20, 
            Component.translatable("gui.servershop.add_item.buy_price"));
        this.buyPriceBox.setValue("100");
        this.addRenderableWidget(this.buyPriceBox);
        
        // Sell price input
        this.sellPriceBox = new EditBox(this.font, popupX + 20, popupY + 100, 120, 20, 
            Component.translatable("gui.servershop.add_item.sell_price"));
        this.sellPriceBox.setValue("80");
        this.addRenderableWidget(this.sellPriceBox);
        
        // Quantity input
        this.quantityBox = new EditBox(this.font, popupX + 20, popupY + 130, 120, 20, 
            Component.translatable("gui.servershop.add_item.quantity"));
        this.quantityBox.setValue("1");
        this.addRenderableWidget(this.quantityBox);
        
        // Item selection buttons
        Button prevItemButton = Button.builder(
            Component.literal("◀"),
            button -> selectPreviousItem()
        ).bounds(popupX + 160, popupY + 40, 20, 20).build();
        this.addRenderableWidget(prevItemButton);
        
        Button nextItemButton = Button.builder(
            Component.literal("▶"),
            button -> selectNextItem()
        ).bounds(popupX + 200, popupY + 40, 20, 20).build();
        this.addRenderableWidget(nextItemButton);
        
        // Add button
        this.addButton = Button.builder(
            Component.translatable("gui.servershop.add_item.add"),
            button -> addItemToList()
        ).bounds(popupX + 20, popupY + 170, 80, 20).build();
        this.addRenderableWidget(this.addButton);
        
        // Cancel button
        this.cancelButton = Button.builder(
            Component.translatable("gui.servershop.add_item.cancel"),
            button -> onClose()
        ).bounds(popupX + 120, popupY + 170, 80, 20).build();
        this.addRenderableWidget(this.cancelButton);
    }
    
    private void selectPreviousItem() {
        selectedItemIndex = (selectedItemIndex - 1 + sampleItems.size()) % sampleItems.size();
        updateItemName();
    }
    
    private void selectNextItem() {
        selectedItemIndex = (selectedItemIndex + 1) % sampleItems.size();
        updateItemName();
    }
    
    private void updateItemName() {
        this.itemNameBox.setValue(sampleItems.get(selectedItemIndex).getItem().getDescription().getString());
    }
    
    private void addItemToList() {
        // For now, just log the item being added
        String itemName = this.itemNameBox.getValue();
        String buyPrice = this.buyPriceBox.getValue();
        String sellPrice = this.sellPriceBox.getValue();
        String quantity = this.quantityBox.getValue();
        
        ServerShop.LOGGER.info("Adding item to marketplace: {} - Buy: {} - Sell: {} - Quantity: {}", 
            itemName, buyPrice, sellPrice, quantity);
        
        // TODO: Add to actual marketplace list
        // For now, just close the popup
        onClose();
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Draw semi-transparent background
        int overlayAlpha = 200; // More opaque than main screen
        guiGraphics.fill(0, 0, this.width, this.height, (overlayAlpha << 24) | 0x000000);
        
        // Calculate popup dimensions and position
        int popupWidth = 300;
        int popupHeight = 220;
        int popupX = (this.width - popupWidth) / 2;
        int popupY = (this.height - popupHeight) / 2;
        
        // Draw popup background
        guiGraphics.fill(popupX, popupY, popupX + popupWidth, popupY + popupHeight, 0xFF2C2C2C);
        guiGraphics.fill(popupX + 1, popupY + 1, popupX + popupWidth - 1, popupY + popupHeight - 1, 0xFF404040);
        
        // Draw popup border
        guiGraphics.fill(popupX, popupY, popupX + popupWidth, popupY + 1, 0xFF808080);
        guiGraphics.fill(popupX, popupY, popupX + 1, popupY + popupHeight, 0xFF808080);
        guiGraphics.fill(popupX + popupWidth - 1, popupY, popupX + popupWidth, popupY + popupHeight, 0xFF808080);
        guiGraphics.fill(popupX, popupY + popupHeight - 1, popupX + popupWidth, popupY + popupHeight, 0xFF808080);
        
        // Render all widgets first
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // Draw title (after widgets to ensure it's on top)
        int titleWidth = this.font.width(this.title);
        int titleX = popupX + (popupWidth - titleWidth) / 2;
        guiGraphics.drawString(this.font, this.title, titleX, popupY + 10, 0xFFFFFF);
        
        // Draw labels (after widgets to ensure they're on top)
        guiGraphics.drawString(this.font, Component.translatable("gui.servershop.add_item.item_name"), 
            popupX + 20, popupY + 30, 0xCCCCCC);
        guiGraphics.drawString(this.font, Component.translatable("gui.servershop.add_item.buy_price"), 
            popupX + 20, popupY + 60, 0xCCCCCC);
        guiGraphics.drawString(this.font, Component.translatable("gui.servershop.add_item.sell_price"), 
            popupX + 20, popupY + 90, 0xCCCCCC);
        guiGraphics.drawString(this.font, Component.translatable("gui.servershop.add_item.quantity"), 
            popupX + 20, popupY + 120, 0xCCCCCC);
        
        // Draw item icon (after widgets to ensure it's on top)
        ItemStack currentItem = sampleItems.get(selectedItemIndex);
        guiGraphics.renderItem(currentItem, popupX + 160, popupY + 70);
        guiGraphics.renderItemDecorations(this.font, currentItem, popupX + 160, popupY + 70);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
    
    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parentScreen);
    }
}

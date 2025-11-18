package com.freemarket.client.gui.marketUI;

import com.freemarket.client.gui.commonUI.FreeMarketGuiScreen;
import com.freemarket.client.gui.commonUI.MoneyFormatter;
import com.freemarket.client.gui.commonUI.PopupOverlay;
import com.freemarket.common.data.FreeMarketItem;
import com.freemarket.common.network.FreeMarketPacket;
import com.freemarket.common.network.PacketType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Popup overlay that confirms marketplace purchases and lets players pick a quantity.
 */
public class BuyConfirmationPopupOverlay extends PopupOverlay {

    private static final int POPUP_WIDTH = 420;
    private static final int POPUP_HEIGHT = 320;

    private final FreeMarketItem marketItem;
    private final FreeMarketGuiScreen parentScreen;
    private final FreeMarketContainer sourceContainer;

    private int currentQuantity = 1;
    
    private long cachedBalance;
    
    public BuyConfirmationPopupOverlay(FreeMarketItem marketItem, FreeMarketGuiScreen parentScreen, FreeMarketContainer sourceContainer) {
        super(0, 0, POPUP_WIDTH, POPUP_HEIGHT);
        this.marketItem = marketItem;
        this.parentScreen = parentScreen;
        this.sourceContainer = sourceContainer;
        this.cachedBalance = parentScreen != null ? parentScreen.getCachedBalance() : com.freemarket.client.handlers.ClientWalletHandler.getPlayerMoney();
    }

    @Override
    public void show() {
        super.show();

        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        this.x = (screenWidth - POPUP_WIDTH) / 2;
        this.y = (screenHeight - POPUP_HEIGHT) / 2;
        this.currentQuantity = 1;
    }

    @Override
    protected Component getTitle() {
        return Component.literal("Confirm Purchase");
    }

    @Override
    protected void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (parentScreen != null) {
            cachedBalance = parentScreen.getCachedBalance();
        }
        renderItemInfo(guiGraphics, mouseX, mouseY);
        renderQuantitySection(guiGraphics, mouseX, mouseY);
        renderSummary(guiGraphics);
        renderButtons(guiGraphics, mouseX, mouseY);
    }

    private void renderItemInfo(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();

        int iconSize = 48;
        int iconX = x + (POPUP_WIDTH - iconSize) / 2;
        int iconY = y + 60;

        // Create item stack with component data and set stack count
        ItemStack stack = createItemWithComponentData(marketItem);
        stack.setCount(marketItem.getStackSize());

        // Check if mouse is hovering over the item icon for tooltip
        boolean isHovered = mouseX >= iconX && mouseX <= iconX + iconSize &&
                           mouseY >= iconY && mouseY <= iconY + iconSize;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(iconX + iconSize / 2F, iconY + iconSize / 2F, 0);
        float scale = (float) iconSize / 16F;
        guiGraphics.pose().scale(scale, scale, scale);
        guiGraphics.renderItem(stack, -8, -8);
        guiGraphics.renderItemDecorations(minecraft.font, stack, -8, -8);
        guiGraphics.pose().popPose();

        // Render tooltip if hovering (with higher z-level to appear above item decorations)
        if (isHovered) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 6000); // Higher z-level than popup (5000) and item decorations
            guiGraphics.renderTooltip(minecraft.font, stack, mouseX, mouseY);
            guiGraphics.pose().popPose();
        }

        Component itemName = stack.getHoverName();
        int nameWidth = minecraft.font.width(itemName);
        guiGraphics.drawString(minecraft.font, itemName, x + (POPUP_WIDTH - nameWidth) / 2, iconY + iconSize + 6, TEXT_PRIMARY);

        String priceText = "Price per order: $" + marketItem.getBuyPrice();
        int priceWidth = minecraft.font.width(priceText);
        guiGraphics.drawString(minecraft.font, priceText, x + (POPUP_WIDTH - priceWidth) / 2, iconY + iconSize + 22, SUCCESS_COLOR);
    }
    
    /**
     * Creates an ItemStack with component data applied from the marketplace item.
     */
    private ItemStack createItemWithComponentData(FreeMarketItem item) {
        ItemStack baseItemStack = item.getItemStack().copy();
        
        // Apply component data if present
        String componentData = item.getComponentData();
        
        if (componentData != null && !componentData.trim().isEmpty() && !componentData.equals("{}")) {
            // Try to use server-side processing for proper registry access
            Minecraft minecraft = Minecraft.getInstance();
            var singleplayerServer = minecraft.getSingleplayerServer();
            
            if (singleplayerServer != null) {
                // Use server-side handler with registry access
                return com.freemarket.server.handlers.ServerItemHandler.createItemWithComponentData(
                    baseItemStack, componentData, singleplayerServer);
            } else {
                // Fallback to client-side processing
                com.freemarket.common.attachments.ItemComponentHandler.applyComponentData(baseItemStack, componentData);
                return baseItemStack;
            }
        }
        
        return baseItemStack;
    }

    private void renderQuantitySection(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();

        int sectionX = x + 40;
        int sectionY = y + 150;
        int sectionWidth = POPUP_WIDTH - 80;
        int sectionHeight = 50;

        guiGraphics.fill(sectionX, sectionY, sectionX + sectionWidth, sectionY + sectionHeight, 0x99000000);

        String quantityLabel = "Quantity";
        int labelWidth = minecraft.font.width(quantityLabel);
        guiGraphics.drawString(minecraft.font, quantityLabel, x + (POPUP_WIDTH - labelWidth) / 2, sectionY + 6, TEXT_PRIMARY);

        int plusMinusWidth = 24;
        int plusMinusHeight = 22;
        int spacing = 6;
        int labelWidth_px = 80; // Width for the quantity label
        int labelX = x + (POPUP_WIDTH - labelWidth_px - (plusMinusWidth * 2) - (spacing * 2)) / 2;
        int labelY = y + 175;
        int buttonY = labelY;

        // Render quantity as a label (centered text in a box)
        String quantityText = String.valueOf(currentQuantity);
        int quantityTextWidth = minecraft.font.width(quantityText);
        int quantityTextX = labelX + (labelWidth_px - quantityTextWidth) / 2;
        guiGraphics.fill(labelX, labelY, labelX + labelWidth_px, labelY + plusMinusHeight, 0x99505050);
        guiGraphics.drawString(minecraft.font, quantityText, quantityTextX, labelY + 6, TEXT_PRIMARY);

        // Minus button on the left
        int minusX = labelX - plusMinusWidth - spacing;
        boolean minusHovered = mouseX >= minusX && mouseX <= minusX + plusMinusWidth && mouseY >= buttonY && mouseY <= buttonY + plusMinusHeight;
        guiGraphics.fill(minusX, buttonY, minusX + plusMinusWidth, buttonY + plusMinusHeight, minusHovered ? 0xCC505050 : 0x99505050);
        guiGraphics.drawString(minecraft.font, "-", minusX + 9, buttonY + 6, TEXT_PRIMARY);

        // Plus button on the right
        int plusX = labelX + labelWidth_px + spacing;
        boolean plusHovered = mouseX >= plusX && mouseX <= plusX + plusMinusWidth && mouseY >= buttonY && mouseY <= buttonY + plusMinusHeight;
        guiGraphics.fill(plusX, buttonY, plusX + plusMinusWidth, buttonY + plusMinusHeight, plusHovered ? 0xCC505050 : 0x99505050);
        guiGraphics.drawString(minecraft.font, "+", plusX + 8, buttonY + 6, TEXT_PRIMARY);
    }

    private void renderSummary(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();

        // Position summary section with proper spacing from quantity section (ends at y + 200)
        int summaryY = y + 210;

        long totalCost = getTotalCost();
        String totalText = "Total: $" + MoneyFormatter.formatWithSuffix(totalCost);
        int totalWidth = minecraft.font.width(totalText);
        guiGraphics.drawString(minecraft.font, totalText, x + (POPUP_WIDTH - totalWidth) / 2, summaryY, SUCCESS_COLOR);

        String balanceText = "Balance: $" + MoneyFormatter.formatWithSuffix(cachedBalance);
        int balanceWidth = minecraft.font.width(balanceText);
        guiGraphics.drawString(minecraft.font, balanceText, x + (POPUP_WIDTH - balanceWidth) / 2, summaryY + 14, TEXT_SECONDARY);
        
        // Show inventory space warning if needed (positioned above buttons to avoid overlap)
        // Buttons start at y + 275, so warning at y + 248 gives 27px gap
        String inventoryWarning = getInventorySpaceWarning();
        if (inventoryWarning != null) {
            int warningY = summaryY + 28;
            // Ensure warning doesn't overlap with buttons (buttons start at y + 275)
            if (warningY + 14 > y + POPUP_HEIGHT - 45) {
                warningY = y + POPUP_HEIGHT - 45 - 14; // Position just above buttons
            }
            int warningWidth = minecraft.font.width(inventoryWarning);
            guiGraphics.drawString(minecraft.font, inventoryWarning, x + (POPUP_WIDTH - warningWidth) / 2, warningY, WARNING_COLOR);
        }
    }
    
    /**
     * Calculates inventory space and returns a warning message if shulker boxes will be used.
     * PERFORMANCE: Only checks shulker boxes if main inventory is insufficient.
     */
    private String getInventorySpaceWarning() {
        Minecraft minecraft = Minecraft.getInstance();
        net.minecraft.world.entity.player.Player player = minecraft.player;
        if (player == null) {
            return null;
        }
        
        int quantity = getQuantity();
        if (quantity <= 0) {
            return null;
        }
        
        // Calculate total items needed
        int totalItemsNeeded = quantity * Math.max(1, marketItem.getStackSize());
        
        // Create item template for checking (only once)
        ItemStack itemTemplate = createItemWithComponentData(marketItem);
        itemTemplate.setCount(1);
        
        // Calculate available space in main inventory
        var inventory = player.getInventory();
        final int MAIN_INVENTORY_SIZE = 36;
        int stackSize = itemTemplate.getMaxStackSize();
        boolean isStackable = stackSize > 1;
        
        int mainInventorySpace = 0;
        int emptySlots = 0;
        
        // PERFORMANCE: Single pass through inventory
        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
            ItemStack slotItem = inventory.getItem(i);
            if (slotItem.isEmpty()) {
                emptySlots++;
                if (isStackable) {
                    mainInventorySpace += stackSize;
                } else {
                    mainInventorySpace += 1;
                }
            } else if (isStackable && ItemStack.isSameItemSameComponents(slotItem, itemTemplate)) {
                mainInventorySpace += (stackSize - slotItem.getCount());
            }
        }
        
        if (!isStackable) {
            mainInventorySpace = emptySlots;
        }
        
        // Early exit: if main inventory has enough space, no warning needed
        if (totalItemsNeeded <= mainInventorySpace) {
            return null;
        }
        
        // Only check for shulker boxes if we need more space
        // PERFORMANCE: Early exit when first shulker box found
        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
            ItemStack slotItem = inventory.getItem(i);
            if (isShulkerBox(slotItem)) {
                return "Items will be placed in boxes";
            }
        }
        
        return "Insufficient inventory space";
    }
    
    /**
     * Checks if an item stack is a shulker box.
     */
    private boolean isShulkerBox(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(net.minecraft.world.item.Items.SHULKER_BOX) || 
               stack.is(net.minecraft.world.item.Items.WHITE_SHULKER_BOX) || 
               stack.is(net.minecraft.world.item.Items.ORANGE_SHULKER_BOX) ||
               stack.is(net.minecraft.world.item.Items.MAGENTA_SHULKER_BOX) || 
               stack.is(net.minecraft.world.item.Items.LIGHT_BLUE_SHULKER_BOX) ||
               stack.is(net.minecraft.world.item.Items.YELLOW_SHULKER_BOX) || 
               stack.is(net.minecraft.world.item.Items.LIME_SHULKER_BOX) ||
               stack.is(net.minecraft.world.item.Items.PINK_SHULKER_BOX) || 
               stack.is(net.minecraft.world.item.Items.GRAY_SHULKER_BOX) ||
               stack.is(net.minecraft.world.item.Items.LIGHT_GRAY_SHULKER_BOX) || 
               stack.is(net.minecraft.world.item.Items.CYAN_SHULKER_BOX) ||
               stack.is(net.minecraft.world.item.Items.PURPLE_SHULKER_BOX) || 
               stack.is(net.minecraft.world.item.Items.BLUE_SHULKER_BOX) ||
               stack.is(net.minecraft.world.item.Items.BROWN_SHULKER_BOX) || 
               stack.is(net.minecraft.world.item.Items.GREEN_SHULKER_BOX) ||
               stack.is(net.minecraft.world.item.Items.RED_SHULKER_BOX) || 
               stack.is(net.minecraft.world.item.Items.BLACK_SHULKER_BOX);
    }


    private void renderButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();

        int buttonWidth = 150;
        int buttonHeight = 26;
        int buttonY = y + POPUP_HEIGHT - 45;
        int buttonSpacing = 20;

        // Center buttons with spacing between them
        int totalButtonsWidth = (buttonWidth * 2) + buttonSpacing;
        int buttonsStartX = x + (POPUP_WIDTH - totalButtonsWidth) / 2;

        int cancelX = buttonsStartX;
        boolean cancelHovered = mouseX >= cancelX && mouseX <= cancelX + buttonWidth && mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
        guiGraphics.fill(cancelX, buttonY, cancelX + buttonWidth, buttonY + buttonHeight, cancelHovered ? 0xCC666666 : 0x99666666);
        String cancelText = "Cancel";
        int cancelTextWidth = minecraft.font.width(cancelText);
        guiGraphics.drawString(minecraft.font, cancelText, cancelX + (buttonWidth - cancelTextWidth) / 2, buttonY + 8, TEXT_PRIMARY);

        int confirmX = buttonsStartX + buttonWidth + buttonSpacing;
        boolean confirmHovered = mouseX >= confirmX && mouseX <= confirmX + buttonWidth && mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
        guiGraphics.fill(confirmX, buttonY, confirmX + buttonWidth, buttonY + buttonHeight, confirmHovered ? 0xCC4CAF50 : 0x994CAF50);
        String confirmText = "Confirm Purchase";
        int confirmTextWidth = minecraft.font.width(confirmText);
        guiGraphics.drawString(minecraft.font, confirmText, confirmX + (buttonWidth - confirmTextWidth) / 2, buttonY + 8, TEXT_PRIMARY);
    }

    @Override
    protected boolean handlePopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }

        int plusMinusWidth = 24;
        int plusMinusHeight = 22;
        int spacing = 6;
        int labelWidth_px = 80;
        int labelX = x + (POPUP_WIDTH - labelWidth_px - (plusMinusWidth * 2) - (spacing * 2)) / 2;
        int labelY = y + 175;
        int buttonY = labelY;

        // Check modifier keys
        boolean isCtrlDown = com.mojang.blaze3d.platform.InputConstants.isKeyDown(
            Minecraft.getInstance().getWindow().getWindow(),
            org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL
        ) || com.mojang.blaze3d.platform.InputConstants.isKeyDown(
            Minecraft.getInstance().getWindow().getWindow(),
            org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL
        );
        boolean isShiftDown = com.mojang.blaze3d.platform.InputConstants.isKeyDown(
            Minecraft.getInstance().getWindow().getWindow(),
            org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT
        ) || com.mojang.blaze3d.platform.InputConstants.isKeyDown(
            Minecraft.getInstance().getWindow().getWindow(),
            org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT
        );

        // Minus button on the left
        int minusX = labelX - plusMinusWidth - spacing;
        if (mouseX >= minusX && mouseX <= minusX + plusMinusWidth && mouseY >= buttonY && mouseY <= buttonY + plusMinusHeight) {
            if (isCtrlDown && isShiftDown) {
                // CTRL + SHIFT + click: set to 1
                setQuantity(1);
            } else if (isCtrlDown) {
                // CTRL + click: minus 10
                adjustQuantity(-10);
            } else if (isShiftDown) {
                // SHIFT + click: minus 100
                adjustQuantity(-100);
            } else {
                // Normal click: minus 1
                adjustQuantity(-1);
            }
            playClickSound();
            return true;
        }

        // Plus button on the right
        int plusX = labelX + labelWidth_px + spacing;
        if (mouseX >= plusX && mouseX <= plusX + plusMinusWidth && mouseY >= buttonY && mouseY <= buttonY + plusMinusHeight) {
            if (isCtrlDown && isShiftDown) {
                // CTRL + SHIFT + click: set to max
                int maxBuyable = getMaxBuyable();
                if (maxBuyable > 0) {
                    setQuantity(maxBuyable);
                } else {
                    setQuantity(1);
                }
            } else if (isCtrlDown) {
                // CTRL + click: plus 10
                adjustQuantity(10);
            } else if (isShiftDown) {
                // SHIFT + click: plus 100
                adjustQuantity(100);
            } else {
                // Normal click: plus 1
                adjustQuantity(1);
            }
            playClickSound();
            return true;
        }

        int buttonWidth = 150;
        int buttonHeight = 26;
        int buttonY_click = y + POPUP_HEIGHT - 45;
        int buttonSpacing = 20;
        int totalButtonsWidth = (buttonWidth * 2) + buttonSpacing;
        int buttonsStartX = x + (POPUP_WIDTH - totalButtonsWidth) / 2;
        
        int cancelX = buttonsStartX;
        if (mouseX >= cancelX && mouseX <= cancelX + buttonWidth && mouseY >= buttonY_click && mouseY <= buttonY_click + buttonHeight) {
            playClickSound();
            hide();
            return true;
        }

        int confirmX = buttonsStartX + buttonWidth + buttonSpacing;
        if (mouseX >= confirmX && mouseX <= confirmX + buttonWidth && mouseY >= buttonY_click && mouseY <= buttonY_click + buttonHeight) {
            playClickSound();
            confirmPurchase();
            return true;
        }

        return false;
    }

    private void playClickSound() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.0f);
        }
    }

    private void adjustQuantity(int delta) {
        int current = getQuantity();
        int newValue = Math.max(1, current + delta);
        int max = getMaxBuyable();
        if (max > 0) {
            newValue = Math.min(newValue, max);
        }
        setQuantity(newValue);
    }

    private void setQuantity(int value) {
        currentQuantity = value;
    }

    private int getQuantity() {
        return currentQuantity;
    }

    private int getMaxBuyable() {
        if (sourceContainer != null) {
            return sourceContainer.calculateMaxBuyable(marketItem);
        }
        long price = marketItem.getBuyPrice();
        if (price <= 0) {
            return 0;
        }
        long balance = parentScreen != null ? parentScreen.getCachedBalance() : cachedBalance;
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, balance / price));
    }

    private long getTotalCost() {
        return Math.max(0, (long) getQuantity() * marketItem.getBuyPrice());
    }

    private void confirmPurchase() {
        if (parentScreen != null) {
            cachedBalance = parentScreen.getCachedBalance();
        }

        int quantity = getQuantity();
        if (quantity <= 0) {
            setErrorMessage("Enter a quantity above zero.");
            return;
        }

        int maxBuyable = getMaxBuyable();
        if (maxBuyable > 0 && quantity > maxBuyable) {
            setErrorMessage("Maximum you can buy is " + maxBuyable + " orders.");
            return;
        }

        long totalCost = (long) quantity * marketItem.getBuyPrice();
        if (totalCost > cachedBalance) {
            setErrorMessage("Insufficient funds for $" + totalCost);
            return;
        }

        String payload = String.format("{\"marketListingId\":\"%s\",\"quantity\":%d}", marketItem.getMarketListingId(), quantity);
        FreeMarketPacket packet = FreeMarketPacket.withJson(PacketType.BUY_ITEM_REQUEST, payload);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);

        if (sourceContainer != null) {
            sourceContainer.startBuyCooldown(marketItem);
        }

        hide();
    }

    @Override
    protected boolean handlePopupKeyPress(int keyCode, int scanCode, int modifiers) {
        // Quantity field is now read-only, no keyboard input needed
        return false;
    }

    @Override
    protected boolean handlePopupCharTyped(char codePoint, int modifiers) {
        // Quantity field is now read-only, no keyboard input needed
        return false;
    }
}


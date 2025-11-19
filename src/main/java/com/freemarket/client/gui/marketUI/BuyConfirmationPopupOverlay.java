package com.freemarket.client.gui.marketUI;

import com.freemarket.client.data.ClientInventorySpaceCache;
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
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Popup overlay that confirms marketplace purchases and lets players pick a quantity.
 */
public class BuyConfirmationPopupOverlay extends PopupOverlay {

    private static final int POPUP_WIDTH = 420;
    private static final int POPUP_HEIGHT = 320;
    private static final int CONTENT_PADDING = 28;
    private static final int ITEM_SECTION_TOP = 48;
    private static final int ITEM_SECTION_HEIGHT = 78;
    private static final int QUANTITY_SECTION_TOP = 138;
    private static final int QUANTITY_SECTION_HEIGHT = 72;
    private static final int SUMMARY_SECTION_TOP = 214;
    private static final int SUMMARY_SECTION_HEIGHT = 48;
    private static final int STEPPER_BUTTON_WIDTH = 28;
    private static final int STEPPER_BUTTON_HEIGHT = 28;
    private static final int STEPPER_DISPLAY_WIDTH = 100;
    private static final int STEPPER_DISPLAY_HEIGHT = 28;
    private static final int STEPPER_SPACING = 8;
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 28;
    private static final int BUTTON_SPACING = 18;
    private static final int BUTTON_AREA_OFFSET = 54;
    private static final int CARD_OUTLINE = 0x40FFFFFF;
    private static final int CARD_BACKGROUND = 0xF01D1D1D;
    private static final int CONTROL_BACKGROUND = 0xFF2F2F2F;
    private static final int CONTROL_BACKGROUND_HOVER = 0xFF3D3D3D;
    private static final int DIVIDER_COLOR = 0x33FFFFFF;

    private final FreeMarketItem marketItem;
    private final String marketListingId;
    private final FreeMarketGuiScreen parentScreen;
    private final FreeMarketContainer sourceContainer;

    private int currentQuantity = 1;
    
    private long cachedBalance;
    private long lastSpaceRequestTime = 0L;
    
    public BuyConfirmationPopupOverlay(FreeMarketItem marketItem, FreeMarketGuiScreen parentScreen, FreeMarketContainer sourceContainer) {
        super(0, 0, POPUP_WIDTH, POPUP_HEIGHT);
        this.marketItem = marketItem;
        this.marketListingId = marketItem.getMarketListingId();
        this.parentScreen = parentScreen;
        this.sourceContainer = sourceContainer;
        this.cachedBalance = parentScreen != null ? parentScreen.getCachedBalance() : com.freemarket.client.handlers.ClientWalletHandler.getPlayerMoney();
    }

    private void drawCard(GuiGraphics guiGraphics, int left, int top, int right, int bottom) {
        guiGraphics.fill(left, top, right, bottom, CARD_OUTLINE);
        guiGraphics.fill(left + 1, top + 1, right - 1, bottom - 1, CARD_BACKGROUND);
    }

    private StepperMetrics getQuantityStepperMetrics() {
        int totalWidth = (STEPPER_BUTTON_WIDTH * 2) + STEPPER_DISPLAY_WIDTH + (STEPPER_SPACING * 2);
        int startX = x + (POPUP_WIDTH - totalWidth) / 2;
        int buttonY = y + QUANTITY_SECTION_TOP + (QUANTITY_SECTION_HEIGHT / 2) - (STEPPER_BUTTON_HEIGHT / 2);

        int minusX = startX;
        int displayX = minusX + STEPPER_BUTTON_WIDTH + STEPPER_SPACING;
        int plusX = displayX + STEPPER_DISPLAY_WIDTH + STEPPER_SPACING;

        return new StepperMetrics(
            minusX,
            plusX,
            buttonY,
            STEPPER_BUTTON_WIDTH,
            STEPPER_BUTTON_HEIGHT,
            displayX,
            buttonY,
            STEPPER_DISPLAY_WIDTH,
            STEPPER_DISPLAY_HEIGHT
        );
    }

    private ButtonLayout getButtonLayout() {
        int totalWidth = (BUTTON_WIDTH * 2) + BUTTON_SPACING;
        int startX = x + (POPUP_WIDTH - totalWidth) / 2;
        int buttonY = y + POPUP_HEIGHT - BUTTON_AREA_OFFSET;
        int confirmX = startX + BUTTON_WIDTH + BUTTON_SPACING;
        return new ButtonLayout(startX, confirmX, buttonY);
    }

    private static boolean isWithin(double mouseX, double mouseY, int rectX, int rectY, int width, int height) {
        return mouseX >= rectX && mouseX <= rectX + width && mouseY >= rectY && mouseY <= rectY + height;
    }

    private record StepperMetrics(
        int minusX,
        int plusX,
        int buttonY,
        int buttonWidth,
        int buttonHeight,
        int displayX,
        int displayY,
        int displayWidth,
        int displayHeight
    ) {}

    private record ButtonLayout(int cancelX, int confirmX, int buttonY) {}

    @Override
    public void show() {
        super.show();

        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        this.x = (screenWidth - POPUP_WIDTH) / 2;
        this.y = (screenHeight - POPUP_HEIGHT) / 2;
        this.currentQuantity = 1;
        requestServerInventorySpace();
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

    private void requestServerInventorySpace() {
        if (marketListingId == null || marketListingId.isEmpty()) {
            lastSpaceRequestTime = 0L;
            return;
        }
        lastSpaceRequestTime = System.currentTimeMillis();
        String payload = String.format("{\"marketListingId\":\"%s\"}", marketListingId);
        FreeMarketPacket packet = FreeMarketPacket.withJson(PacketType.INVENTORY_SPACE_REQUEST, payload);
        PacketDistributor.sendToServer(packet);
    }

    private ClientInventorySpaceCache.InventorySpaceInfo getServerInventorySpaceInfo() {
        if (marketListingId == null || marketListingId.isEmpty()) {
            return null;
        }
        ClientInventorySpaceCache.InventorySpaceInfo info = ClientInventorySpaceCache.get(marketListingId);
        if (info == null) {
            return null;
        }
        if (lastSpaceRequestTime > 0 && info.timestamp() < lastSpaceRequestTime) {
            return null;
        }
        return info;
    }

    private int getAvailableItemCapacity() {
        ClientInventorySpaceCache.InventorySpaceInfo info = getServerInventorySpaceInfo();
        if (info != null) {
            return info.totalItems();
        }
        return calculateMaxInventorySpace();
    }

    private int getInventoryLimitedOrders() {
        int itemsPerOrder = Math.max(1, marketItem.getStackSize());
        ClientInventorySpaceCache.InventorySpaceInfo info = getServerInventorySpaceInfo();
        if (info != null) {
            return Math.max(0, info.maxOrders());
        }
        int estimatedItems = calculateMaxInventorySpace();
        if (estimatedItems <= 0) {
            return 0;
        }
        return estimatedItems / itemsPerOrder;
    }

    private void renderItemInfo(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        var font = minecraft.font;

        int cardLeft = x + CONTENT_PADDING;
        int cardRight = x + POPUP_WIDTH - CONTENT_PADDING;
        int cardTop = y + ITEM_SECTION_TOP;
        int cardBottom = cardTop + ITEM_SECTION_HEIGHT;

        drawCard(guiGraphics, cardLeft, cardTop, cardRight, cardBottom);

        int iconSize = 52;
        int cardWidth = cardRight - cardLeft;
        int iconX = cardLeft + (cardWidth - iconSize) / 2;
        int iconY = cardTop + (ITEM_SECTION_HEIGHT - iconSize) / 2;

        ItemStack stack = createItemWithComponentData(marketItem);
        stack.setCount(marketItem.getStackSize());

        boolean isHovered = mouseX >= iconX && mouseX <= iconX + iconSize &&
                            mouseY >= iconY && mouseY <= iconY + iconSize;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(iconX + iconSize / 2F, iconY + iconSize / 2F, 0);
        float scale = (float) iconSize / 16F;
        guiGraphics.pose().scale(scale, scale, scale);
        guiGraphics.renderItem(stack, -8, -8);
        guiGraphics.renderItemDecorations(font, stack, -8, -8);
        guiGraphics.pose().popPose();

        if (isHovered) {
            guiGraphics.renderTooltip(font, stack, mouseX, mouseY);
        }

        String priceValue = "$" + MoneyFormatter.formatWithSuffix(marketItem.getBuyPrice());
        int priceWidth = font.width(priceValue);
        int priceX = cardRight - priceWidth - 12;
        int priceLabelWidth = font.width("Price per order");
        int priceLabelX = cardRight - priceLabelWidth - 12;
        guiGraphics.drawString(font, "Price per order", priceLabelX, cardTop + 8, TEXT_SECONDARY);
        guiGraphics.drawString(font, priceValue, priceX, cardTop + 8 + font.lineHeight + 2, SUCCESS_COLOR);
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
        var font = minecraft.font;

        int cardLeft = x + CONTENT_PADDING;
        int cardRight = x + POPUP_WIDTH - CONTENT_PADDING;
        int cardTop = y + QUANTITY_SECTION_TOP;
        int cardBottom = cardTop + QUANTITY_SECTION_HEIGHT;

        drawCard(guiGraphics, cardLeft, cardTop, cardRight, cardBottom);

        String sectionLabel = "Select quantity";
        guiGraphics.drawString(font, sectionLabel, cardLeft + 12, cardTop + 8, TEXT_PRIMARY);

        int maxBuyable = getMaxBuyable();
        String maxText;
        if (maxBuyable == Integer.MAX_VALUE) {
            maxText = "Unlimited (funds only)";
        } else if (maxBuyable <= 0) {
            maxText = "Max: 0 orders";
        } else {
            maxText = "Max: " + maxBuyable + " orders";
        }

        if (getServerInventorySpaceInfo() != null) {
            maxText += " (verified)";
        } else if (marketListingId != null && !marketListingId.isEmpty()) {
            maxText += " (estimating)";
        }
        int maxWidth = font.width(maxText);
        guiGraphics.drawString(font, maxText, cardRight - maxWidth - 12, cardTop + 8, TEXT_SECONDARY);

        StepperMetrics metrics = getQuantityStepperMetrics();

        // Minus button
        boolean minusHovered = mouseX >= metrics.minusX && mouseX <= metrics.minusX + metrics.buttonWidth &&
            mouseY >= metrics.buttonY && mouseY <= metrics.buttonY + metrics.buttonHeight;
        guiGraphics.fill(metrics.minusX, metrics.buttonY, metrics.minusX + metrics.buttonWidth, metrics.buttonY + metrics.buttonHeight,
            minusHovered ? CONTROL_BACKGROUND_HOVER : CONTROL_BACKGROUND);
        int buttonTextY = metrics.buttonY + (metrics.buttonHeight - font.lineHeight) / 2;
        guiGraphics.drawString(font, "-", metrics.minusX + (metrics.buttonWidth - font.width("-")) / 2, buttonTextY, TEXT_PRIMARY);

        // Quantity display
        guiGraphics.fill(metrics.displayX, metrics.displayY, metrics.displayX + metrics.displayWidth, metrics.displayY + metrics.displayHeight, CONTROL_BACKGROUND);
        guiGraphics.fill(metrics.displayX, metrics.displayY, metrics.displayX + metrics.displayWidth, metrics.displayY + 1, DIVIDER_COLOR);
        String quantityText = String.valueOf(currentQuantity);
        int quantityTextWidth = font.width(quantityText);
        guiGraphics.drawString(
            font,
            quantityText,
            metrics.displayX + (metrics.displayWidth - quantityTextWidth) / 2,
            metrics.displayY + (metrics.displayHeight - font.lineHeight) / 2,
            TEXT_PRIMARY
        );

        // Plus button
        boolean plusHovered = mouseX >= metrics.plusX && mouseX <= metrics.plusX + metrics.buttonWidth &&
            mouseY >= metrics.buttonY && mouseY <= metrics.buttonY + metrics.buttonHeight;
        guiGraphics.fill(metrics.plusX, metrics.buttonY, metrics.plusX + metrics.buttonWidth, metrics.buttonY + metrics.buttonHeight,
            plusHovered ? CONTROL_BACKGROUND_HOVER : CONTROL_BACKGROUND);
        guiGraphics.drawString(font, "+", metrics.plusX + (metrics.buttonWidth - font.width("+")) / 2, buttonTextY, TEXT_PRIMARY);

        String warning = getInventorySpaceWarning();
        if (warning != null) {
            int warningWidth = font.width(warning);
            guiGraphics.drawString(font, warning, x + (POPUP_WIDTH - warningWidth) / 2, cardBottom - font.lineHeight - 4, WARNING_COLOR);
        }
    }

    private void renderSummary(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        var font = minecraft.font;

        int cardLeft = x + CONTENT_PADDING;
        int cardRight = x + POPUP_WIDTH - CONTENT_PADDING;
        int cardTop = y + SUMMARY_SECTION_TOP;
        int cardBottom = cardTop + SUMMARY_SECTION_HEIGHT;

        drawCard(guiGraphics, cardLeft, cardTop, cardRight, cardBottom);

        long totalCost = getTotalCost();
        String totalValue = "$" + MoneyFormatter.formatWithSuffix(totalCost);
        guiGraphics.drawString(font, "Total cost", cardLeft + 12, cardTop + 8, TEXT_SECONDARY);
        guiGraphics.drawString(font, totalValue, cardLeft + 12, cardTop + 8 + font.lineHeight + 2, SUCCESS_COLOR);

        String balanceText = "Balance: $" + MoneyFormatter.formatWithSuffix(cachedBalance);
        int balanceWidth = font.width(balanceText);
        guiGraphics.drawString(font, balanceText, cardRight - balanceWidth - 12, cardTop + 8, TEXT_SECONDARY);

        long remaining = Math.max(0, cachedBalance - totalCost);
        String remainingText = "After purchase: $" + MoneyFormatter.formatWithSuffix(remaining);
        int remainingWidth = font.width(remainingText);
        guiGraphics.drawString(font, remainingText, cardRight - remainingWidth - 12, cardTop + 8 + font.lineHeight + 2, TEXT_PRIMARY);
    }
    
    /**
     * Calculates inventory space and returns a warning message.
     * Returns null if there's enough space, or a warning message if items will be dropped or placed in shulker boxes.
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
        
        // Calculate available space
        int totalSpace = getAvailableItemCapacity();
        
        // If we have enough space, no warning needed
        if (totalItemsNeeded <= totalSpace) {
            return null;
        }

        ClientInventorySpaceCache.InventorySpaceInfo serverInfo = getServerInventorySpaceInfo();
        if (serverInfo != null) {
            return "Not enough verified inventory space (need " + totalItemsNeeded + ", have " + totalSpace + ")";
        }
        
        // Check if we have shulker boxes
        var inventory = player.getInventory();
        final int MAIN_INVENTORY_SIZE = 36;
        boolean hasShulkerBoxes = false;
        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
            ItemStack slotItem = inventory.getItem(i);
            if (isShulkerBox(slotItem)) {
                hasShulkerBoxes = true;
                break;
            }
        }
        
        if (hasShulkerBoxes) {
            return "Items will be placed in your shulker boxes";
        } else {
            return "Not enough inventory slots. Items will be dropped";
        }
    }
    
    /**
     * Calculates the maximum number of items that can fit in the player's inventory (including shulker boxes).
     * Similar to server-side calculateInventorySpace but client-side compatible.
     */
    private int calculateMaxInventorySpace() {
        Minecraft minecraft = Minecraft.getInstance();
        net.minecraft.world.entity.player.Player player = minecraft.player;
        if (player == null) {
            return 0;
        }
        
        // Create item template for checking
        ItemStack itemTemplate = createItemWithComponentData(marketItem);
        itemTemplate.setCount(1);
        
        var inventory = player.getInventory();
        final int MAIN_INVENTORY_SIZE = 36;
        int stackSize = itemTemplate.getMaxStackSize();
        boolean isStackable = stackSize > 1;
        
        // Calculate space in main inventory
        int mainInventorySpace = 0;
        int emptySlots = 0;
        
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
        
        // Check shulker boxes for additional space
        int shulkerSpace = 0;
        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
            ItemStack slotItem = inventory.getItem(i);
            if (isShulkerBox(slotItem)) {
                // Try to get shulker container (client-side, may not work in multiplayer)
                try {
                    var singleplayerServer = minecraft.getSingleplayerServer();
                    if (singleplayerServer != null) {
                        var serverPlayer = singleplayerServer.getPlayerList().getPlayer(player.getUUID());
                        if (serverPlayer != null) {
                            // Use server-side calculation if available
                            // For now, estimate: each shulker box has 27 slots
                            final int SHULKER_SIZE = 27;
                            if (isStackable) {
                                shulkerSpace += SHULKER_SIZE * stackSize;
                            } else {
                                shulkerSpace += SHULKER_SIZE;
                            }
                        }
                    }
                } catch (Exception e) {
                    // Fallback: estimate shulker space
                    final int SHULKER_SIZE = 27;
                    if (isStackable) {
                        shulkerSpace += SHULKER_SIZE * stackSize;
                    } else {
                        shulkerSpace += SHULKER_SIZE;
                    }
                }
            }
        }
        
        return mainInventorySpace + shulkerSpace;
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
        var font = minecraft.font;

        ButtonLayout layout = getButtonLayout();
        int buttonY = layout.buttonY;

        int cancelX = layout.cancelX;
        boolean cancelHovered = mouseX >= cancelX && mouseX <= cancelX + BUTTON_WIDTH && mouseY >= buttonY && mouseY <= buttonY + BUTTON_HEIGHT;
        
        // Cancel Button Background (Gray like Create Auction Popup)
        int cancelBgColor = cancelHovered ? 0xCC666666 : 0x99666666;
        guiGraphics.fill(cancelX, buttonY, cancelX + BUTTON_WIDTH, buttonY + BUTTON_HEIGHT, cancelBgColor);
        
        // Cancel Button Borders
        guiGraphics.fill(cancelX, buttonY, cancelX + BUTTON_WIDTH, buttonY + 1, BORDER_COLOR); // Top
        guiGraphics.fill(cancelX, buttonY + 1, cancelX + 1, buttonY + BUTTON_HEIGHT, BORDER_COLOR); // Left
        guiGraphics.fill(cancelX + BUTTON_WIDTH - 1, buttonY + 1, cancelX + BUTTON_WIDTH, buttonY + BUTTON_HEIGHT, BORDER_COLOR); // Right
        guiGraphics.fill(cancelX + 1, buttonY + BUTTON_HEIGHT - 1, cancelX + BUTTON_WIDTH - 1, buttonY + BUTTON_HEIGHT, BORDER_COLOR); // Bottom
        
        // Cancel Button Text (Simple centered)
        String cancelText = "Cancel";
        int cancelTextWidth = font.width(cancelText);
        int cancelTextX = cancelX + (BUTTON_WIDTH - cancelTextWidth) / 2;
        int cancelTextY = buttonY + (BUTTON_HEIGHT - font.lineHeight) / 2;
        guiGraphics.drawString(font, cancelText, cancelTextX, cancelTextY, TEXT_PRIMARY);

        int confirmX = layout.confirmX;
        boolean confirmHovered = mouseX >= confirmX && mouseX <= confirmX + BUTTON_WIDTH && mouseY >= buttonY && mouseY <= buttonY + BUTTON_HEIGHT;
        
        // Confirm Button Background
        guiGraphics.fill(confirmX, buttonY, confirmX + BUTTON_WIDTH, buttonY + BUTTON_HEIGHT, confirmHovered ? 0xCC4CAF50 : 0x994CAF50);
        
        // Confirm Button Borders
        guiGraphics.fill(confirmX, buttonY, confirmX + BUTTON_WIDTH, buttonY + 1, BORDER_COLOR); // Top
        guiGraphics.fill(confirmX, buttonY + 1, confirmX + 1, buttonY + BUTTON_HEIGHT, BORDER_COLOR); // Left
        guiGraphics.fill(confirmX + BUTTON_WIDTH - 1, buttonY + 1, confirmX + BUTTON_WIDTH, buttonY + BUTTON_HEIGHT, BORDER_COLOR); // Right
        guiGraphics.fill(confirmX + 1, buttonY + BUTTON_HEIGHT - 1, confirmX + BUTTON_WIDTH - 1, buttonY + BUTTON_HEIGHT, BORDER_COLOR); // Bottom
        
        // Confirm Button Text (Simple centered)
        String confirmText = "Confirm Purchase";
        int confirmTextWidth = font.width(confirmText);
        int confirmTextX = confirmX + (BUTTON_WIDTH - confirmTextWidth) / 2;
        int confirmTextY = buttonY + (BUTTON_HEIGHT - font.lineHeight) / 2;
        guiGraphics.drawString(font, confirmText, confirmTextX, confirmTextY, TEXT_PRIMARY);
    }

    @Override
    protected boolean handlePopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }

        StepperMetrics metrics = getQuantityStepperMetrics();

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
        if (isWithin(mouseX, mouseY, metrics.minusX, metrics.buttonY, metrics.buttonWidth, metrics.buttonHeight)) {
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
        if (isWithin(mouseX, mouseY, metrics.plusX, metrics.buttonY, metrics.buttonWidth, metrics.buttonHeight)) {
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

        ButtonLayout layout = getButtonLayout();
        int buttonY_click = layout.buttonY;
        int cancelX = layout.cancelX;
        if (isWithin(mouseX, mouseY, cancelX, buttonY_click, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            playClickSound();
            hide();
            return true;
        }

        int confirmX = layout.confirmX;
        if (isWithin(mouseX, mouseY, confirmX, buttonY_click, BUTTON_WIDTH, BUTTON_HEIGHT)) {
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
        if (max > 0 && max != Integer.MAX_VALUE) {
            newValue = Math.min(newValue, max);
        } else if (max == 0) {
            newValue = 1;
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
        long price = marketItem.getBuyPrice();
        long balance = sourceContainer != null && parentScreen != null
            ? parentScreen.getCachedBalance()
            : (parentScreen != null ? parentScreen.getCachedBalance() : cachedBalance);

        int maxByMoney = price > 0
            ? (int) Math.min(Integer.MAX_VALUE, Math.max(0, balance / price))
            : Integer.MAX_VALUE;

        int maxByInventory = Math.max(0, getInventoryLimitedOrders());

        if (maxByInventory == 0) {
            return 0;
        }
        if (maxByMoney == Integer.MAX_VALUE) {
            return maxByInventory;
        }
        if (maxByMoney == 0) {
            return 0;
        }
        return Math.min(maxByMoney, maxByInventory);
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
        if (maxBuyable <= 0) {
            setErrorMessage("You do not have enough inventory space for this item.");
            return;
        }
        if (maxBuyable != Integer.MAX_VALUE && quantity > maxBuyable) {
            setErrorMessage("Maximum you can buy is " + maxBuyable + " orders.");
            return;
        }

        // Check money
        long totalCost = (long) quantity * marketItem.getBuyPrice();
        if (totalCost > cachedBalance) {
            setErrorMessage("Insufficient funds for $" + totalCost);
            return;
        }

        // Check inventory space
        int totalItemsNeeded = quantity * Math.max(1, marketItem.getStackSize());
        int maxInventorySpace = getAvailableItemCapacity();
        if (totalItemsNeeded > maxInventorySpace) {
            setErrorMessage("Insufficient inventory space. Need " + totalItemsNeeded + " items, have space for " + maxInventorySpace);
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


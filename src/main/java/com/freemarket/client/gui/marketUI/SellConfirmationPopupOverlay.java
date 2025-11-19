package com.freemarket.client.gui.marketUI;

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
 * Popup overlay that confirms marketplace sales and allows selecting quantity.
 */
public class SellConfirmationPopupOverlay extends PopupOverlay {

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
    private final FreeMarketContainer sourceContainer;

    private int currentQuantity = 1;

    public SellConfirmationPopupOverlay(FreeMarketItem marketItem, FreeMarketContainer sourceContainer) {
        super(0, 0, POPUP_WIDTH, POPUP_HEIGHT);
        this.marketItem = marketItem;
        this.sourceContainer = sourceContainer;
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
        return Component.literal("Confirm Sale");
    }

    @Override
    protected void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderItemInfo(guiGraphics, mouseX, mouseY);
        renderQuantitySection(guiGraphics, mouseX, mouseY);
        renderSummary(guiGraphics);
        renderButtons(guiGraphics, mouseX, mouseY);
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
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 7000);
            guiGraphics.renderTooltip(font, stack, mouseX, mouseY);
            guiGraphics.pose().popPose();
        }

        int textStartX = iconX + iconSize + 12;
        Component itemName = stack.getHoverName();
        guiGraphics.drawString(font, itemName, textStartX, cardTop + 8, TEXT_PRIMARY);

        int perOrder = Math.max(1, marketItem.getStackSize());
        String stackSummary = perOrder > 1
            ? "Orders consume " + perOrder + " items"
            : "Single item order";
        guiGraphics.drawString(font, stackSummary, textStartX, cardTop + 8 + font.lineHeight + 2, TEXT_SECONDARY);

        String payoutValue = "$" + MoneyFormatter.formatWithSuffix(marketItem.getSellPrice());
        int valueWidth = font.width(payoutValue);
        int valueX = cardRight - valueWidth - 12;
        int labelWidth = font.width("Payout per order");
        int labelX = cardRight - labelWidth - 12;
        guiGraphics.drawString(font, "Payout per order", labelX, cardTop + 8, TEXT_SECONDARY);
        guiGraphics.drawString(font, payoutValue, valueX, cardTop + 8 + font.lineHeight + 2, SUCCESS_COLOR);
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

        guiGraphics.drawString(font, "Select quantity", cardLeft + 12, cardTop + 8, TEXT_PRIMARY);

        int maxSellable = getMaxSellable();
        String availableText = "Available: " + maxSellable + " orders";
        int availableWidth = font.width(availableText);
        guiGraphics.drawString(font, availableText, cardRight - availableWidth - 12, cardTop + 8, TEXT_SECONDARY);

        StepperMetrics metrics = getQuantityStepperMetrics();

        boolean minusHovered = mouseX >= metrics.minusX && mouseX <= metrics.minusX + metrics.buttonWidth &&
            mouseY >= metrics.buttonY && mouseY <= metrics.buttonY + metrics.buttonHeight;
        guiGraphics.fill(metrics.minusX, metrics.buttonY, metrics.minusX + metrics.buttonWidth, metrics.buttonY + metrics.buttonHeight,
            minusHovered ? CONTROL_BACKGROUND_HOVER : CONTROL_BACKGROUND);
        int buttonTextY = metrics.buttonY + (metrics.buttonHeight - font.lineHeight) / 2;
        guiGraphics.drawString(font, "-", metrics.minusX + (metrics.buttonWidth - font.width("-")) / 2, buttonTextY, TEXT_PRIMARY);

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

        boolean plusHovered = mouseX >= metrics.plusX && mouseX <= metrics.plusX + metrics.buttonWidth &&
            mouseY >= metrics.buttonY && mouseY <= metrics.buttonY + metrics.buttonHeight;
        guiGraphics.fill(metrics.plusX, metrics.buttonY, metrics.plusX + metrics.buttonWidth, metrics.buttonY + metrics.buttonHeight,
            plusHovered ? CONTROL_BACKGROUND_HOVER : CONTROL_BACKGROUND);
        guiGraphics.drawString(font, "+", metrics.plusX + (metrics.buttonWidth - font.width("+")) / 2, buttonTextY, TEXT_PRIMARY);
    }

    private void renderSummary(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        var font = minecraft.font;

        int cardLeft = x + CONTENT_PADDING;
        int cardRight = x + POPUP_WIDTH - CONTENT_PADDING;
        int cardTop = y + SUMMARY_SECTION_TOP;
        int cardBottom = cardTop + SUMMARY_SECTION_HEIGHT;

        drawCard(guiGraphics, cardLeft, cardTop, cardRight, cardBottom);

        long totalPayout = getTotalPayout();
        String totalValue = "$" + MoneyFormatter.formatWithSuffix(totalPayout);
        guiGraphics.drawString(font, "Total payout", cardLeft + 12, cardTop + 8, TEXT_SECONDARY);
        guiGraphics.drawString(font, totalValue, cardLeft + 12, cardTop + 8 + font.lineHeight + 2, SUCCESS_COLOR);

        int maxSellable = getMaxSellable();
        String availableText = "Orders available: " + maxSellable;
        int availableWidth = font.width(availableText);
        guiGraphics.drawString(font, availableText, cardRight - availableWidth - 12, cardTop + 8, TEXT_SECONDARY);

        int perOrder = Math.max(1, marketItem.getStackSize());
        String perOrderText = "Consumes " + perOrder + " item" + (perOrder > 1 ? "s" : "") + " per order";
        int perOrderWidth = font.width(perOrderText);
        guiGraphics.drawString(font, perOrderText, cardRight - perOrderWidth - 12, cardTop + 8 + font.lineHeight + 2, TEXT_MUTED);

        String noteText = "Items are pulled from inventory and loaded shulker boxes";
        int noteWidth = font.width(noteText);
        int noteY = cardBottom + 6;
        int maxY = y + POPUP_HEIGHT - BUTTON_AREA_OFFSET - font.lineHeight;
        if (noteY > maxY) {
            noteY = maxY;
        }
        guiGraphics.drawString(font, noteText, x + (POPUP_WIDTH - noteWidth) / 2, noteY, TEXT_MUTED);
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
        guiGraphics.fill(confirmX, buttonY, confirmX + BUTTON_WIDTH, buttonY + BUTTON_HEIGHT, confirmHovered ? 0xCC2196F3 : 0x992196F3);
        
        // Confirm Button Borders
        guiGraphics.fill(confirmX, buttonY, confirmX + BUTTON_WIDTH, buttonY + 1, BORDER_COLOR); // Top
        guiGraphics.fill(confirmX, buttonY + 1, confirmX + 1, buttonY + BUTTON_HEIGHT, BORDER_COLOR); // Left
        guiGraphics.fill(confirmX + BUTTON_WIDTH - 1, buttonY + 1, confirmX + BUTTON_WIDTH, buttonY + BUTTON_HEIGHT, BORDER_COLOR); // Right
        guiGraphics.fill(confirmX + 1, buttonY + BUTTON_HEIGHT - 1, confirmX + BUTTON_WIDTH - 1, buttonY + BUTTON_HEIGHT, BORDER_COLOR); // Bottom
        
        // Confirm Button Text (Simple centered)
        String confirmText = "Confirm Sale";
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
                int maxSellable = getMaxSellable();
                if (maxSellable > 0) {
                    setQuantity(maxSellable);
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
            confirmSale();
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
        int max = getMaxSellable();
        if (max > 0) {
            newValue = Math.min(newValue, max);
        }
        setQuantity(newValue);
    }

    private void setQuantity(int value) {
        this.currentQuantity = value;
    }

    private int getQuantity() {
        return this.currentQuantity;
    }

    private long getTotalPayout() {
        return Math.max(0, (long) getQuantity() * marketItem.getSellPrice());
    }

    private int getMaxSellable() {
        int perOrder = Math.max(1, marketItem.getStackSize());
        int ownedItems = sourceContainer != null ? sourceContainer.getPlayerInventoryCount(marketItem) : 0;
        return ownedItems / perOrder;
    }

    private void confirmSale() {
        int quantity = getQuantity();
        if (quantity <= 0) {
            setErrorMessage("Enter a quantity above zero.");
            return;
        }

        int maxSellable = getMaxSellable();
        if (maxSellable <= 0) {
            setErrorMessage("No matching items in inventory.");
            return;
        }

        if (quantity > maxSellable) {
            setErrorMessage("You can sell at most " + maxSellable + " orders.");
            return;
        }

        String payload = String.format("{\"marketListingId\":\"%s\",\"quantity\":%d}", marketItem.getMarketListingId(), quantity);
        FreeMarketPacket packet = FreeMarketPacket.withJson(PacketType.SELL_ITEM_REQUEST, payload);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);

        if (sourceContainer != null) {
            sourceContainer.startSellCooldown(marketItem);
        }

        hide();
    }

    @Override
    protected boolean handlePopupKeyPress(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    protected boolean handlePopupCharTyped(char codePoint, int modifiers) {
        return false;
    }
}

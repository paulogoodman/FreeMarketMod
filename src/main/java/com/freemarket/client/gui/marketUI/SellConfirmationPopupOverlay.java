package com.freemarket.client.gui.marketUI;

import com.freemarket.client.gui.commonUI.MoneyFormatter;
import com.freemarket.client.gui.commonUI.PopupOverlay;
import com.freemarket.common.data.FreeMarketItem;
import com.freemarket.common.network.FreeMarketPacket;
import com.freemarket.common.network.PacketType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Popup overlay that confirms marketplace sales and allows selecting quantity.
 */
public class SellConfirmationPopupOverlay extends PopupOverlay {

    private static final int POPUP_WIDTH = 420;
    private static final int POPUP_HEIGHT = 320;

    private final FreeMarketItem marketItem;
    private final FreeMarketContainer sourceContainer;

    private EditBox quantityBox;

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

        initializeQuantityBox();
    }

    private void initializeQuantityBox() {
        Minecraft minecraft = Minecraft.getInstance();

        int plusMinusWidth = 24;
        int spacing = 6;
        int inputWidth = 100;
        int inputX = x + (POPUP_WIDTH - inputWidth - (plusMinusWidth * 2) - (spacing * 2)) / 2;
        int inputY = y + 170;

        this.quantityBox = new EditBox(
            minecraft.font,
            inputX,
            inputY,
            inputWidth,
            22,
            Component.literal("Quantity")
        );
        this.quantityBox.setBordered(true);
        this.quantityBox.setFilter(text -> text.matches("\\d*"));
        this.quantityBox.setValue("1");
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

        // Render tooltip if hovering
        if (isHovered) {
            guiGraphics.renderTooltip(minecraft.font, stack, mouseX, mouseY);
        }

        Component itemName = stack.getHoverName();
        int nameWidth = minecraft.font.width(itemName);
        guiGraphics.drawString(minecraft.font, itemName, x + (POPUP_WIDTH - nameWidth) / 2, iconY + iconSize + 6, TEXT_PRIMARY);

        String priceText = "Payout per order: $" + marketItem.getSellPrice();
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
        int sectionHeight = 80;

        guiGraphics.fill(sectionX, sectionY, sectionX + sectionWidth, sectionY + sectionHeight, 0x99000000);

        String quantityLabel = "Quantity";
        guiGraphics.drawString(minecraft.font, quantityLabel, sectionX + 4, sectionY + 6, TEXT_PRIMARY);

        int plusMinusWidth = 24;
        int plusMinusHeight = 22;
        int spacing = 6;
        int inputWidth = 100;
        int inputX = x + (POPUP_WIDTH - inputWidth - (plusMinusWidth * 2) - (spacing * 2)) / 2;
        int inputY = y + 170;
        int buttonY = inputY;

        // Update quantity box position if it exists
        if (quantityBox != null) {
            quantityBox.setX(inputX);
            quantityBox.setY(inputY);
            quantityBox.setWidth(inputWidth);
            quantityBox.render(guiGraphics, mouseX, mouseY, 0);
        }

        // Minus button on the left
        int minusX = inputX - plusMinusWidth - spacing;
        boolean minusHovered = mouseX >= minusX && mouseX <= minusX + plusMinusWidth && mouseY >= buttonY && mouseY <= buttonY + plusMinusHeight;
        guiGraphics.fill(minusX, buttonY, minusX + plusMinusWidth, buttonY + plusMinusHeight, minusHovered ? 0xCC505050 : 0x99505050);
        guiGraphics.drawString(minecraft.font, "-", minusX + 9, buttonY + 6, TEXT_PRIMARY);

        // Plus button on the right
        int plusX = inputX + inputWidth + spacing;
        boolean plusHovered = mouseX >= plusX && mouseX <= plusX + plusMinusWidth && mouseY >= buttonY && mouseY <= buttonY + plusMinusHeight;
        guiGraphics.fill(plusX, buttonY, plusX + plusMinusWidth, buttonY + plusMinusHeight, plusHovered ? 0xCC505050 : 0x99505050);
        guiGraphics.drawString(minecraft.font, "+", plusX + 8, buttonY + 6, TEXT_PRIMARY);
    }

    private void renderSummary(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();

        int summaryY = y + 250;

        long totalPayout = getTotalPayout();
        String totalText = "Total: $" + MoneyFormatter.formatWithSuffix(totalPayout);
        int totalWidth = minecraft.font.width(totalText);
        guiGraphics.drawString(minecraft.font, totalText, x + (POPUP_WIDTH - totalWidth) / 2, summaryY, SUCCESS_COLOR);

        String availableText = "Orders available: " + getMaxSellable();
        int availableWidth = minecraft.font.width(availableText);
        guiGraphics.drawString(minecraft.font, availableText, x + (POPUP_WIDTH - availableWidth) / 2, summaryY + 14, TEXT_SECONDARY);
    }


    private void renderButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();

        int buttonWidth = 150;
        int buttonHeight = 26;
        int buttonY = y + POPUP_HEIGHT - 50;

        int cancelX = x + 40;
        boolean cancelHovered = mouseX >= cancelX && mouseX <= cancelX + buttonWidth && mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
        guiGraphics.fill(cancelX, buttonY, cancelX + buttonWidth, buttonY + buttonHeight, cancelHovered ? 0xCC666666 : 0x99666666);
        String cancelText = "Cancel";
        int cancelTextWidth = minecraft.font.width(cancelText);
        guiGraphics.drawString(minecraft.font, cancelText, cancelX + (buttonWidth - cancelTextWidth) / 2, buttonY + 8, TEXT_PRIMARY);

        int confirmX = x + POPUP_WIDTH - buttonWidth - 40;
        boolean confirmHovered = mouseX >= confirmX && mouseX <= confirmX + buttonWidth && mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
        guiGraphics.fill(confirmX, buttonY, confirmX + buttonWidth, buttonY + buttonHeight, confirmHovered ? 0xCC2196F3 : 0x992196F3);
        String confirmText = "Confirm Sale";
        int confirmTextWidth = minecraft.font.width(confirmText);
        guiGraphics.drawString(minecraft.font, confirmText, confirmX + (buttonWidth - confirmTextWidth) / 2, buttonY + 8, TEXT_PRIMARY);
    }

    @Override
    protected boolean handlePopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }

        if (quantityBox != null && quantityBox.mouseClicked(mouseX, mouseY, button)) {
            quantityBox.setFocused(true);
            return true;
        }

        int plusMinusWidth = 24;
        int plusMinusHeight = 22;
        int spacing = 6;
        int inputWidth = 100;
        int inputX = x + (POPUP_WIDTH - inputWidth - (plusMinusWidth * 2) - (spacing * 2)) / 2;
        int inputY = y + 170;
        int buttonY = inputY;

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
        int minusX = inputX - plusMinusWidth - spacing;
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
        int plusX = inputX + inputWidth + spacing;
        if (mouseX >= plusX && mouseX <= plusX + plusMinusWidth && mouseY >= buttonY && mouseY <= buttonY + plusMinusHeight) {
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

        int buttonWidth = 150;
        int buttonHeight = 26;
        int cancelX = x + 40;
        int buttonBottom = y + POPUP_HEIGHT - 50 + buttonHeight;
        if (mouseX >= cancelX && mouseX <= cancelX + buttonWidth && mouseY >= y + POPUP_HEIGHT - 50 && mouseY <= buttonBottom) {
            playClickSound();
            hide();
            return true;
        }

        int confirmX = x + POPUP_WIDTH - buttonWidth - 40;
        if (mouseX >= confirmX && mouseX <= confirmX + buttonWidth && mouseY >= y + POPUP_HEIGHT - 50 && mouseY <= buttonBottom) {
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
        if (quantityBox != null) {
            quantityBox.setValue(String.valueOf(value));
        }
    }

    private int getQuantity() {
        if (quantityBox == null) {
            return 1;
        }
        String text = quantityBox.getValue();
        if (text.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
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
        if (quantityBox != null && quantityBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return false;
    }

    @Override
    protected boolean handlePopupCharTyped(char codePoint, int modifiers) {
        if (quantityBox != null && quantityBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return false;
    }
}


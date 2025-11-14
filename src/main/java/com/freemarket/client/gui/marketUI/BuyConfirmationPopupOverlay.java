package com.freemarket.client.gui.marketUI;

import com.freemarket.client.gui.commonUI.FreeMarketGuiScreen;
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
 * Popup overlay that confirms marketplace purchases and lets players pick a quantity.
 */
public class BuyConfirmationPopupOverlay extends PopupOverlay {

    private static final int POPUP_WIDTH = 420;
    private static final int POPUP_HEIGHT = 320;

    private final FreeMarketItem marketItem;
    private final FreeMarketGuiScreen parentScreen;
    private final FreeMarketContainer sourceContainer;

    private EditBox quantityBox;
    
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

        initializeQuantityBox();
    }

    private void initializeQuantityBox() {
        Minecraft minecraft = Minecraft.getInstance();

        int inputWidth = POPUP_WIDTH - 80;
        int inputX = x + 40;
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
        return Component.literal("Confirm Purchase");
    }

    @Override
    protected void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (parentScreen != null) {
            cachedBalance = parentScreen.getCachedBalance();
        }
        renderItemInfo(guiGraphics);
        renderQuantitySection(guiGraphics, mouseX, mouseY);
        renderSummary(guiGraphics);
        renderButtons(guiGraphics, mouseX, mouseY);
    }

    private void renderItemInfo(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();

        int iconSize = 48;
        int iconX = x + (POPUP_WIDTH - iconSize) / 2;
        int iconY = y + 60;

        ItemStack stack = marketItem.getItemStack();

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(iconX + iconSize / 2F, iconY + iconSize / 2F, 0);
        float scale = (float) iconSize / 16F;
        guiGraphics.pose().scale(scale, scale, scale);
        guiGraphics.renderItem(stack, -8, -8);
        guiGraphics.renderItemDecorations(minecraft.font, stack, -8, -8);
        guiGraphics.pose().popPose();

        Component itemName = stack.getHoverName();
        int nameWidth = minecraft.font.width(itemName);
        guiGraphics.drawString(minecraft.font, itemName, x + (POPUP_WIDTH - nameWidth) / 2, iconY + iconSize + 6, TEXT_PRIMARY);

        String priceText = "Price per order: $" + marketItem.getBuyPrice();
        int priceWidth = minecraft.font.width(priceText);
        guiGraphics.drawString(minecraft.font, priceText, x + (POPUP_WIDTH - priceWidth) / 2, iconY + iconSize + 22, SUCCESS_COLOR);
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

        if (quantityBox != null) {
            quantityBox.render(guiGraphics, mouseX, mouseY, 0);
        }

        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonY = sectionY + sectionHeight - buttonHeight - 8;

        int buyMaxX = sectionX + sectionWidth - buttonWidth - 6;
        boolean buyMaxHovered = mouseX >= buyMaxX && mouseX <= buyMaxX + buttonWidth && mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
        guiGraphics.fill(buyMaxX, buttonY, buyMaxX + buttonWidth, buttonY + buttonHeight, buyMaxHovered ? 0xCC4CAF50 : 0x994CAF50);
        guiGraphics.drawString(minecraft.font, "Buy Max", buyMaxX + 14, buttonY + 6, TEXT_PRIMARY);

        int plusMinusWidth = 24;
        int plusMinusHeight = 20;
        int minusX = sectionX + 6;
        int plusX = minusX + plusMinusWidth + 6;

        boolean minusHovered = mouseX >= minusX && mouseX <= minusX + plusMinusWidth && mouseY >= buttonY && mouseY <= buttonY + plusMinusHeight;
        guiGraphics.fill(minusX, buttonY, minusX + plusMinusWidth, buttonY + plusMinusHeight, minusHovered ? 0xCC505050 : 0x99505050);
        guiGraphics.drawString(minecraft.font, "-", minusX + 9, buttonY + 6, TEXT_PRIMARY);

        boolean plusHovered = mouseX >= plusX && mouseX <= plusX + plusMinusWidth && mouseY >= buttonY && mouseY <= buttonY + plusMinusHeight;
        guiGraphics.fill(plusX, buttonY, plusX + plusMinusWidth, buttonY + plusMinusHeight, plusHovered ? 0xCC505050 : 0x99505050);
        guiGraphics.drawString(minecraft.font, "+", plusX + 8, buttonY + 6, TEXT_PRIMARY);
    }

    private void renderSummary(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();

        int summaryY = y + 250;

        long totalCost = getTotalCost();
        String totalText = "Total: $" + totalCost;
        int totalWidth = minecraft.font.width(totalText);
        guiGraphics.drawString(minecraft.font, totalText, x + (POPUP_WIDTH - totalWidth) / 2, summaryY, SUCCESS_COLOR);

        String balanceText = "Balance: $" + cachedBalance;
        int balanceWidth = minecraft.font.width(balanceText);
        guiGraphics.drawString(minecraft.font, balanceText, x + (POPUP_WIDTH - balanceWidth) / 2, summaryY + 14, TEXT_SECONDARY);
    }

    private void renderButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();

        int buttonWidth = 150;
        int buttonHeight = 26;
        int buttonY = y + POPUP_HEIGHT - 50;

        int confirmX = x + 40;
        boolean confirmHovered = mouseX >= confirmX && mouseX <= confirmX + buttonWidth && mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
        guiGraphics.fill(confirmX, buttonY, confirmX + buttonWidth, buttonY + buttonHeight, confirmHovered ? 0xCC4CAF50 : 0x994CAF50);
        guiGraphics.drawString(minecraft.font, "Confirm Purchase", confirmX + 18, buttonY + 8, TEXT_PRIMARY);

        int cancelX = x + POPUP_WIDTH - buttonWidth - 40;
        boolean cancelHovered = mouseX >= cancelX && mouseX <= cancelX + buttonWidth && mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
        guiGraphics.fill(cancelX, buttonY, cancelX + buttonWidth, buttonY + buttonHeight, cancelHovered ? 0xCC666666 : 0x99666666);
        guiGraphics.drawString(minecraft.font, "Cancel", cancelX + 48, buttonY + 8, TEXT_PRIMARY);
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

        int sectionX = x + 40;
        int sectionY = y + 150;
        int sectionWidth = POPUP_WIDTH - 80;
        int sectionHeight = 80;
        int buttonY = sectionY + sectionHeight - 20 - 8;

        int minusX = sectionX + 6;
        int plusMinusWidth = 24;
        if (mouseX >= minusX && mouseX <= minusX + plusMinusWidth && mouseY >= buttonY && mouseY <= buttonY + 20) {
            adjustQuantity(-1);
            playClickSound();
            return true;
        }

        int plusX = minusX + plusMinusWidth + 6;
        if (mouseX >= plusX && mouseX <= plusX + plusMinusWidth && mouseY >= buttonY && mouseY <= buttonY + 20) {
            adjustQuantity(1);
            playClickSound();
            return true;
        }

        int buyMaxX = sectionX + sectionWidth - 90 - 6;
        if (mouseX >= buyMaxX && mouseX <= buyMaxX + 90 && mouseY >= buttonY && mouseY <= buttonY + 20) {
            int maxBuyable = getMaxBuyable();
            if (maxBuyable > 0) {
                setQuantity(maxBuyable);
            } else {
                setQuantity(1);
            }
            playClickSound();
            return true;
        }

        int buttonWidth = 150;
        int buttonHeight = 26;
        int confirmX = x + 40;
        int buttonBottom = y + POPUP_HEIGHT - 50 + buttonHeight;
        if (mouseX >= confirmX && mouseX <= confirmX + buttonWidth && mouseY >= y + POPUP_HEIGHT - 50 && mouseY <= buttonBottom) {
            playClickSound();
            confirmPurchase();
            return true;
        }

        int cancelX = x + POPUP_WIDTH - buttonWidth - 40;
        if (mouseX >= cancelX && mouseX <= cancelX + buttonWidth && mouseY >= y + POPUP_HEIGHT - 50 && mouseY <= buttonBottom) {
            playClickSound();
            hide();
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


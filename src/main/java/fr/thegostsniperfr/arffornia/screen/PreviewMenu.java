package fr.thegostsniperfr.arffornia.screen;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class PreviewMenu extends AbstractContainerMenu {
    private final Container container;
    public final String title;

    // Called on client side via network
    public PreviewMenu(int id, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        super(ModMenuTypes.PREVIEW_MENU.get(), id);
        this.title = buf.readUtf();
        int size = buf.readInt();
        this.container = new SimpleContainer(size);
        for(int i = 0; i < size; i++) {
            // Lecture compatible 1.21.1
            this.container.setItem(i, ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        }
        setupSlots(playerInv);
    }

    // Called on server side
    public PreviewMenu(int id, Inventory playerInv, List<ItemStack> items, String title) {
        super(ModMenuTypes.PREVIEW_MENU.get(), id);
        this.title = title;
        this.container = new SimpleContainer(items.size());
        for (int i = 0; i < items.size(); i++) {
            this.container.setItem(i, items.get(i));
        }
        setupSlots(playerInv);
    }

    private void setupSlots(Inventory playerInv) {
        for (int i = 0; i < this.container.getContainerSize(); i++) {
            int row = i / 9;
            int col = i % 9;
            this.addSlot(new DisplaySlot(this.container, i, 8 + col * 18, 18 + row * 18));
        }

        int playerInvY = 84;
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInv, j + i * 9 + 9, 8 + j * 18, playerInvY + i * 18));
            }
        }
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInv, i, 8 + i * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
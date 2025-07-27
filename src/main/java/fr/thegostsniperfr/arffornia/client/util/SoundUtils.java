package fr.thegostsniperfr.arffornia.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A utility class for handling client-side sound effects.
 * This ensures sound-playing logic is centralized and easy to reuse.
 */
@OnlyIn(Dist.CLIENT)
public class SoundUtils {

    /**
     * Plays the standard UI button click sound.
     * This is a versatile sound effect for most UI interactions.
     */
    public static void playClickSound() {
        // We use SimpleSoundInstance.forUI() which is the correct and easy way to play UI sounds.
        // SoundEvents.UI_BUTTON_CLICK is the default Minecraft sound for button clicks.
        // The second parameter '1.0F' is the pitch, 1.0F being the default.
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
        );
    }
}
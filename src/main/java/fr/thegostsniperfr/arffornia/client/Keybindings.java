package fr.thegostsniperfr.arffornia.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class Keybindings {
    public static final KeyMapping OPEN_GRAPH_KEY = new KeyMapping(
            "key.arffornia.open_graph",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.category.arffornia"
    );
}
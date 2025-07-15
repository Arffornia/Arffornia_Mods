// src/main/java/fr/thegostsniperfr/arffornia/client/Keybindings.java
package fr.thegostsniperfr.arffornia.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class Keybindings {
    public static final KeyMapping OPEN_GRAPH_KEY = new KeyMapping(
            "key.arffornia.open_graph", // Nom de la clé pour les traductions
            KeyConflictContext.IN_GAME, // Contexte (uniquement en jeu)
            InputConstants.Type.KEYSYM, // Type d'input (clavier)
            GLFW.GLFW_KEY_G,            // Touche par défaut (G)
            "key.category.arffornia"    // Catégorie dans le menu des contrôles
    );
}
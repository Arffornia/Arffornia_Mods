package fr.thegostsniperfr.arffornia.client.web;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Key presses for the page as AWT events that carry their character. WebGUI 2.0.0 sends every key press with
 * {@link KeyEvent#CHAR_UNDEFINED}; JCEF on Linux derives the DOM key from that character, and 0xFFFF is the X keysym
 * of Delete, so every printable key reached the page as {@code "Delete"} and shortcuts like {@code T} never fired.
 */
final class PageKeys {
    private PageKeys() {
    }

    /** The character of a printable key from its layout name ({@code glfwGetKeyName}), or CHAR_UNDEFINED. */
    static char keyChar(@Nullable String keyName, int glfwMods) {
        if (keyName == null || keyName.length() != 1) {
            return KeyEvent.CHAR_UNDEFINED;
        }
        char c = keyName.charAt(0);
        return (glfwMods & GLFW.GLFW_MOD_SHIFT) != 0 ? Character.toUpperCase(c) : c;
    }

    static int awtKeyCode(int glfwKey) {
        if (glfwKey >= GLFW.GLFW_KEY_KP_0 && glfwKey <= GLFW.GLFW_KEY_KP_9) {
            return KeyEvent.VK_NUMPAD0 + glfwKey - GLFW.GLFW_KEY_KP_0;
        }
        return switch (glfwKey) {
            case GLFW.GLFW_KEY_APOSTROPHE -> KeyEvent.VK_QUOTE;
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> KeyEvent.VK_BACK_QUOTE;
            case GLFW.GLFW_KEY_KP_DECIMAL -> KeyEvent.VK_DECIMAL;
            case GLFW.GLFW_KEY_KP_DIVIDE -> KeyEvent.VK_DIVIDE;
            case GLFW.GLFW_KEY_KP_MULTIPLY -> KeyEvent.VK_MULTIPLY;
            case GLFW.GLFW_KEY_KP_SUBTRACT -> KeyEvent.VK_SUBTRACT;
            case GLFW.GLFW_KEY_KP_ADD -> KeyEvent.VK_ADD;
            case GLFW.GLFW_KEY_KP_EQUAL -> KeyEvent.VK_EQUALS;
            default -> glfwKey;
        };
    }

    static int awtModifiers(int glfwMods) {
        int awt = 0;
        if ((glfwMods & GLFW.GLFW_MOD_SHIFT) != 0) {
            awt |= InputEvent.SHIFT_DOWN_MASK;
        }
        if ((glfwMods & GLFW.GLFW_MOD_CONTROL) != 0) {
            awt |= InputEvent.CTRL_DOWN_MASK;
        }
        if ((glfwMods & GLFW.GLFW_MOD_ALT) != 0) {
            awt |= InputEvent.ALT_DOWN_MASK;
        }
        if ((glfwMods & GLFW.GLFW_MOD_SUPER) != 0) {
            awt |= InputEvent.META_DOWN_MASK;
        }
        return awt;
    }
}

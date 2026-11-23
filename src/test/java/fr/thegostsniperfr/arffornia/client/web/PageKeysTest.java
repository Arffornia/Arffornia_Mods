package fr.thegostsniperfr.arffornia.client.web;

import org.junit.jupiter.api.Test;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageKeysTest {
    // LWJGL is not on the unit test classpath; these are GLFW's values.
    private static final int GLFW_MOD_SHIFT = 0x1;
    private static final int GLFW_MOD_CONTROL = 0x2;
    private static final int GLFW_KEY_T = 84;
    private static final int GLFW_KEY_KP_7 = 327;

    @Test
    void aPrintableKeyCarriesItsLayoutCharacter() {
        assertEquals('t', PageKeys.keyChar("t", 0));
    }

    @Test
    void shiftGivesTheUpperCaseCharacter() {
        assertEquals('T', PageKeys.keyChar("t", GLFW_MOD_SHIFT));
    }

    @Test
    void aKeyWithoutPrintableNameHasNoCharacter() {
        assertEquals(KeyEvent.CHAR_UNDEFINED, PageKeys.keyChar(null, 0));
    }

    @Test
    void lettersKeepTheirCodeAndKeypadDigitsMapToNumpadCodes() {
        assertEquals(KeyEvent.VK_T, PageKeys.awtKeyCode(GLFW_KEY_T));
        assertEquals(KeyEvent.VK_NUMPAD7, PageKeys.awtKeyCode(GLFW_KEY_KP_7));
    }

    @Test
    void controlReachesThePageForShortcutsLikeCtrlK() {
        assertEquals(InputEvent.CTRL_DOWN_MASK, PageKeys.awtModifiers(GLFW_MOD_CONTROL));
    }
}

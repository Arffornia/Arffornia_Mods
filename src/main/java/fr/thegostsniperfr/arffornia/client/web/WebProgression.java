package fr.thegostsniperfr.arffornia.client.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.config.ClientConfig;
import fr.thegostsniperfr.arffornia.network.ServerPayloadChecks;
import land.webgui.browser.WebBrowser;
import net.minecraft.client.Minecraft;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.browser.OffscreenBrowser;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.awt.event.KeyEvent;
import java.lang.reflect.Method;

/**
 * The website's progression viewer in WebGUI's embedded Chromium (spec 018 D2 a). One browser for the session,
 * loaded off-screen once the server sent the page URL and kept at {@link #HIDDEN_FPS} while the screen is closed
 * (the bundled JCEF has no "was hidden"; 1 fps already brings it under 1 % of a core). Closed on leaving the server.
 * WebGUI is a required client dependency; when its runtime fails the screen shows an error instead.
 * <p>
 * The page talks to the game through {@code window.arfforniaQuery}, a CEF message router of its own (WebGUI's
 * {@code cefQuery} can run commands for trusted origins; this one cannot), answered only for the page's origin.
 */
public final class WebProgression {
    static final int HIDDEN_FPS = 1;

    @Nullable private static WebBrowser browser;
    @Nullable private static CefMessageRouter router;
    private static boolean bridgeInstalled;
    @Nullable private static Method offscreenOf;
    private static boolean offscreenUnavailable;

    private WebProgression() {
    }

    /** From client setup: installs the bridge once the browser runtime is up. */
    public static void init() {
        ProgressionWebSession.INSTANCE.onUrl(WebProgression::reload);
        WebBrowser.addListener(new WebBrowser.Listener() {
            @Override
            public void onLoadEnd(WebBrowser loaded, String url, int status) {
                Minecraft.getInstance().execute(() -> {
                    if (loaded == browser) {
                        applyZoom(loaded);
                    }
                });
            }
        });
        WebBrowser.whenReady(() -> Minecraft.getInstance().execute(WebProgression::installBridge));
    }

    /** False when the browser runtime failed to start (missing system libraries, no download). */
    public static boolean usable() {
        return WebBrowser.usable();
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new WebProgressionScreen());
    }

    /** On joining a server: get the page URL and a token, so the first open is already rendered. */
    public static void prewarm() {
        ProgressionWebSession.INSTANCE.requestSession(false, session -> {
        });
    }

    /** On leaving a server. */
    public static void release() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
    }

    @Nullable
    static WebBrowser browser() {
        ensureBrowser();
        return browser;
    }

    static void shown(int pixelWidth, int pixelHeight) {
        if (ProgressionWebSession.INSTANCE.url() == null) {
            prewarm();
        }
        WebBrowser current = browser();
        if (current == null) {
            return;
        }
        current.resize(pixelWidth, pixelHeight);
        applyZoom(current);
        current.setWindowlessFrameRate(ClientConfig.WEB_FPS.get());
        current.setFocus(true);
        current.executeJavaScript("window.dispatchEvent(new Event('arffornia:shown'))", "", 0);
    }

    static void hidden() {
        if (browser != null) {
            browser.setFocus(false);
            browser.setWindowlessFrameRate(HIDDEN_FPS);
        }
    }

    /** Chromium keeps the zoom per host and drops one set before the page loaded, hence also on every load end. */
    private static void applyZoom(WebBrowser target) {
        var window = Minecraft.getInstance().getWindow();
        target.setZoomLevel(PageZoom.level(PageZoom.factor(window.getGuiScale(), window.getWidth(), window.getHeight())));
    }

    /** True when the key went to the page with its character; false leaves it to WebGUI's own forwarding. */
    static boolean sendPrintableKey(WebBrowser target, boolean pressed, int glfwKey, int scanCode, int glfwMods) {
        char keyChar = PageKeys.keyChar(GLFW.glfwGetKeyName(glfwKey, scanCode), glfwMods);
        if (keyChar == KeyEvent.CHAR_UNDEFINED) {
            return false;
        }
        OffscreenBrowser offscreen = offscreen(target);
        if (offscreen == null) {
            return false;
        }
        offscreen.send(new KeyEvent(offscreen.eventSource(), pressed ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                System.currentTimeMillis(), PageKeys.awtModifiers(glfwMods), PageKeys.awtKeyCode(glfwKey), keyChar));
        return true;
    }

    /** WebGUI 2.0.0 has no public way to send a key event with its character, so reach its JCEF browser. */
    @Nullable
    private static OffscreenBrowser offscreen(WebBrowser target) {
        Object view = target.view();
        if (view == null || offscreenUnavailable) {
            return null;
        }
        try {
            if (offscreenOf == null) {
                Method method = view.getClass().getDeclaredMethod("browser");
                method.setAccessible(true);
                offscreenOf = method;
            }
            return offscreenOf.invoke(view) instanceof OffscreenBrowser offscreen ? offscreen : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            offscreenUnavailable = true;
            Arffornia.LOGGER.warn("Progression viewer: cannot send typed keys to the page, shortcuts may not work ({})", e.toString());
            return null;
        }
    }

    private static void installBridge() {
        if (bridgeInstalled) {
            return;
        }
        router = CefMessageRouter.create(new CefMessageRouter.CefMessageRouterConfig("arfforniaQuery", "arfforniaQueryCancel"));
        router.addHandler(new Bridge(), true);
        WebBrowser.client().addMessageRouter(router);
        bridgeInstalled = true;
        ensureBrowser();
    }

    /** A new server URL: drop the page loaded for the previous one. */
    private static void reload() {
        release();
        ensureBrowser();
    }

    private static void ensureBrowser() {
        String url = ProgressionWebSession.INSTANCE.url();
        if (browser != null || !bridgeInstalled || url == null) {
            return;
        }
        var window = Minecraft.getInstance().getWindow();
        browser = WebBrowser.create(url + (url.contains("?") ? "&" : "?") + "locale=" + ProgressionWebSession.pageLocale(), false);
        browser.resize(Math.max(1, window.getWidth()), Math.max(1, window.getHeight()));
        browser.setWindowlessFrameRate(HIDDEN_FPS);
    }

    private static final class Bridge extends CefMessageRouterHandlerAdapter {
        @Override
        public boolean onQuery(CefBrowser cefBrowser, CefFrame frame, long queryId, String request, boolean persistent, CefQueryCallback callback) {
            String origin = frame != null && frame.isMain() ? ProgressionWebSession.originOf(frame.getURL()) : null;
            Minecraft.getInstance().execute(() -> answer(origin, request, callback));
            return true;
        }
    }

    private static void answer(@Nullable String origin, String request, CefQueryCallback callback) {
        ProgressionWebSession session = ProgressionWebSession.INSTANCE;
        if (origin == null || !origin.equals(session.origin())) {
            callback.failure(403, "untrusted origin");
            return;
        }
        JsonObject query;
        try {
            JsonElement parsed = request == null || request.length() > 4096 ? null : JsonParser.parseString(request);
            query = parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            query = null;
        }
        String type = query != null && query.has("type") && query.get("type").isJsonPrimitive() ? query.get("type").getAsString() : "";
        switch (type) {
            case "session" -> session.requestSession(query.has("renew") && query.get("renew").isJsonPrimitive() && query.get("renew").getAsBoolean(),
                    answer -> answer.ifPresentOrElse(s -> {
                        JsonObject reply = new JsonObject();
                        reply.addProperty("token", s.token());
                        reply.addProperty("expires_at", s.expiresAt());
                        callback.success(reply.toString());
                    }, () -> callback.failure(503, "no session")));
            case "target" -> {
                JsonElement milestone = query.get("milestone");
                String key = milestone == null || milestone.isJsonNull() || !milestone.isJsonPrimitive() ? null : milestone.getAsString();
                if (key != null && !ServerPayloadChecks.isMilestoneKey(key)) {
                    callback.failure(400, "bad milestone");
                    return;
                }
                session.requestTarget(key, accepted -> {
                    if (accepted) {
                        callback.success("{\"ok\":true}");
                    } else {
                        callback.failure(409, "refused");
                    }
                });
            }
            default -> callback.failure(400, "unknown request");
        }
    }
}

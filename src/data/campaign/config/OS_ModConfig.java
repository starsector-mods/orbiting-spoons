package data.campaign.config;

import java.io.IOException;
import org.json.JSONException;
import org.json.JSONObject;

import com.fs.starfarer.api.Global;

public class OS_ModConfig {

    public static final String MOD_ID = "orbiting_spoons";
    public static final String CONFIG_PATH = "data/config/orbiting_spoons_config.json";

    public static final String STYLE_COMPACT = "Compact";
    public static final String STYLE_MINIMAL = "Minimal";
    public static final String STYLE_FULL = "Full";
    public static final String STYLE_OFF = "Off";

    private static String cachedHudStyle = null;
    private static boolean cachedWarnLow = true;
    private static boolean cachedSmoothFade = true;
    private static long lastCheckMs = 0L;

    public static boolean isLunaLibEnabled() {
        if (Global.getSettings() == null || Global.getSettings().getModManager() == null) return false;
        return Global.getSettings().getModManager().isModEnabled("lunalib");
    }

    public static synchronized void refreshConfig() {
        if (isLunaLibEnabled()) {
            cachedHudStyle = OS_LunaSettingsBridge.getString(MOD_ID, "os_hudStatusStyle", STYLE_COMPACT);
            cachedWarnLow = OS_LunaSettingsBridge.getBoolean(MOD_ID, "os_warnLowDuration", true);
            cachedSmoothFade = OS_LunaSettingsBridge.getBoolean(MOD_ID, "os_smoothFadeOnExpiry", true);
            return;
        }

        // Fallback to orbiting_spoons_config.json
        try {
            if (Global.getSettings() != null) {
                JSONObject json = Global.getSettings().loadJSON(CONFIG_PATH);
                if (json != null) {
                    cachedHudStyle = json.optString("hudStatusStyle", STYLE_COMPACT);
                    cachedWarnLow = json.optBoolean("warnLowDuration", true);
                    cachedSmoothFade = json.optBoolean("smoothFadeOnExpiry", true);
                    return;
                }
            }
        } catch (IOException | JSONException ignored) {}

        // Safe defaults
        cachedHudStyle = STYLE_COMPACT;
        cachedWarnLow = true;
        cachedSmoothFade = true;
    }

    public static String getHudStatusStyle() {
        checkThrottle();
        return cachedHudStyle != null ? cachedHudStyle : STYLE_COMPACT;
    }

    public static boolean isWarnLowDuration() {
        checkThrottle();
        return cachedWarnLow;
    }

    public static boolean isSmoothFadeOnExpiry() {
        checkThrottle();
        return cachedSmoothFade;
    }

    private static void checkThrottle() {
        long now = System.currentTimeMillis();
        if (cachedHudStyle == null || (now - lastCheckMs > 1000L)) {
            lastCheckMs = now;
            refreshConfig();
        }
    }
}

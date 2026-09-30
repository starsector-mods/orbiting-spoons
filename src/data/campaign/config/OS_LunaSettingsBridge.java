package data.campaign.config;

import lunalib.lunaSettings.LunaSettings;

/**
 * Isolated bridge to LunaLib's API.
 * This class is only ever touched at runtime if LunaLib is confirmed enabled in the mod manager.
 */
public class OS_LunaSettingsBridge {

    public static String getString(String modId, String fieldId, String defaultVal) {
        try {
            String val = LunaSettings.getString(modId, fieldId);
            return val != null ? val : defaultVal;
        } catch (Throwable t) {
            return defaultVal;
        }
    }

    public static boolean getBoolean(String modId, String fieldId, boolean defaultVal) {
        try {
            Boolean val = LunaSettings.getBoolean(modId, fieldId);
            return val != null ? val : defaultVal;
        } catch (Throwable t) {
            return defaultVal;
        }
    }
}

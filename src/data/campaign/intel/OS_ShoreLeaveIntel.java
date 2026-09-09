package data.campaign.intel;

import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;

/**
 * @deprecated Deprecated as of Orbiting Spoon update.
 * Shore leave and fleet morale are now unified exclusively in {@link OS_SpoonDirectoryIntel}.
 * This class is preserved solely for legacy save-game deserialization and automatically self-purges.
 */
@Deprecated
public class OS_ShoreLeaveIntel extends BaseIntelPlugin {

    public OS_ShoreLeaveIntel() {
        this("independent", 14f);
    }

    public OS_ShoreLeaveIntel(String factionId, float initialDuration) {
        endAfterDelay(0f);
    }

    @Override
    public boolean shouldRemoveIntel() {
        return true;
    }

    @Override
    public boolean isEnded() {
        return true;
    }

    @Override
    public boolean isEnding() {
        return true;
    }

    public static void syncIntel(String factionId, float durationDays, boolean notify) {
        cleanupLegacyIntel();
    }

    public static void endIntel() {
        cleanupLegacyIntel();
    }

    public static void cleanupLegacyIntel() {
        if (Global.getSector() == null || Global.getSector().getIntelManager() == null) return;
        IntelManagerAPI im = Global.getSector().getIntelManager();
        List<IntelInfoPlugin> existing = im.getIntel(OS_ShoreLeaveIntel.class);
        if (existing != null) {
            for (IntelInfoPlugin item : existing) {
                im.removeIntel(item);
            }
        }
    }
}


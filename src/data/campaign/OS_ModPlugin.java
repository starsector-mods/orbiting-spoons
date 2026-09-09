package data.campaign;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.thoughtworks.xstream.XStream;
import data.campaign.buffs.OS_ShoreLeaveBuff;
import data.campaign.intel.OS_ShoreLeaveIntel;
import data.campaign.intel.OS_SpoonDirectoryIntel;

public class OS_ModPlugin extends BaseModPlugin {

    /**
     * Called before save deserialization. Registers XStream aliases so that
     * saves created with ANY prior version of the mod can always find our classes,
     * even if we refactored packages or renamed things.
     */
    @Override
    public void configureXStream(XStream x) {
        x.alias("OS_SpoonDirectoryIntel", OS_SpoonDirectoryIntel.class);
        x.alias("OS_SpoonDirectoryIntel$DirectoryTab", OS_SpoonDirectoryIntel.DirectoryTab.class);
        x.alias("OS_ShoreLeaveBuff", OS_ShoreLeaveBuff.class);
        x.alias("OS_ShoreLeaveIntel", OS_ShoreLeaveIntel.class);
        x.alias("OS_OfficerFriendship", OS_OfficerFriendship.class);
        // Alias with full qualified names too so old saves with FQN references still work
        x.aliasPackage("data.campaign", "data.campaign");
    }

    @Override
    public void onGameLoad(boolean newGame) {
        super.onGameLoad(newGame);

        // Purge obsolete Shore Leave intel entries from older mod versions
        if (Global.getSector() != null && Global.getSector().getIntelManager() != null) {
            IntelManagerAPI im = Global.getSector().getIntelManager();
            List<IntelInfoPlugin> shoreLeaveIntels = im.getIntel(OS_ShoreLeaveIntel.class);
            if (shoreLeaveIntels != null) {
                for (IntelInfoPlugin intel : new ArrayList<>(shoreLeaveIntels)) {
                    im.removeIntel(intel);
                }
            }
        }

        // Sync buff state from save memory keys
        OS_ShoreLeaveBuff.syncOnLoad();

        // Ensure the directory intel exists (adds if missing, safe if already present)
        OS_SpoonDirectoryIntel.addIntelIfNeeded();
    }

    @Override
    public void onNewGameAfterTimePass() {
        OS_SpoonDirectoryIntel.addIntelIfNeeded();
    }
}

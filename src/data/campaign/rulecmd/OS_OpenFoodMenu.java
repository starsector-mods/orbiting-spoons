package data.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import data.campaign.ui.OS_CustomFoodDialogDelegate;

/**
 * Command plugin to open the graphical Custom Food Menu dialog.
 */
public class OS_OpenFoodMenu extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        float width = 840f;
        float height = 600f;
        dialog.showCustomDialog(width, height, new OS_CustomFoodDialogDelegate(dialog, memoryMap));
        return true;
    }
}

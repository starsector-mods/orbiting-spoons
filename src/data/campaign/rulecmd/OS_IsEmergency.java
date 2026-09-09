package data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

public class OS_IsEmergency extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null || Global.getSector().getPlayerFleet().getCargo() == null) {
            return false;
        }
        float supplies = Global.getSector().getPlayerFleet().getCargo().getSupplies();
        float fuel = Global.getSector().getPlayerFleet().getCargo().getFuel();
        
        MemoryAPI mem = memoryMap != null ? memoryMap.get(MemKeys.PLAYER) : null;
        if (mem != null && mem.contains("$os_emergency_cooldown")) {
            return false;
        }

        // Trigger if either supplies or fuel are critically low (<= 10)
        return (supplies <= 10f || fuel <= 10f);
    }
}

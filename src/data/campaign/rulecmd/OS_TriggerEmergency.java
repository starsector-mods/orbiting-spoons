package data.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

public class OS_TriggerEmergency extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null || Global.getSector().getPlayerFleet().getCargo() == null) {
            return false;
        }
        
        // Grant emergency package
        Global.getSector().getPlayerFleet().getCargo().addSupplies(20f);
        Global.getSector().getPlayerFleet().getCargo().addFuel(20f);
        
        // Setup text printout
        if (dialog != null && dialog.getTextPanel() != null) {
            dialog.getTextPanel().setFontSmallInsignia();
            dialog.getTextPanel().addParagraph("Added 20 Supplies and 20 Fuel to cargo holds.", Misc.getPositiveHighlightColor());
            dialog.getTextPanel().setFontInsignia();
        }

        // Add visual text notification
        if (Global.getSector().getCampaignUI() != null) {
            Global.getSector().getCampaignUI().addMessage("Received Emergency Logistics Package.", Misc.getPositiveHighlightColor());
        }

        // Set cooldown for 60 days
        if (memoryMap != null) {
            MemoryAPI mem = memoryMap.get(MemKeys.PLAYER);
            if (mem != null) {
                mem.set("$os_emergency_cooldown", true, 60f);
            }
        }

        return true;
    }
}

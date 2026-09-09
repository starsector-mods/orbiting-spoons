package data.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import data.campaign.buffs.OS_ShoreLeaveBuff;

public class OS_PayForMeal extends BaseCommandPlugin {
    
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null || params.isEmpty()) return false;
        
        int basePrice = params.get(0).getInt(memoryMap);
        int finalPrice = -1;
        
        // Check local, global, entity, and sector memories
        if (memoryMap != null) {
            MemoryAPI local = memoryMap.get(MemKeys.LOCAL);
            if (local != null && local.contains("$os_priceNum_" + basePrice)) {
                finalPrice = local.getInt("$os_priceNum_" + basePrice);
            }
            if (finalPrice < 0) {
                MemoryAPI global = memoryMap.get(MemKeys.GLOBAL);
                if (global != null && global.contains("$os_priceNum_" + basePrice)) {
                    finalPrice = global.getInt("$os_priceNum_" + basePrice);
                }
            }
            if (finalPrice < 0) {
                MemoryAPI entity = memoryMap.get(MemKeys.ENTITY);
                if (entity != null && entity.contains("$os_priceNum_" + basePrice)) {
                    finalPrice = entity.getInt("$os_priceNum_" + basePrice);
                }
            }
        }
        
        if (finalPrice < 0) {
            MemoryAPI sectorMem = Global.getSector().getMemoryWithoutUpdate();
            if (sectorMem != null && sectorMem.contains("$os_priceNum_" + basePrice)) {
                finalPrice = sectorMem.getInt("$os_priceNum_" + basePrice);
            }
        }
        
        // Dynamic calculation fallback if memory was somehow lost
        if (finalPrice < 0) {
            MemoryAPI mem = memoryMap != null ? memoryMap.get(MemKeys.LOCAL) : null;
            if (mem != null && mem.contains("$os_multiplierNum")) {
                int mult = mem.getInt("$os_multiplierNum");
                finalPrice = basePrice * mult;
            } else {
                finalPrice = basePrice;
            }
        }
        
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null || Global.getSector().getPlayerFleet().getCargo() == null) {
            return false;
        }

        float currentCredits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        if (currentCredits >= finalPrice) {
            Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(finalPrice);
            AddRemoveCommodity.addCreditsLossText(finalPrice, dialog.getTextPanel());
        } else {
            Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(currentCredits);
            if ((int) currentCredits > 0) {
                AddRemoveCommodity.addCreditsLossText((int) currentCredits, dialog.getTextPanel());
            }
        }

        // Extract faction parameter if provided, else fallback to market faction
        String factionId = null;
        if (params.size() > 1) {
            factionId = params.get(1).getString(memoryMap);
        }
        if (factionId == null || factionId.isEmpty()) {
            factionId = OS_PickRandomOfficer.getFactionId(dialog, memoryMap);
        }

        // Grant Shore Leave & Well-Fed buff on paid meals
        float duration = "independent".equalsIgnoreCase(factionId) ? OS_ShoreLeaveBuff.INDIE_DURATION : OS_ShoreLeaveBuff.DEFAULT_DURATION;
        OS_ShoreLeaveBuff.applyBuff(dialog, factionId, duration);

        // Award officer camaraderie based on personality and mealset
        com.fs.starfarer.api.characters.PersonAPI officer = OS_OfficerMentoring.getActiveOfficer(memoryMap);
        if (officer != null) {
            String reaction = data.campaign.OS_OfficerFriendship.getMealReactionText(officer, factionId);
            if (!reaction.isEmpty()) {
                dialog.getTextPanel().addParagraph(reaction, Misc.getTextColor());
            }
            int delta = data.campaign.OS_OfficerFriendship.getMealCamaraderieDelta(officer, factionId);
            data.campaign.OS_OfficerFriendship.addFriendship(officer, delta, dialog);
        }

        // Award Dining Experience (Officer XP + Fleet Bonus XP or Crew Bonus XP)
        OS_OfficerMentoring.awardDiningExperience(dialog, officer);
        
        return true;
    }
}

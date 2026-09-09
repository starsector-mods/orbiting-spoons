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
        int multiplier = 1;
        if (memoryMap != null && memoryMap.get(MemKeys.LOCAL) != null && memoryMap.get(MemKeys.LOCAL).contains("$os_multiplierNum")) {
            multiplier = memoryMap.get(MemKeys.LOCAL).getInt("$os_multiplierNum");
        }
        if (multiplier <= 0) multiplier = 1;
        int finalPrice = basePrice * multiplier;
        
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null || Global.getSector().getPlayerFleet().getCargo() == null) {
            return false;
        }

        if (OS_ShoreLeaveBuff.isDigesting()) {
            float digRemain = OS_ShoreLeaveBuff.getDigestionDaysRemaining();
            String activeMeal = OS_ShoreLeaveBuff.getMealName(OS_ShoreLeaveBuff.getActiveFaction());
            dialog.getTextPanel().addParagraph(
                "Your crew is still digesting leftover " + activeMeal + " (" + String.format("%.1f", digRemain) + " days remaining). Satiated spacers cannot stomach another full meal spread.",
                Misc.getNegativeHighlightColor()
            );
            return true;
        }

        float currentCredits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        if (currentCredits < finalPrice) {
            dialog.getTextPanel().addParagraph(
                "You cannot afford this meal (Requires " + String.format("%,d", finalPrice) + " credits; fleet purser has " + String.format("%,d", (long) currentCredits) + ").",
                Misc.getNegativeHighlightColor()
            );
            return true;
        }

        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(finalPrice);
        AddRemoveCommodity.addCreditsLossText(finalPrice, dialog.getTextPanel());

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

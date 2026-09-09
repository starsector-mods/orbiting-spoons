package data.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import data.campaign.OS_OfficerFriendship;
import data.campaign.buffs.OS_ShoreLeaveBuff;

public class OS_CulinaryFeast extends BaseCommandPlugin {

    public static final int LOBSTER_COST = 1;
    public static final int LUXURY_COST = 1;
    public static final int FAMINE_RELIEF_FOOD = 10;

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null || params == null || params.isEmpty()) return false;

        String action = params.get(0).getString(memoryMap);
        CampaignFleetAPI playerFleet = Global.getSector() != null ? Global.getSector().getPlayerFleet() : null;
        if (playerFleet == null || playerFleet.getCargo() == null) return false;

        PersonAPI officer = OS_OfficerMentoring.getActiveOfficer(memoryMap);

        if ("order_lobster".equals(action)) {
            float available = playerFleet.getCargo().getCommodityQuantity(Commodities.LOBSTER);
            if (available < LOBSTER_COST) {
                dialog.getTextPanel().addParagraph("You do not have enough Volturnian Lobster in your cargo hold.", Misc.getNegativeHighlightColor());
                return true;
            }

            playerFleet.getCargo().removeCommodity(Commodities.LOBSTER, LOBSTER_COST);
            AddRemoveCommodity.addCommodityLossText(Commodities.LOBSTER, LOBSTER_COST, dialog.getTextPanel());

            dialog.getTextPanel().addParagraph(
                "The portside kitchen goes silent as your crew wheels in a pressurized cryo-crate of live blue-shell Volturnian Lobster. Within an hour, the table groans under platters of butter-poached tails, spiced claw meat, and steaming broth. Your officers feast like Persean archons.",
                Misc.getTextColor()
            );

            OS_ShoreLeaveBuff.applyBuff(dialog, "lobster_feast", 21f);

            if (officer != null) {
                String reaction = OS_OfficerFriendship.getMealReactionText(officer, "lobster_feast");
                if (!reaction.isEmpty()) {
                    dialog.getTextPanel().addParagraph(reaction, Misc.getTextColor());
                }
                List<PersonAPI> allOfficers = OS_PickRandomOfficer.getHumanOfficers();
                if (allOfficers.isEmpty()) {
                    int delta = OS_OfficerFriendship.getMealCamaraderieDelta(officer, "lobster_feast");
                    OS_OfficerFriendship.addFriendship(officer, delta, dialog);
                } else {
                    for (PersonAPI p : allOfficers) {
                        int delta = OS_OfficerFriendship.getMealCamaraderieDelta(p, "lobster_feast");
                        OS_OfficerFriendship.addFriendship(p, delta, dialog);
                    }
                }
            }

            OS_OfficerMentoring.awardDiningExperience(dialog, officer);
            return true;
        }

        if ("order_luxury".equals(action)) {
            float available = playerFleet.getCargo().getCommodityQuantity(Commodities.LUXURY_GOODS);
            if (available < LUXURY_COST) {
                dialog.getTextPanel().addParagraph("You do not have enough Luxury Goods in your cargo hold.", Misc.getNegativeHighlightColor());
                return true;
            }

            playerFleet.getCargo().removeCommodity(Commodities.LUXURY_GOODS, LUXURY_COST);
            AddRemoveCommodity.addCommodityLossText(Commodities.LUXURY_GOODS, LUXURY_COST, dialog.getTextPanel());

            dialog.getTextPanel().addParagraph(
                "You unseal an air-locked crate of pre-Collapse luxury goods: century-aged vintages, cured delicacies, and vacuum-sealed spices. The rich, forgotten aromas draw envious glances across the bar as your command staff toasts to the fleet's fortune.",
                Misc.getTextColor()
            );

            OS_ShoreLeaveBuff.applyBuff(dialog, "luxury_feast", 21f);

            if (officer != null) {
                String reaction = OS_OfficerFriendship.getMealReactionText(officer, "luxury_feast");
                if (!reaction.isEmpty()) {
                    dialog.getTextPanel().addParagraph(reaction, Misc.getTextColor());
                }
                List<PersonAPI> allOfficers = OS_PickRandomOfficer.getHumanOfficers();
                if (allOfficers.isEmpty()) {
                    int delta = OS_OfficerFriendship.getMealCamaraderieDelta(officer, "luxury_feast");
                    OS_OfficerFriendship.addFriendship(officer, delta, dialog);
                } else {
                    for (PersonAPI p : allOfficers) {
                        int delta = OS_OfficerFriendship.getMealCamaraderieDelta(p, "luxury_feast");
                        OS_OfficerFriendship.addFriendship(p, delta, dialog);
                    }
                }
            }

            OS_OfficerMentoring.awardDiningExperience(dialog, officer);
            return true;
        }

        if ("donate_famine".equals(action)) {
            float available = playerFleet.getCargo().getCommodityQuantity(Commodities.FOOD);
            if (available < FAMINE_RELIEF_FOOD) {
                dialog.getTextPanel().addParagraph("You do not have enough bulk Food in your cargo hold.", Misc.getNegativeHighlightColor());
                return true;
            }

            playerFleet.getCargo().removeCommodity(Commodities.FOOD, FAMINE_RELIEF_FOOD);
            AddRemoveCommodity.addCommodityLossText(Commodities.FOOD, FAMINE_RELIEF_FOOD, dialog.getTextPanel());

            dialog.getTextPanel().addParagraph(
                "Pallets of bulk grain and preserved ration bricks are rolled into the cantina's depleted pantry. Exhausted dockworkers and station staff bow in profound gratitude. The cooks immediately fire up the industrial pressure cauldrons to brew a massive communal stew.",
                Misc.getTextColor()
            );

            MarketAPI market = dialog.getInteractionTarget() != null ? dialog.getInteractionTarget().getMarket() : null;
            if (market != null && market.getFaction() != null) {
                CoreReputationPlugin.CustomRepImpact impact = new CoreReputationPlugin.CustomRepImpact();
                impact.delta = 0.05f;
                Global.getSector().adjustPlayerReputation(
                    new CoreReputationPlugin.RepActionEnvelope(CoreReputationPlugin.RepActions.CUSTOM, impact, null, dialog.getTextPanel(), true),
                    market.getFactionId()
                );
            }

            OS_ShoreLeaveBuff.applyBuff(dialog, "famine_stew", 14f);

            if (officer != null) {
                String reaction = OS_OfficerFriendship.getMealReactionText(officer, "famine_stew");
                if (!reaction.isEmpty()) {
                    dialog.getTextPanel().addParagraph(reaction, Misc.getTextColor());
                }
                List<PersonAPI> allOfficers = OS_PickRandomOfficer.getHumanOfficers();
                if (allOfficers.isEmpty()) {
                    int delta = OS_OfficerFriendship.getMealCamaraderieDelta(officer, "famine_stew");
                    OS_OfficerFriendship.addFriendship(officer, delta, dialog);
                } else {
                    for (PersonAPI p : allOfficers) {
                        int delta = OS_OfficerFriendship.getMealCamaraderieDelta(p, "famine_stew");
                        OS_OfficerFriendship.addFriendship(p, delta, dialog);
                    }
                }
            }

            OS_OfficerMentoring.awardDiningExperience(dialog, officer);
            return true;
        }

        return false;
    }
}

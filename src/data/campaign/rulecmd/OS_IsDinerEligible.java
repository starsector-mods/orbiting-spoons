package data.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

/**
 * Bulletproof condition checker for whether a market qualifies to host 'The Orbiting Spoon':
 * 1. Must have a valid MarketAPI.
 * 2. Market size must be 4 or greater.
 * 3. Market must not be hidden.
 * 4. Faction must be a recognized major vanilla faction.
 */
public class OS_IsDinerEligible extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MarketAPI market = getMarket(dialog, memoryMap);
        if (market == null) return false;

        // 1. Must not be hidden
        if (market.isHidden()) return false;

        // 2. Market Size 4+ requirement
        if (market.getSize() < 4) return false;

        // 3. Major Vanilla Factions only
        String factionId = market.getFactionId();
        if (factionId == null || factionId.isEmpty()) {
            if (market.getFaction() != null) {
                factionId = market.getFaction().getId();
            }
        }
        if (factionId == null) return false;

        return OS_IsVanillaFaction.isVanilla(factionId);
    }

    public static MarketAPI getMarket(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        if (dialog != null) {
            SectorEntityToken target = dialog.getInteractionTarget();
            if (target != null && target.getMarket() != null) {
                return target.getMarket();
            }
        }
        if (memoryMap != null) {
            MemoryAPI marketMem = memoryMap.get(MemKeys.MARKET);
            if (marketMem != null && marketMem.contains("$market")) {
                Object mObj = marketMem.get("$market");
                if (mObj instanceof MarketAPI) {
                    return (MarketAPI) mObj;
                }
            }
        }
        return null;
    }
}

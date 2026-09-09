package data.campaign.rulecmd;

import java.util.List;
import java.util.Map;
import java.util.Arrays;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

public class OS_IsVanillaFaction extends BaseCommandPlugin {
    
    private static final List<String> VANILLA_FACTIONS = Arrays.asList(
        "hegemony", 
        "tritachyon", 
        "sindrian_diktat", 
        "luddic_church", 
        "luddic_path", 
        "persean", 
        "pirates", 
        "independent", 
        "player", 
        "player_npc"
    );

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String factionId = OS_PickRandomOfficer.getFactionId(dialog, memoryMap);
        if (factionId == null) return false;
        return VANILLA_FACTIONS.contains(factionId.toLowerCase());
    }
    
    public static boolean isVanilla(String factionId) {
        if (factionId == null) return false;
        return VANILLA_FACTIONS.contains(factionId.toLowerCase());
    }
}

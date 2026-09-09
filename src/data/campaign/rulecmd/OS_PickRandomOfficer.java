package data.campaign.rulecmd;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import data.campaign.OS_OfficerFriendship;
import data.campaign.buffs.OS_ShoreLeaveBuff;

public class OS_PickRandomOfficer extends BaseCommandPlugin {
    
    public static final String KEY_ACTIVE_OFFICER_ID = "$os_activeDiningOfficerId";

    public static class MenuItem {
        public final String optionId;
        public final String name;
        public final int basePrice;

        public MenuItem(String optionId, String name, int basePrice) {
            this.optionId = optionId;
            this.name = name;
            this.basePrice = basePrice;
        }
    }
    
    public static String getFactionId(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        if (memoryMap != null) {
            MemoryAPI factionMem = memoryMap.get(MemKeys.FACTION);
            if (factionMem != null && factionMem.contains("$id")) {
                String id = factionMem.getString("$id");
                if (id != null && !id.isEmpty()) return id;
            }
            MemoryAPI marketMem = memoryMap.get(MemKeys.MARKET);
            if (marketMem != null && marketMem.contains("$factionId")) {
                String id = marketMem.getString("$factionId");
                if (id != null && !id.isEmpty()) return id;
            }
        }
        if (dialog != null && dialog.getInteractionTarget() != null) {
            if (dialog.getInteractionTarget().getMarket() != null) {
                return dialog.getInteractionTarget().getMarket().getFactionId();
            }
            if (dialog.getInteractionTarget().getFaction() != null) {
                return dialog.getInteractionTarget().getFaction().getId();
            }
        }
        return "independent";
    }

    public static List<MenuItem> getMenuItems(String rawFactionId) {
        String factionId = rawFactionId != null ? rawFactionId.toLowerCase().trim() : "independent";
        List<MenuItem> items = new ArrayList<>();
        
        if (factionId.equals("hegemony")) {
            items.add(new MenuItem("orbiting_spoon_order_hegemony_set", "Commissary Auxiliary Rations Set", 150));
        } else if (factionId.equals("tritachyon")) {
            items.add(new MenuItem("orbiting_spoon_order_tritachyon_set", "Executive Synth-Steak Suite", 450));
        } else if (factionId.equals("sindrian_diktat")) {
            items.add(new MenuItem("orbiting_spoon_order_sindrian_set", "Supreme Executor's Volturnian Feast", 600));
        } else if (factionId.equals("luddic_church")) {
            items.add(new MenuItem("orbiting_spoon_order_church_set", "Pilgrim's Hearth Harvest Feast", 200));
        } else if (factionId.equals("luddic_path")) {
            items.add(new MenuItem("orbiting_spoon_order_path_set", "Ascetic Penance Rations Set", 50));
        } else if (factionId.equals("pirates")) {
            items.add(new MenuItem("orbiting_spoon_order_pirates_set", "Fringe Scavenger Platter Set", 100));
        } else if (factionId.equals("persean")) {
            items.add(new MenuItem("orbiting_spoon_order_persean_set", "Archon's Grand Mezze Banquet", 350));
        } else {
            items.add(new MenuItem("orbiting_spoon_order_generic_set", "Loaded Spacer's Full-Burn Diner Set", 200));
        }
        return items;
    }

    public static List<PersonAPI> getHumanOfficers() {
        List<PersonAPI> humanOfficers = new ArrayList<>();
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return humanOfficers;
        List<OfficerDataAPI> allOfficers = Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy();
        if (allOfficers == null) return humanOfficers;
        for (OfficerDataAPI data : allOfficers) {
            PersonAPI person = data.getPerson();
            if (person != null && !person.isAICore()) {
                humanOfficers.add(person);
            }
        }
        return humanOfficers;
    }

    public static PersonAPI getActiveOfficerFromMemory(Map<String, MemoryAPI> memoryMap) {
        String officerId = null;
        if (memoryMap != null) {
            MemoryAPI local = memoryMap.get(MemKeys.LOCAL);
            if (local != null && local.contains(KEY_ACTIVE_OFFICER_ID)) {
                officerId = local.getString(KEY_ACTIVE_OFFICER_ID);
            }
        }
        if (officerId == null && Global.getSector() != null) {
            MemoryAPI sectorMem = Global.getSector().getMemoryWithoutUpdate();
            if (sectorMem != null && sectorMem.contains(KEY_ACTIVE_OFFICER_ID)) {
                officerId = sectorMem.getString(KEY_ACTIVE_OFFICER_ID);
            }
        }
        if (officerId != null) {
            if ("crew".equals(officerId)) return null;
            if (Global.getSector() != null && Global.getSector().getPlayerFleet() != null && Global.getSector().getPlayerFleet().getFleetData() != null) {
                for (OfficerDataAPI data : Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy()) {
                    if (data.getPerson() != null && !data.getPerson().isAICore() && data.getPerson().getId().equals(officerId)) {
                        return data.getPerson();
                    }
                }
            }
        }
        return null;
    }

    public static void populateFoodMenu(InteractionDialogAPI dialog, int multiplier, String factionId) {
        PersonAPI officer = getActiveOfficerFromMemory(null);
        int officerCount = getHumanOfficers().size();
        populateFoodMenu(dialog, multiplier, factionId, officer, officerCount);
    }

    public static void populateFoodMenu(InteractionDialogAPI dialog, int multiplier, String factionId, PersonAPI officer, int humanOfficerCount) {
        populateMainMenu(dialog, officer, humanOfficerCount, factionId);
    }

    public static void populateMainMenu(InteractionDialogAPI dialog, PersonAPI officer, int humanOfficerCount, String factionId) {
        if (dialog == null) return;
        OptionPanelAPI options = dialog.getOptionPanel();
        options.clearOptions();

        boolean isDigesting = OS_ShoreLeaveBuff.isDigesting();
        float digestionDays = OS_ShoreLeaveBuff.getDigestionDaysRemaining();
        String activeMeal = OS_ShoreLeaveBuff.getMealName(OS_ShoreLeaveBuff.getActiveFaction());

        CampaignFleetAPI playerFleet = Global.getSector() != null ? Global.getSector().getPlayerFleet() : null;
        long playerCredits = (playerFleet != null && playerFleet.getCargo() != null) ? (long) playerFleet.getCargo().getCredits().get() : 0L;
        List<MenuItem> items = getMenuItems(factionId);
        MenuItem meal = !items.isEmpty() ? items.get(0) : new MenuItem("orbiting_spoon_order_generic_set", "Loaded Spacer's Full-Burn Diner Set", 18);

        MarketAPI market = dialog.getInteractionTarget() != null ? dialog.getInteractionTarget().getMarket() : null;
        boolean hasFoodShortage = false;
        if (market != null) {
            if (market.hasCondition(Conditions.EVENT_FOOD_SHORTAGE)) {
                hasFoodShortage = true;
            } else if (market.getCommodityData(Commodities.FOOD) != null && market.getCommodityData(Commodities.FOOD).getDeficitQuantity() > 0) {
                hasFoodShortage = true;
            }
        }
        int scarcityMult = hasFoodShortage ? 2 : 1;

        int crewCount = (playerFleet != null && playerFleet.getCargo() != null) ? Math.max(1, (int) playerFleet.getCargo().getCrew()) : 1;
        int multiplier = 1;

        int finalPrice = meal.basePrice * multiplier * scarcityMult;
        String priceFormatted = String.format("%,d", finalPrice);

        if (officer != null) {
            // CONTEXT A: Dining With an Officer (Wardroom Table)
            // 1. Open Custom Graphical Food Menu
            String mealLabel = "Order from 'The Orbiting Spoon' for the table...";
            options.addOption(mealLabel, "orbiting_spoon_open_food_menu");

            String menuTooltip = "Order a hot meal spread from 'The Orbiting Spoon' fabricator, including the Wardroom Cut (" + meal.name + " at " + priceFormatted + " credits), cargo hold banquets, and officer palate preferences for you and " + officer.getNameString() + ".";
            if (isDigesting) {
                menuTooltip += "\n\n(Note: Your crew is currently digesting leftover " + activeMeal + " [" + String.format("%.1f", digestionDays) + " days remaining].)";
            }
            options.setTooltip("orbiting_spoon_open_food_menu", menuTooltip);

            // 2. Direct combat readiness discussion
            options.addOption("Discuss vessel readiness with " + officer.getNameString(), "orbiting_spoon_chat_talk");
            options.setTooltip("orbiting_spoon_chat_talk", "Converse with " + officer.getNameString() + " about vessel readiness, engagement envelopes, and doctrine.");

            // 3. Tactical Mentoring & War Stories
            boolean canMentor = OS_OfficerMentoring.canDoWarStories(officer);
            float daysRem = OS_OfficerMentoring.getWarStoriesDaysRemaining(officer);
            long mentorXP = OS_OfficerMentoring.calculateOfficerXP(officer);
            String mentorOptId = "orbiting_spoon_mentor_warstories";
            if (canMentor) {
                options.addOption("Exchange war stories & mentor " + officer.getNameString() + " (+" + Misc.getWithDGS(mentorXP) + " XP)", mentorOptId);
                options.setTooltip(mentorOptId, "Review combat telemetry and mentor " + officer.getNameString() + " over plates of hot food. Grants " + Misc.getWithDGS(mentorXP) + " Officer XP, fleet Bonus XP, and +5% Camaraderie (Available once every 7 days).");
            } else {
                options.addOption("Exchange war stories & mentor " + officer.getNameString() + " [" + String.format("%.1f", daysRem) + "d cooldown]", mentorOptId);
                options.setEnabled(mentorOptId, false);
                options.setTooltip(mentorOptId, "Debriefed recently (" + String.format("%.1f", daysRem) + " days cooldown remaining). Wait before holding another mentoring session.");
            }

            // 4. Wardroom Tactical Debrief
            options.addOption("Hold a Wardroom Tactical Debrief with " + officer.getNameString(), "orbiting_spoon_mentor_menu");
            options.setTooltip("orbiting_spoon_mentor_menu", "Review combat telemetry over dinner plates to debrief combat habits and unlearn an unwanted skill (Requires 1 Story Point and 100,000 credits).");

            // 5. Tactical assessment of the station
            options.addOption("Ask " + officer.getNameString() + " for station tactical assessment", "orbiting_spoon_chat_officer_port");
            options.setTooltip("orbiting_spoon_chat_officer_port", "Get " + officer.getNameString() + "'s professional evaluation of station defenses, patrol doctrine, and security risks.");

            // 5. Switch officer (if humanOfficerCount > 1)
            if (humanOfficerCount > 1) {
                options.addOption("Invite another officer to the table...", "orbiting_spoon_switch_officer_menu");
                options.setTooltip("orbiting_spoon_switch_officer_menu", "Invite a different officer to take the seat across the table.");
            }

            // 6. Step out to crew mess
            options.addOption("Step out to check on the deck crew", "os_opt_switch_crew");
            options.setTooltip("os_opt_switch_crew", "Leave the wardroom table to eat and socialize with off-duty deckhands in the general mess hall.");

            // 7. Department Status Reports
            options.addOption("Review fleet departmental status reports...", "orbiting_spoon_chat_status");
            options.setTooltip("orbiting_spoon_chat_status", "Consult with Engineering, Astrogation, Personnel, and the Quartermaster on fleet operational status.");

            // 8. Leave
            options.addOption("Leave the mess hall and return to the concourse", "orbiting_spoon_leave");
            options.setTooltip("orbiting_spoon_leave", "Step back into the spaceport concourse and conclude your visit.");
        } else {
            // CONTEXT B: Dining With Crew / Alone (General Crew Mess Hall)
            String marketName = market != null ? market.getName() : "this station";

            // 1. Open Custom Graphical Food Menu
            String mealLabel = "Order from 'The Orbiting Spoon' for the fleet...";
            options.addOption(mealLabel, "orbiting_spoon_open_food_menu");

            String menuTooltip = "Order a hot meal spread from 'The Orbiting Spoon' fabricator, including the Fleet Spread (" + meal.name + " at " + priceFormatted + " credits), cargo hold banquets, and station relief stews for your crew.";
            if (isDigesting) {
                menuTooltip += "\n\n(Note: Your crew is currently digesting leftover " + activeMeal + " [" + String.format("%.1f", digestionDays) + " days remaining].)";
            }
            options.setTooltip("orbiting_spoon_open_food_menu", menuTooltip);

            // 2. Crew spirits & gossip
            options.addOption("Listen to mess hall gossip and check crew spirits", "orbiting_spoon_chat_crew_morale");
            options.setTooltip("orbiting_spoon_chat_crew_morale", "Spend time among the ratings to hear how the crew is handling duty rotations and fleet life.");

            // 3. Station impressions
            options.addOption("Ask crew's impression of " + marketName, "orbiting_spoon_chat_crew_port");
            options.setTooltip("orbiting_spoon_chat_crew_port", "Hear what the dockhands, engineers, and spacers think of " + marketName + ".");

            // 4. Spacefarer rumors
            options.addOption("Listen to dockside rumors and spacer gossip", "orbiting_spoon_chat_crew_rumors");
            options.setTooltip("orbiting_spoon_chat_crew_rumors", "Listen to dockside rumors, fringe whispers, and trade chatter from visiting spacers.");

            // 5. Food review (if buff active)
            if (OS_ShoreLeaveBuff.isBuffActive()) {
                options.addOption("Hear what the crew thinks of the food", "orbiting_spoon_chat_crew_meal");
                options.setTooltip("orbiting_spoon_chat_crew_meal", "Ask the crew for their honest thoughts on the current meal spread.");
            }

            // 6. Invite officer (if humanOfficerCount > 0)
            if (humanOfficerCount > 0) {
                options.addOption("Invite an officer to join you at the table...", "orbiting_spoon_switch_officer_menu");
                options.setTooltip("orbiting_spoon_switch_officer_menu", "Invite one of your bridge officers to leave their quarters and join you at the table.");
            }

            // 7. Department Status Reports
            options.addOption("Review fleet departmental status reports...", "orbiting_spoon_chat_status");
            options.setTooltip("orbiting_spoon_chat_status", "Consult with Engineering, Astrogation, Personnel, and the Quartermaster on fleet operational status.");

            // 8. Leave
            options.addOption("Leave the mess hall and return to the concourse", "orbiting_spoon_leave");
            options.setTooltip("orbiting_spoon_leave", "Step back into the spaceport concourse and conclude your visit.");
        }
    }
    
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;
        
        String command = params != null && !params.isEmpty() ? params.get(0).getString(memoryMap) : "start";
        List<PersonAPI> humanOfficers = getHumanOfficers();
        int humanOfficerCount = humanOfficers.size();
        MemoryAPI sectorMem = Global.getSector().getMemoryWithoutUpdate();

        // Sub-command: Switch Menu
        if ("switch_menu".equals(command)) {
            OptionPanelAPI options = dialog.getOptionPanel();
            options.clearOptions();
            dialog.getTextPanel().setFontSmallInsignia();
            dialog.getTextPanel().addParagraph("Select an officer to invite to the mess table:", Misc.getHighlightColor());
            dialog.getTextPanel().setFontInsignia();

            for (int i = 0; i < humanOfficers.size() && i < 16; i++) {
                PersonAPI p = humanOfficers.get(i);
                int fs = OS_OfficerFriendship.getFriendship(p);
                String tier = OS_OfficerFriendship.getTierName(fs);
                int lvl = p.getStats().getLevel();
                String optId = "os_opt_switch_" + i;
                options.addOption("Invite " + p.getNameString() + " (Lvl " + lvl + ", " + tier + ")", optId);
                options.setTooltip(
                    optId,
                    "Personality: " + (p.getPersonalityAPI() != null ? p.getPersonalityAPI().getDisplayName() : "Steady") +
                    "\nCamaraderie: " + fs + "% (" + tier + ")" +
                    "\nCampaign Doctrine: " + OS_OfficerFriendship.getLoyalPerkSummary(p)
                );
            }

            options.addOption("Dine with the deck crew instead", "os_opt_switch_crew");
            options.setTooltip("os_opt_switch_crew", "Invite off-duty deckhands, mechanics, and spacers to share the table.");

            options.addOption("Return to the table", "orbiting_spoon_start_dispatch");
            return true;
        }

        // Sub-command: Switch To specific officer by index or crew
        if ("switch_to_index".equals(command) || "switch_to".equals(command)) {
            String targetIndexStr = params.size() > 1 ? params.get(1).getString(memoryMap) : null;
            if (targetIndexStr != null && sectorMem != null) {
                if ("crew".equals(targetIndexStr) || "-1".equals(targetIndexStr)) {
                    sectorMem.set(KEY_ACTIVE_OFFICER_ID, "crew");
                } else {
                    try {
                        int idx = Integer.parseInt(targetIndexStr);
                        if (idx >= 0 && idx < humanOfficers.size()) {
                            sectorMem.set(KEY_ACTIVE_OFFICER_ID, humanOfficers.get(idx).getId());
                        }
                    } catch (Exception ignored) {}
                }
            }
            command = "start"; // Fall through to reinitialize dining party
        }

        if ("main_menu".equals(command)) {
            PersonAPI officer = getActiveOfficerFromMemory(memoryMap);
            if (officer != null) {
                dialog.getVisualPanel().showPersonInfo(officer, true);
            } else {
                dialog.getVisualPanel().fadeVisualOut();
            }
            String factionId = getFactionId(dialog, memoryMap);
            populateMainMenu(dialog, officer, humanOfficerCount, factionId);
            return true;
        }

        if ("open_food_menu".equals(command) || "open_food_ui".equals(command)) {
            dialog.showCustomDialog(840f, 600f, new data.campaign.ui.OS_CustomFoodDialogDelegate(dialog, memoryMap));
            return true;
        }

        boolean populateOnly = "populate_only".equals(command) || "food_menu".equals(command);
        if (populateOnly) {
            int multiplier = 100;
            if (memoryMap != null) {
                if (memoryMap.get(MemKeys.LOCAL) != null && memoryMap.get(MemKeys.LOCAL).contains("$os_multiplierNum")) {
                    multiplier = memoryMap.get(MemKeys.LOCAL).getInt("$os_multiplierNum");
                } else if (memoryMap.get(MemKeys.GLOBAL) != null && memoryMap.get(MemKeys.GLOBAL).contains("$os_multiplierNum")) {
                    multiplier = memoryMap.get(MemKeys.GLOBAL).getInt("$os_multiplierNum");
                } else if (memoryMap.get(MemKeys.ENTITY) != null && memoryMap.get(MemKeys.ENTITY).contains("$os_multiplierNum")) {
                    multiplier = memoryMap.get(MemKeys.ENTITY).getInt("$os_multiplierNum");
                }
            }
            if (multiplier <= 0 && sectorMem != null && sectorMem.contains("$os_multiplierNum")) {
                multiplier = sectorMem.getInt("$os_multiplierNum");
            }
            if (multiplier <= 0) multiplier = 100;
            
            // Restore visual if officer is present, else fade visual
            PersonAPI officer = getActiveOfficerFromMemory(memoryMap);
            if (officer != null) {
                dialog.getVisualPanel().showPersonInfo(officer, true);
            } else {
                dialog.getVisualPanel().fadeVisualOut();
            }
            
            String factionId = getFactionId(dialog, memoryMap);
            populateFoodMenu(dialog, multiplier, factionId, officer, humanOfficerCount);
            return true;
        }
        
        // Determine active dining partner
        String savedOfficerId = sectorMem != null && sectorMem.contains(KEY_ACTIVE_OFFICER_ID) ? sectorMem.getString(KEY_ACTIVE_OFFICER_ID) : null;
        PersonAPI chosenPerson = null;
        boolean hasOfficer = false;

        if (!"crew".equals(savedOfficerId) && !humanOfficers.isEmpty()) {
            if (savedOfficerId != null) {
                for (PersonAPI p : humanOfficers) {
                    if (p.getId().equals(savedOfficerId)) {
                        chosenPerson = p;
                        break;
                    }
                }
            }
            if (chosenPerson == null) {
                chosenPerson = humanOfficers.get(0);
                if (sectorMem != null) sectorMem.set(KEY_ACTIVE_OFFICER_ID, chosenPerson.getId());
            }
            hasOfficer = true;
        }

        CampaignFleetAPI playerFleet = Global.getSector() != null ? Global.getSector().getPlayerFleet() : null;
        int multiplier;
        String officerName = "";
        String officerPersonality = "steady";
        int officerLevel = 1;
        int crewCount = (playerFleet != null && playerFleet.getCargo() != null) ? Math.max(1, (int) playerFleet.getCargo().getCrew()) : 1;
        
        if (!hasOfficer || chosenPerson == null) {
            multiplier = 1;
            dialog.getVisualPanel().fadeVisualOut();
        } else {
            officerName = chosenPerson.getNameString();
            if (chosenPerson.getPersonalityAPI() != null) {
                officerPersonality = chosenPerson.getPersonalityAPI().getId();
            }
            officerLevel = Math.max(1, chosenPerson.getStats().getLevel());
            multiplier = 1;
            
            // Show officer portrait in the dialog visual panel
            dialog.getVisualPanel().showPersonInfo(chosenPerson, true);
        }

        // Clean up any legacy raw PersonAPI or mod keys from ENTITY memory scope
        if (memoryMap != null && memoryMap.get(MemKeys.ENTITY) != null) {
            MemoryAPI entityMem = memoryMap.get(MemKeys.ENTITY);
            entityMem.unset("$os_officerPerson");
            entityMem.unset("$os_hasOfficer");
            entityMem.unset("$os_officerName");
            entityMem.unset("$os_officerPersonality");
            entityMem.unset("$os_officerLevel");
            entityMem.unset("$os_crewCount");
            entityMem.unset("$os_multiplier");
            entityMem.unset("$os_multiplierNum");
            entityMem.unset("$os_officerFriendship");
            entityMem.unset("$os_officerTier");
        }

        MarketAPI market = dialog.getInteractionTarget() != null ? dialog.getInteractionTarget().getMarket() : null;
        String marketName = market != null ? market.getName() : "the station";

        // Store exclusively in transient LOCAL dialog memory with 0-day expiration
        if (memoryMap != null && memoryMap.get(MemKeys.LOCAL) != null) {
            MemoryAPI local = memoryMap.get(MemKeys.LOCAL);
            local.set("$marketName", marketName, 0);
            local.set("$os_hasOfficer", hasOfficer, 0);
            local.set("$os_officerName", officerName, 0);
            local.set("$os_officerPersonality", officerPersonality, 0);
            local.set("$os_officerLevel", officerLevel, 0);
            local.set("$os_crewCount", crewCount, 0);
            local.set("$os_multiplier", String.format("%,d", multiplier), 0);
            local.set("$os_multiplierNum", multiplier, 0);
            if (chosenPerson != null) {
                local.set(KEY_ACTIVE_OFFICER_ID, chosenPerson.getId(), 0);
                local.set("$os_officerFriendship", OS_OfficerFriendship.getFriendship(chosenPerson), 0);
                local.set("$os_officerTier", OS_OfficerFriendship.getTierName(OS_OfficerFriendship.getFriendship(chosenPerson)), 0);
            } else {
                local.set(KEY_ACTIVE_OFFICER_ID, "crew", 0);
                local.unset("$os_officerFriendship");
                local.unset("$os_officerTier");
            }
        }

        // Clean up any legacy raw PersonAPI references from sectorMem
        if (sectorMem != null && sectorMem.contains("$os_officerPerson")) {
            sectorMem.unset("$os_officerPerson");
        }
        
        // Populate telemetry memory variables silently without printing
        OS_FetchFleetStatus.populateTelemetryMemory(memoryMap);
        
        // Populate structured main booth menu
        String factionId = getFactionId(dialog, memoryMap);
        populateMainMenu(dialog, chosenPerson, humanOfficerCount, factionId);
        
        return true;
    }
}

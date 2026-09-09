package data.campaign.rulecmd;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI.SkillLevelAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import data.campaign.OS_OfficerFriendship;

public class OS_OfficerMentoring extends BaseCommandPlugin {

    public static final int RESPEC_SP_COST = 1;
    public static final int RESPEC_CREDIT_COST = 100000;

    public static final String KEY_LAST_MENTORING_TIMESTAMP = "$os_last_mentoring_ts";
    public static final float MENTORING_COOLDOWN_DAYS = 7.0f; // 7-day tactical debriefing lockout
    public static final long DINING_OFFICER_BONUS_XP = 25000L;
    public static final long DINING_CREW_BONUS_XP = 35000L;
    public static final long MENTORING_FLEET_BONUS_XP = 20000L;

    public static final String[] VALID_OFFICER_SKILLS = new String[] {
        "helmsmanship",
        "combat_endurance",
        "impact_mitigation",
        "damage_control",
        "field_modulation",
        "point_defense",
        "target_analysis",
        "ballistic_mastery",
        "systems_expertise",
        "missile_specialization",
        "gunnery_implants",
        "energy_weapon_mastery",
        "ordnance_expert",
        "polarized_armor"
    };

    public static PersonAPI getActiveOfficer(Map<String, MemoryAPI> memoryMap) {
        return OS_PickRandomOfficer.getActiveOfficerFromMemory(memoryMap);
    }

    public static long calculateOfficerXP(PersonAPI officer) {
        if (officer == null || officer.getStats() == null) return 5000L;
        int level = Math.max(1, officer.getStats().getLevel());
        // 5,000 XP at Level 1 up to 15,000 XP at Level 6
        return 5000L + (long)(level - 1) * 2000L;
    }

    public static boolean canDoWarStories(PersonAPI officer) {
        if (officer == null) return false;
        MemoryAPI mem = officer.getMemoryWithoutUpdate();
        if (mem == null || !mem.contains(KEY_LAST_MENTORING_TIMESTAMP)) return true;
        long lastTs = mem.getLong(KEY_LAST_MENTORING_TIMESTAMP);
        float elapsed = Global.getSector().getClock().getElapsedDaysSince(lastTs);
        return elapsed >= MENTORING_COOLDOWN_DAYS;
    }

    public static float getWarStoriesDaysRemaining(PersonAPI officer) {
        if (officer == null) return 0f;
        MemoryAPI mem = officer.getMemoryWithoutUpdate();
        if (mem == null || !mem.contains(KEY_LAST_MENTORING_TIMESTAMP)) return 0f;
        long lastTs = mem.getLong(KEY_LAST_MENTORING_TIMESTAMP);
        float elapsed = Global.getSector().getClock().getElapsedDaysSince(lastTs);
        return Math.max(0f, MENTORING_COOLDOWN_DAYS - elapsed);
    }

    public static void awardDiningExperience(InteractionDialogAPI dialog, PersonAPI officer) {
        if (Global.getSector() == null) return;

        if (officer != null && officer.getStats() != null) {
            long officerXP = calculateOfficerXP(officer);
            int levelBefore = officer.getStats().getLevel();
            officer.getStats().addXP(officerXP);
            int levelAfter = officer.getStats().getLevel();

            if (dialog != null && dialog.getTextPanel() != null) {
                dialog.getTextPanel().setFontSmallInsignia();
                dialog.getTextPanel().addParagraph(
                    officer.getNameString() + " gained " + Misc.getWithDGS(officerXP) + " Officer Experience from wardroom tactical debriefing.",
                    Misc.getPositiveHighlightColor()
                );
                dialog.getTextPanel().highlightInLastPara(Misc.getHighlightColor(), officer.getNameString(), Misc.getWithDGS(officerXP));

                if (levelAfter > levelBefore) {
                    dialog.getTextPanel().addParagraph(
                        officer.getNameString() + " has reached Level " + levelAfter + "! Allocate their new skill point in your fleet screen.",
                        Misc.getPositiveHighlightColor()
                    );
                    dialog.getTextPanel().highlightInLastPara(Misc.getHighlightColor(), "Level " + levelAfter);
                }
                dialog.getTextPanel().setFontInsignia();
            }

            // Award player fleet Bonus XP
            Global.getSector().getPlayerStats().setBonusXPGainReason("from wardroom dining & debriefing");
            Global.getSector().getPlayerStats().addBonusXP(DINING_OFFICER_BONUS_XP, true, dialog != null ? dialog.getTextPanel() : null, true);

        } else {
            // Eating with crew ($os_hasOfficer == false)
            if (dialog != null && dialog.getTextPanel() != null) {
                dialog.getTextPanel().setFontSmallInsignia();
                dialog.getTextPanel().addParagraph(
                    "Over hot plates in the crew mess, seasoned deckhands mentor rookie spacers, swapping tricks on maintenance routines, damage control, and burn vectors.",
                    Misc.getTextColor()
                );
                dialog.getTextPanel().setFontInsignia();
            }

            Global.getSector().getPlayerStats().setBonusXPGainReason("from seasoned deckhands mentoring rookie spacers");
            Global.getSector().getPlayerStats().addBonusXP(DINING_CREW_BONUS_XP, true, dialog != null ? dialog.getTextPanel() : null, true);
        }
    }

    public static List<SkillLevelAPI> getLearnedCombatSkills(PersonAPI officer) {
        List<SkillLevelAPI> list = new ArrayList<>();
        if (officer == null || officer.getStats() == null) return list;
        for (SkillLevelAPI sl : officer.getStats().getSkillsCopy()) {
            if (sl.getSkill() != null && sl.getSkill().isCombatOfficerSkill() && sl.getLevel() > 0) {
                list.add(sl);
            }
        }
        return list;
    }

    public static boolean hasSkill(PersonAPI officer, String skillId) {
        if (officer == null || officer.getStats() == null || skillId == null) return false;
        return officer.getStats().getSkillLevel(skillId) > 0;
    }

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        String action = params != null && !params.isEmpty() ? params.get(0).getString(memoryMap) : "menu";
        PersonAPI officer = getActiveOfficer(memoryMap);

        if (officer == null) {
            dialog.getTextPanel().addParagraph("No officer is currently seated at the table.", Misc.getNegativeHighlightColor());
            OptionPanelAPI options = dialog.getOptionPanel();
            options.addOption("Return to the table", "orbiting_spoon_start_dispatch");
            return true;
        }

        // Handle conversation friendship boost
        if ("add_chat_friendship".equals(action)) {
            String topic = params.size() > 1 ? params.get(1).getString(memoryMap) : "general";
            String key = "$os_chatted_" + topic;
            MemoryAPI mem = officer.getMemoryWithoutUpdate();
            if (mem != null && !mem.getBoolean(key)) {
                mem.set(key, true, 2.0f); // valid for this shore leave
                data.campaign.OS_OfficerFriendship.addFriendship(officer, 5, dialog);
            }
            return true;
        }

        // Restore officer portrait
        dialog.getVisualPanel().showPersonInfo(officer, true);

        if ("war_stories".equals(action) || "mentor_warstories".equals(action)) {
            executeWarStories(dialog, officer, memoryMap);
            return true;
        }

        if ("do_remove".equals(action)) {
            String skillId = params.size() > 1 ? params.get(1).getString(memoryMap) : null;
            if (skillId != null && hasSkill(officer, skillId)) {
                int playerSP = Global.getSector().getPlayerStats().getStoryPoints();
                float playerCredits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();

                if (playerSP < RESPEC_SP_COST || playerCredits < RESPEC_CREDIT_COST) {
                    dialog.getTextPanel().addParagraph(
                        "You do not have enough Story Points or credits to finance this tactical retraining session.",
                        Misc.getNegativeHighlightColor()
                    );
                    showDeleterMenu(dialog, officer);
                    return true;
                }

                float currentLevel = officer.getStats().getSkillLevel(skillId);
                boolean wasElite = currentLevel > 1.0f;
                SkillSpecAPI spec = Global.getSettings().getSkillSpec(skillId);
                String skillName = spec != null ? spec.getName() : skillId;

                // 1. Spend 1 Story Point (100% bonus XP returned)
                Global.getSector().getPlayerStats().spendStoryPoints(
                    RESPEC_SP_COST,
                    true,
                    dialog.getTextPanel(),
                    true,
                    1f,
                    "Unlearned " + skillName + " for " + officer.getNameString() + " over top-of-the-line banquet"
                );

                // 2. Deduct 100,000 credits for top-of-the-line food and private debrief
                Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(RESPEC_CREDIT_COST);
                AddRemoveCommodity.addCreditsLossText(RESPEC_CREDIT_COST, dialog.getTextPanel());

                // 3. If the skill was Elite, refund the Story Point previously spent to make it Elite
                if (wasElite) {
                    Global.getSector().getPlayerStats().addStoryPoints(1, dialog.getTextPanel(), false);
                    dialog.getTextPanel().addParagraph(
                        "Refunded 1 Story Point previously invested in making " + skillName + " Elite.",
                        Misc.getPositiveHighlightColor()
                    );
                }

                // 4. Cleanly unlearn skill
                officer.getStats().setSkillLevel(skillId, 0f);
                officer.getStats().decreaseSkill(skillId);
                officer.getStats().refreshCharacterStatsEffects();

                // 5. Feedback in log
                dialog.getTextPanel().setFontSmallInsignia();
                dialog.getTextPanel().addParagraph(
                    officer.getNameString() + " has unlearned " + skillName + ". 1 skill point refunded for retraining in the fleet officer screen!",
                    Misc.getPositiveHighlightColor()
                );
                dialog.getTextPanel().highlightInLastPara(Misc.getHighlightColor(), skillName, "1 skill point refunded");
                dialog.getTextPanel().setFontInsignia();

                String reaction = data.campaign.OS_OfficerFriendship.getRetrainingReactionText(officer, skillName);
                if (!reaction.isEmpty()) {
                    dialog.getTextPanel().addParagraph(reaction, Misc.getTextColor());
                }

                // 6. Personality-based Camaraderie malus from retraining strain
                int delta = data.campaign.OS_OfficerFriendship.getRetrainingCamaraderieDelta(officer);
                data.campaign.OS_OfficerFriendship.addFriendship(officer, delta, dialog);
            }
            showDeleterMenu(dialog, officer);
            return true;
        } else {
            showDeleterMenu(dialog, officer);
            return true;
        }
    }

    public static void executeWarStories(InteractionDialogAPI dialog, PersonAPI officer, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null || officer == null) return;

        OptionPanelAPI options = dialog.getOptionPanel();
        options.clearOptions();
        dialog.getVisualPanel().showPersonInfo(officer, true);

        if (!canDoWarStories(officer)) {
            float daysRemaining = getWarStoriesDaysRemaining(officer);
            String daysStr = String.format("%.1f", daysRemaining);
            dialog.getTextPanel().addParagraph(
                officer.getNameString() + " shakes their head with a faint smile. \"We already conducted a thorough telemetry debrief and went through past sorties recently, Captain. Let's make some new combat logs out in the black before we dissect them again over dinner.\"",
                Misc.getTextColor()
            );
            dialog.getTextPanel().setFontSmallInsignia();
            dialog.getTextPanel().addParagraph(
                "(Tactical Mentoring on cooldown: " + daysStr + " days remaining before " + officer.getNameString() + " is ready for another debrief session).",
                Misc.getHighlightColor()
            );
            dialog.getTextPanel().setFontInsignia();

            options.addOption("Hold a Wardroom Tactical Debrief (Doctrine Retraining)", "orbiting_spoon_mentor_menu");
            options.addOption("Return to the table", "orbiting_spoon_start_dispatch");
            return;
        }

        // Set cooldown timestamp
        officer.getMemoryWithoutUpdate().set(KEY_LAST_MENTORING_TIMESTAMP, Global.getSector().getClock().getTimestamp());

        // Officer XP
        long officerXP = calculateOfficerXP(officer);
        int levelBefore = officer.getStats().getLevel();
        officer.getStats().addXP(officerXP);
        int levelAfter = officer.getStats().getLevel();

        // Player Fleet Bonus XP
        Global.getSector().getPlayerStats().setBonusXPGainReason("from tactical mentoring & war stories");
        Global.getSector().getPlayerStats().addBonusXP(MENTORING_FLEET_BONUS_XP, true, dialog.getTextPanel(), true);

        // Camaraderie boost (+5%)
        OS_OfficerFriendship.addFriendship(officer, 5, dialog);

        // Personality-specific narrative
        String personality = officer.getPersonalityAPI() != null ? officer.getPersonalityAPI().getId() : "steady";
        String narrative;
        switch (personality) {
            case "timid":
                narrative = "You and " + officer.getNameString() + " spread out telemetry printouts across the diner table. With quiet patience, you walk through defensive screening angles, escort formations, and missile interception envelopes. " + officer.getNameString() + " relaxes visibly, taking meticulous notes on how proper fleet positioning keeps their vessel safe even when outnumbered.";
                break;
            case "cautious":
                narrative = "Over cooling mugs of chicory, you and " + officer.getNameString() + " dissect flux dissipation rates, weapon range brackets, and heat management curves from recent fleet sorties. Your methodical breakdown of standoff tactics and flux discipline validates their instincts and sharpens their command judgment.";
                break;
            case "aggressive":
                narrative = "Plates rattle as " + officer.getNameString() + " leans in over the table, analyzing fleet assault vectors and breakthrough timings. You share hard-won lessons on recognizing overextended targets, coordinating alpha-strikes, and disengaging before secondary battle lines collapse.";
                break;
            case "reckless":
                narrative = "Over loud laughter and clattering mugs, " + officer.getNameString() + " grins as you swap near-suicidal war stories of close-quarters broadsides and point-blank torpedo runs. Beneath the bravado, you instill crucial survival reflexes: reading emergency flameouts and recognizing when to cycle shields before hull integrity fails.";
                break;
            case "steady":
            default:
                narrative = "You and " + officer.getNameString() + " sit back with warm mugs, trading weathered war stories from past campaigns. You compare notes on bridge communication under fire, reading battle lines at a glance, and keeping command composure when proximity alarms are deafening.";
                break;
        }

        dialog.getTextPanel().addParagraph(narrative, Misc.getTextColor());

        dialog.getTextPanel().setFontSmallInsignia();
        dialog.getTextPanel().addParagraph(
            officer.getNameString() + " gained " + Misc.getWithDGS(officerXP) + " Officer Experience from the tactical mentoring session!",
            Misc.getPositiveHighlightColor()
        );
        dialog.getTextPanel().highlightInLastPara(Misc.getHighlightColor(), officer.getNameString(), Misc.getWithDGS(officerXP));

        if (levelAfter > levelBefore) {
            dialog.getTextPanel().addParagraph(
                officer.getNameString() + " has reached Level " + levelAfter + "! A new skill point is available in your fleet officer screen.",
                Misc.getPositiveHighlightColor()
            );
            dialog.getTextPanel().highlightInLastPara(Misc.getHighlightColor(), "Level " + levelAfter);
        }
        dialog.getTextPanel().setFontInsignia();

        // Sound effect
        Global.getSoundPlayer().playUISound("ui_char_increase_skill", 1f, 1f);

        options.addOption("Hold a Wardroom Tactical Debrief (Doctrine Retraining)", "orbiting_spoon_mentor_menu");
        options.addOption("Return to the table", "orbiting_spoon_start_dispatch");
    }

    public static void showDeleterMenu(InteractionDialogAPI dialog, PersonAPI officer) {
        OptionPanelAPI options = dialog.getOptionPanel();
        options.clearOptions();

        int friendship = OS_OfficerFriendship.getFriendship(officer);
        String tier = OS_OfficerFriendship.getTierName(friendship);

        if (!OS_OfficerFriendship.canRetrain(officer)) {
            dialog.getTextPanel().addParagraph(
                officer.getNameString() + " shakes their head over their mug. \"We haven't flown together long enough for me to take personal doctrine critiques, Captain. Build up our trust over a few more meals and sorties first.\"",
                Misc.getTextColor()
            );
            dialog.getTextPanel().setFontSmallInsignia();
            dialog.getTextPanel().addParagraph(
                "(Requires at least 50% Camaraderie [Trusted Comrade] before an officer will accept tactical doctrine retraining; current Camaraderie is " + friendship + "% [" + tier + "]).",
                Misc.getHighlightColor()
            );
            dialog.getTextPanel().setFontInsignia();

            // Tactical Mentoring & War Stories option available even before 50% Camaraderie
            boolean canMentor = canDoWarStories(officer);
            float daysRem = getWarStoriesDaysRemaining(officer);
            long mentorXP = calculateOfficerXP(officer);
            String mentorOptId = "os_opt_war_stories";
            if (canMentor) {
                options.addOption("Exchange war stories & mentor (+" + Misc.getWithDGS(mentorXP) + " XP)", mentorOptId);
                options.setTooltip(mentorOptId, "Review combat telemetry and mentor " + officer.getNameString() + " over plates of hot food. Grants " + Misc.getWithDGS(mentorXP) + " Officer XP, fleet Bonus XP, and +5% Camaraderie (Available once every 7 days).");
            } else {
                options.addOption("Exchange war stories & mentor [" + String.format("%.1f", daysRem) + "d cooldown]", mentorOptId);
                options.setEnabled(mentorOptId, false);
                options.setTooltip(mentorOptId, "Debriefed recently (" + String.format("%.1f", daysRem) + " days cooldown remaining). Wait before holding another mentoring session.");
            }

            options.addOption("Return to the table", "orbiting_spoon_start_dispatch");
            return;
        }

        // 1. Tactical Mentoring & War Stories option
        boolean canMentor = canDoWarStories(officer);
        float daysRem = getWarStoriesDaysRemaining(officer);
        long mentorXP = calculateOfficerXP(officer);
        String mentorOptId = "os_opt_war_stories";
        if (canMentor) {
            options.addOption("Exchange war stories & mentor (+" + Misc.getWithDGS(mentorXP) + " XP)", mentorOptId);
            options.setTooltip(mentorOptId, "Review combat telemetry and mentor " + officer.getNameString() + " over plates of hot food. Grants " + Misc.getWithDGS(mentorXP) + " Officer XP, fleet Bonus XP, and +5% Camaraderie (Available once every 7 days).");
        } else {
            options.addOption("Exchange war stories & mentor [" + String.format("%.1f", daysRem) + "d cooldown]", mentorOptId);
            options.setEnabled(mentorOptId, false);
            options.setTooltip(mentorOptId, "Debriefed recently (" + String.format("%.1f", daysRem) + " days cooldown remaining). Wait before holding another mentoring session.");
        }

        int maxSkills = officer.getStats().getLevel();
        List<SkillLevelAPI> learned = getLearnedCombatSkills(officer);
        int currentSkills = learned.size();
        int openSlots = Math.max(0, maxSkills - currentSkills);

        int playerSP = Global.getSector().getPlayerStats().getStoryPoints();
        float playerCredits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();

        dialog.getTextPanel().setFontSmallInsignia();
        dialog.getTextPanel().addParagraph(
            "Wardroom Tactical Debrief: " + officer.getNameString() + " (Level " + maxSkills + " - Camaraderie: " + friendship + "% [" + tier + "])",
            Misc.getHighlightColor()
        );
        dialog.getTextPanel().highlightInLastPara(Misc.getHighlightColor(), officer.getNameString(), friendship + "% [" + tier + "]");

        dialog.getTextPanel().addParagraph(
            "Available Resources: " + playerSP + " Story Point" + (playerSP == 1 ? "" : "s") + " | " + Misc.getDGSCredits(playerCredits) + " credits (Cost: " + RESPEC_SP_COST + " SP & " + Misc.getDGSCredits(RESPEC_CREDIT_COST) + " credits per doctrine)",
            Misc.getHighlightColor()
        );
        dialog.getTextPanel().highlightInLastPara(Misc.getHighlightColor(), String.valueOf(playerSP), Misc.getDGSCredits(playerCredits), RESPEC_SP_COST + " SP", Misc.getDGSCredits(RESPEC_CREDIT_COST));
        dialog.getTextPanel().setFontInsignia();

        StringBuilder sb = new StringBuilder();
        sb.append(officer.getNameString()).append(" sits back amidst empty platters from a top-of-the-line meal spread, vintage private reserves and classified tactical slates.\n\n");
        sb.append("\"Captain, scrubbing deeply ingrained combat reflexes and unlearning old tactical habits takes hours of grueling telemetry debriefs over dinner. It costs ")
          .append(RESPEC_SP_COST).append(" Story Point and ")
          .append(Misc.getDGSCredits(RESPEC_CREDIT_COST)).append(" credits per doctrine to cover the time, private table catering, and tactical data compilation. If you're ready, tell me which doctrine to drop from my regimen.\"\n\n");
        sb.append("Current Combat Skills (").append(currentSkills).append("/").append(maxSkills).append(" slots utilized):\n");
        if (learned.isEmpty()) {
            sb.append("  - No combat skills currently learned");
        } else {
            for (SkillLevelAPI sl : learned) {
                sb.append("  - ").append(sl.getSkill().getName());
                if (sl.getLevel() > 1.0f) sb.append(" [Elite]");
                sb.append("\n");
            }
        }
        if (openSlots > 0) {
            sb.append("\nAvailable unspent skill slots: ").append(openSlots).append(" (Allocate via fleet officer screen)");
        }
        dialog.getTextPanel().addParagraph(sb.toString().trim(), Misc.getTextColor());
        dialog.getTextPanel().setFontInsignia();

        if (learned.isEmpty()) {
            dialog.getTextPanel().addParagraph(officer.getNameString() + " has no learned combat skills to forget.", Misc.getGrayColor());
        } else {
            for (SkillLevelAPI sl : learned) {
                String skillId = sl.getSkill().getId();
                String skillName = sl.getSkill().getName();
                boolean isElite = sl.getLevel() > 1.0f;
                String optId = "os_opt_remove_" + skillId;

                options.addOption("Forget " + skillName + (isElite ? " [Elite]" : "") + " (1 SP, 100,000 credits)", optId);

                boolean canAffordSP = playerSP >= RESPEC_SP_COST;
                boolean canAffordCredits = playerCredits >= RESPEC_CREDIT_COST;
                boolean canAfford = canAffordSP && canAffordCredits;

                String desc = sl.getSkill().getDescription();
                if (desc == null || desc.isEmpty()) desc = "Combat doctrine skill.";

                String costReason = "";
                if (!canAfford) {
                    options.setEnabled(optId, false);
                    if (!canAffordSP && !canAffordCredits) {
                        costReason = "\n\nCannot unlearn: Insufficient resources.\nRequires 1 Story Point and 100,000 credits (You have: " + playerSP + " SP, " + Misc.getDGSCredits(playerCredits) + " credits).";
                    } else if (!canAffordSP) {
                        costReason = "\n\nCannot unlearn: Insufficient Story Points.\nRequires 1 Story Point (You have: 0 SP).";
                    } else {
                        costReason = "\n\nCannot unlearn: Insufficient credits.\nRequires 100,000 credits to cover the catering and wardroom debriefing (You have: " + Misc.getDGSCredits(playerCredits) + " credits).";
                    }
                }

                String eliteNote = isElite ? "\n\nNote: This is an Elite skill. The Story Point previously invested to make it Elite will be refunded." : "";

                options.setTooltip(optId, "Unlearn " + skillName + ".\n\n" + desc + "\n\nRefunds 1 skill point to re-spend normally in your fleet officer screen.\n\nRequires: 1 Story Point (100% bonus XP) and 100,000 credits for private catering and wardroom debrief." + eliteNote + costReason);
            }
        }

        options.addOption("Return to the table", "orbiting_spoon_start_dispatch");
    }
}

package data.campaign.buffs;

import java.awt.Color;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.HintPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.util.Misc;

public class OS_ShoreLeaveBuff implements EveryFrameScript {

    public static final String BUFF_ID = "os_shore_leave";
    public static final String KEY_TIMESTAMP = "$os_shore_leave_timestamp";
    public static final String KEY_DURATION = "$os_shore_leave_duration";
    public static final String KEY_FACTION = "$os_shore_leave_faction";
    public static final String KEY_STATION = "$os_shore_leave_station";

    public static final float DEFAULT_DURATION = 14f;
    public static final float INDIE_DURATION = 21f;
    public static final float DIGESTION_COOLDOWN_DAYS = 7.0f; // 7-day physical digestion lockout

    public static final float SUPPLY_REDUCTION_PERCENT = -5f; // -5% Monthly Supply Upkeep
    public static final float MAX_CR_BONUS_FLAT = 0.05f; // +5% Max Combat Readiness (CR)
    public static final float CR_RECOVERY_BONUS_PERCENT = 10f; // +10% CR Recovery rate
    public static final float SENSOR_PROFILE_PENALTY_PERCENT = 10f; // +10% Sensor Profile
    public static final float ACCELERATION_PENALTY_MULT = 0.95f; // -5% Fleet Acceleration

    protected static int activeHintSlot = -1;
    protected static String lastHintText = null;

    protected boolean isDone = false;
    protected float checkTimer = 0f;

    public static boolean isBuffActive() {
        if (Global.getSector() == null) return false;
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        if (mem == null || !mem.contains(KEY_TIMESTAMP)) return false;
        return getDaysRemaining() > 0f;
    }

    public static String getActiveFaction() {
        if (Global.getSector() == null) return "independent";
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        if (mem == null || !mem.contains(KEY_FACTION)) return "independent";
        return mem.getString(KEY_FACTION);
    }

    public static float getDaysRemaining() {
        if (Global.getSector() == null || Global.getSector().getClock() == null) return 0f;
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        if (mem == null || !mem.contains(KEY_TIMESTAMP)) return 0f;
        long ts = mem.getLong(KEY_TIMESTAMP);
        float duration = mem.contains(KEY_DURATION) ? mem.getFloat(KEY_DURATION) : DEFAULT_DURATION;
        float elapsed = Global.getSector().getClock().getElapsedDaysSince(ts);
        float remaining = duration - elapsed;
        return Math.max(0f, remaining);
    }

    public static float getDuration() {
        if (Global.getSector() == null) return DEFAULT_DURATION;
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        if (mem == null || !mem.contains(KEY_DURATION)) return DEFAULT_DURATION;
        return mem.getFloat(KEY_DURATION);
    }

    public static boolean isDigesting() {
        if (!isBuffActive()) return false;
        long ts = Global.getSector().getMemoryWithoutUpdate().getLong(KEY_TIMESTAMP);
        float elapsed = Global.getSector().getClock().getElapsedDaysSince(ts);
        return elapsed < DIGESTION_COOLDOWN_DAYS;
    }

    public static float getDigestionDaysRemaining() {
        if (!isBuffActive()) return 0f;
        long ts = Global.getSector().getMemoryWithoutUpdate().getLong(KEY_TIMESTAMP);
        float elapsed = Global.getSector().getClock().getElapsedDaysSince(ts);
        return Math.max(0f, DIGESTION_COOLDOWN_DAYS - elapsed);
    }

    public static String getMealName(String factionId) {
        String f = factionId != null ? factionId.toLowerCase().trim() : "independent";
        switch (f) {
            case "hegemony": return "Auxiliary Commissary Rations";
            case "tritachyon": case "tri_tachyon": return "Executive Synth-Steak Suite";
            case "sindrian_diktat": case "diktat": return "Supreme Executor's Volturnian Feast";
            case "luddic_church": case "church": return "Pilgrim's Hearth Harvest Feast";
            case "luddic_path": case "path": return "Ascetic Penance Rations";
            case "pirates": case "pirate": return "Fringe Scavenger Platter";
            case "persean": case "persean_league": return "Archon's Grand Mezze Banquet";
            case "lobster_feast": return "Private Volturnian Lobster Banquet";
            case "luxury_feast": return "Pre-Collapse Vintage Luxury Banquet";
            case "famine_stew": return "Dockworkers' Gratitude Stew";
            default: return "Loaded Spacer's Full-Burn Diner Set";
        }
    }

    public static String getMealPerkSummary(String factionId) {
        return "-5% Supply Upkeep, +5% Max CR & +10% CR Recovery (Combat Ships) | Downsides: +10% Sensor Profile, -5% Fleet Acceleration";
    }

    public static String getShortPerkSummary(String factionId) {
        return "-5% Upkeep, +5% Max CR, +10% CR, +10% Profile, -5% Accel";
    }

    public static String getMealTooltip(String factionId, int priceFormatted) {
        String f = factionId != null ? factionId.toLowerCase().trim() : "independent";
        StringBuilder sb = new StringBuilder();

        int dur = "independent".equals(f) ? (int) INDIE_DURATION : (int) DEFAULT_DURATION;
        sb.append("Grants ").append(dur).append(" days of Shore Leave across the entire fleet:\n");
        sb.append("- -5% Monthly Supply Upkeep across all fleet ships\n");
        sb.append("- +5% Maximum Combat Readiness (CR) across all combat ships\n");
        sb.append("- +10% Daily Combat Readiness (CR) Recovery Rate across all combat ships\n\n");

        sb.append("Shore Leave Downsides:\n");
        sb.append("- +10% Fleet Sensor Profile (Lax signature discipline & off-duty chatter)\n");
        sb.append("- -5% Campaign Fleet Acceleration (Post-meal sluggishness & resting crew)\n\n");

        sb.append("Wardroom Camaraderie & Mentoring:\n");
        sb.append("- Sponsoring a meal awards Officer Experience and fleet Bonus XP!\n");
        if ("lobster_feast".equals(f) || "luxury_feast".equals(f)) {
            sb.append("- Sponsoring a private banquet awards +25% Camaraderie with your seated officer!");
        } else if ("famine_stew".equals(f)) {
            sb.append("- Relieving portside famine awards +15% Camaraderie with your seated officer!");
        } else {
            sb.append("- Awards Camaraderie based on your officer's personality (Liked dishes +15%, Neutral +10%, Disliked -5%). Native cuisine grants +5% bonus.");
        }

        if (isBuffActive() && !isDigesting()) {
            sb.append("\n\n[Satiated Note: Ordering will replace active ").append(getMealName(getActiveFaction()))
              .append(" perks and reset Shore Leave to ").append(dur).append(" days.]");
        }

        return sb.toString();
    }

    public static void applyBuff(InteractionDialogAPI dialog, String factionId, float durationDays) {
        if (Global.getSector() == null) return;
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        if (mem == null) return;

        boolean wasActive = isBuffActive();

        // Wipe any previous buffs or maluses cleanly before applying new spread
        unapplyStatsFromFleet();

        mem.set(KEY_TIMESTAMP, Global.getSector().getClock().getTimestamp());
        mem.set(KEY_DURATION, durationDays);
        mem.set(KEY_FACTION, factionId);

        if (dialog != null && dialog.getInteractionTarget() != null) {
            String stationName = null;
            if (dialog.getInteractionTarget().getMarket() != null) {
                stationName = dialog.getInteractionTarget().getMarket().getName();
            } else if (dialog.getInteractionTarget().getName() != null) {
                stationName = dialog.getInteractionTarget().getName();
            }
            if (stationName != null) {
                mem.set(KEY_STATION, stationName);
            }
        }

        applyStatsToFleet();
        ensureScriptAdded();

        String mealName = getMealName(factionId);

        if (dialog != null && dialog.getTextPanel() != null) {
            String daysStr = (int) durationDays + " days";
            if (wasActive) {
                dialog.getTextPanel().addParagraph(
                    "The fleet enjoys a fresh spread of " + mealName + ". Shore leave refreshed for " + daysStr + " (-5% Upkeep, +5% Max CR, +10% CR Recovery).",
                    Misc.getPositiveHighlightColor()
                );
                dialog.getTextPanel().highlightInLastPara(Misc.getHighlightColor(), mealName, daysStr, "-5% Upkeep", "+5% Max CR", "+10% CR Recovery");
            } else {
                dialog.getTextPanel().addParagraph(
                    "The fleet gathers around the table for " + mealName + ". Shore leave active for " + daysStr + " (-5% Upkeep, +5% Max CR, +10% CR Recovery).",
                    Misc.getPositiveHighlightColor()
                );
                dialog.getTextPanel().highlightInLastPara(Misc.getHighlightColor(), mealName, daysStr, "-5% Upkeep", "+5% Max CR", "+10% CR Recovery");
            }

            dialog.getTextPanel().setFontSmallInsignia();
            dialog.getTextPanel().addParagraph(
                "FLEET MORALE: Shore Leave active (-5% Monthly Supply Upkeep, +5% Max CR & +10% CR Recovery for combat ships | Downsides: +10% Sensor Profile, -5% Fleet Acceleration).",
                Misc.getPositiveHighlightColor()
            );
            dialog.getTextPanel().highlightInLastPara(Misc.getHighlightColor(), "-5% Monthly Supply Upkeep", "+5% Max CR", "+10% CR Recovery");
            dialog.getTextPanel().highlightInLastPara(Misc.getNegativeHighlightColor(), "+10% Sensor Profile", "-5% Fleet Acceleration");
            dialog.getTextPanel().setFontInsignia();
        }

        // Ensure unified Intel directory is present
        data.campaign.intel.OS_SpoonDirectoryIntel.addIntelIfNeeded();
        lastHintText = null;
    }

    public static void ensureScriptAdded() {
        if (Global.getSector() == null) return;
        Global.getSector().removeTransientScriptsOfClass(OS_ShoreLeaveBuff.class);
        Global.getSector().addTransientScript(new OS_ShoreLeaveBuff());
    }

    public static void applyStatsToFleet() {
        if (Global.getSector() == null) return;
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet == null || playerFleet.getFleetData() == null) return;

        // Universal Shore Leave Base for all paid meals:
        // -5% Monthly Supply Upkeep across all ships
        // +5% Max CR and +10% CR Recovery rate across all combat ships
        for (FleetMemberAPI member : playerFleet.getFleetData().getMembersListCopy()) {
            if (member.isFighterWing()) continue;

            member.getStats().getSuppliesPerMonth().modifyPercent(BUFF_ID, SUPPLY_REDUCTION_PERCENT, "Shore leave & well-rested crew");

            if (!member.isCivilian()) {
                member.getStats().getMaxCombatReadiness().modifyFlat(BUFF_ID, MAX_CR_BONUS_FLAT, "Shore leave & energized crew");
                member.getStats().getBaseCRRecoveryRatePercentPerDay().modifyPercent(BUFF_ID, CR_RECOVERY_BONUS_PERCENT, "Shore leave & energized crew");
            }
        }

        // Universal Shore Leave Downsides (+10% Sensor Profile, -5% Acceleration)
        playerFleet.getStats().getSensorProfileMod().modifyPercent(BUFF_ID, SENSOR_PROFILE_PENALTY_PERCENT, "Shore leave lax signature discipline");
        playerFleet.getStats().getAccelerationMult().modifyMult(BUFF_ID, ACCELERATION_PENALTY_MULT, "Post-meal sluggishness");

        // Apply campaign doctrine from any Loyal Confidant officers
        data.campaign.OS_OfficerFriendship.applyLoyalBonus(playerFleet);
    }

    public static void unapplyStatsFromFleet() {
        if (Global.getSector() == null) return;
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet == null || playerFleet.getFleetData() == null) return;

        // Strip fleet-level stats
        playerFleet.getStats().getAccelerationMult().unmodify(BUFF_ID);
        playerFleet.getStats().getAccelerationMult().unmodify(BUFF_ID + "_sindrian");
        playerFleet.getStats().getDynamic().getStat(Stats.EMERGENCY_BURN_CR_MULT).unmodify(BUFF_ID);
        playerFleet.getStats().getDynamic().getStat(Stats.EMERGENCY_BURN_CR_MULT).unmodify(BUFF_ID + "_path");
        playerFleet.getStats().getSensorRangeMod().unmodify(BUFF_ID);
        playerFleet.getStats().getSensorProfileMod().unmodify(BUFF_ID);
        playerFleet.getStats().getFuelUseNormalMult().unmodify(BUFF_ID);
        playerFleet.getStats().getFuelUseHyperMult().unmodify(BUFF_ID);
        playerFleet.getStats().getFuelUseHyperMult().unmodify(BUFF_ID + "_tt_fuel");
        playerFleet.getStats().getDynamic().getStat(Stats.DIRECT_JUMP_CR_MULT).unmodify(BUFF_ID);
        playerFleet.getStats().getDynamic().getStat(Stats.NON_COMBAT_CREW_LOSS_MULT).unmodify(BUFF_ID);
        playerFleet.getStats().getDynamic().getMod(Stats.PLANETARY_OPERATIONS_MOD).unmodifyPercent(BUFF_ID);
        playerFleet.getStats().getDynamic().getStat(Stats.BATTLE_SALVAGE_MULT_FLEET).unmodify(BUFF_ID);
        playerFleet.getStats().getDynamic().getMod(Stats.SUSTAINED_BURN_BONUS).unmodifyFlat(BUFF_ID);
        playerFleet.getStats().getDynamic().getMod(Stats.SUSTAINED_BURN_BONUS).unmodifyFlat(BUFF_ID + "_indie");
        playerFleet.getStats().getDynamic().getMod(Stats.SUSTAINED_BURN_BONUS).unmodifyFlat(BUFF_ID + "_lobster");
        playerFleet.getStats().getDynamic().getStat(Stats.GO_DARK_DETECTED_AT_MULT).unmodify(BUFF_ID);
        playerFleet.getStats().getDynamic().getStat(Stats.GO_DARK_DETECTED_AT_MULT).unmodify(BUFF_ID + "_tt");
        playerFleet.getStats().getDynamic().getStat(Stats.GO_DARK_DETECTED_AT_MULT).unmodify(BUFF_ID + "_lux");
        playerFleet.getStats().getFleetwideMaxBurnMod().unmodifyFlat(BUFF_ID);

        // Strip ship-level stats
        for (FleetMemberAPI m : playerFleet.getFleetData().getMembersListCopy()) {
            m.getStats().getSuppliesPerMonth().unmodify(BUFF_ID);
            m.getStats().getSuppliesPerMonth().unmodify(BUFF_ID + "_lobster");
            m.getStats().getSuppliesPerMonth().unmodify(BUFF_ID + "_lux");
            m.getStats().getMaxCombatReadiness().unmodify(BUFF_ID);
            m.getStats().getBaseCRRecoveryRatePercentPerDay().unmodify(BUFF_ID);
            m.getStats().getBaseCRRecoveryRatePercentPerDay().unmodify(BUFF_ID + "_path");
            m.getStats().getBaseCRRecoveryRatePercentPerDay().unmodify(BUFF_ID + "_gruel");
            m.getStats().getBaseCRRecoveryRatePercentPerDay().unmodify(BUFF_ID + "_lobster");
            m.getStats().getBaseCRRecoveryRatePercentPerDay().unmodify(BUFF_ID + "_lux");
            m.getStats().getBaseCRRecoveryRatePercentPerDay().unmodify(BUFF_ID + "_relief");
            m.getStats().getRepairRatePercentPerDay().unmodify(BUFF_ID);
            m.getStats().getCrewLossMult().unmodify(BUFF_ID);
            m.getStats().getDynamic().getStat(Stats.CORONA_EFFECT_MULT).unmodify(BUFF_ID);
            m.getStats().getSuppliesToRecover().unmodify(BUFF_ID);
        }

        // Unapply officer loyalty campaign bonuses
        data.campaign.OS_OfficerFriendship.unapplyLoyalBonus(playerFleet);
    }

    public static void syncOnLoad() {
        lastHintText = null;
        activeHintSlot = -1;
        data.campaign.intel.OS_SpoonDirectoryIntel.addIntelIfNeeded();
        if (isBuffActive()) {
            applyStatsToFleet();
            ensureScriptAdded();
        } else {
            unapplyStatsFromFleet();
            if (Global.getSector() != null) {
                MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
                if (mem != null) {
                    mem.unset(KEY_TIMESTAMP);
                    mem.unset(KEY_DURATION);
                    mem.unset(KEY_FACTION);
                    mem.unset(KEY_STATION);
                    mem.unset("$os_officerPerson");
                }
            }
            clearCampaignHint();
            if (Global.getSector() != null && Global.getSector().getPlayerFleet() != null) {
                data.campaign.OS_OfficerFriendship.applyLoyalBonus(Global.getSector().getPlayerFleet());
            }
        }
    }

    public static void clearCampaignHint() {
        if (Global.getSector() == null || Global.getSector().getCampaignUI() == null) return;
        HintPanelAPI hintPanel = Global.getSector().getCampaignUI().getHintPanel();
        if (hintPanel != null && activeHintSlot >= 0) {
            if (hintPanel.hasHint(activeHintSlot)) {
                hintPanel.fadeOutHint(activeHintSlot);
            }
        }
        activeHintSlot = -1;
        lastHintText = null;
    }

    public void updateCampaignHint() {
        if (Global.getSector() == null || Global.getSector().getCampaignUI() == null) return;
        if (Global.getSector().getCampaignUI().isShowingDialog()) return;

        HintPanelAPI hintPanel = Global.getSector().getCampaignUI().getHintPanel();
        if (hintPanel == null) return;

        if (!isBuffActive()) {
            clearCampaignHint();
            return;
        }

        int targetSlot = 0;
        try {
            if (com.fs.starfarer.api.impl.campaign.tutorial.TutorialMissionIntel.isTutorialInProgress()) {
                targetSlot = 1;
            }
        } catch (Throwable ignored) {}

        if (activeHintSlot >= 0 && activeHintSlot != targetSlot) {
            if (hintPanel.hasHint(activeHintSlot)) {
                hintPanel.fadeOutHint(activeHintSlot);
            }
        }
        activeHintSlot = targetSlot;

        String faction = getActiveFaction();
        float remaining = getDaysRemaining();
        String daysStr = String.format("%.1f", remaining);

        String meal = getMealName(faction);
        String perks = getShortPerkSummary(faction);
        String text = "Shore Leave (" + meal + "): " + daysStr + "d - " + perks;
        String[] highlights = new String[]{meal, daysStr + "d", perks};
        Color[] highlightColors = new Color[]{
            Misc.getHighlightColor(),
            Misc.getHighlightColor(),
            Misc.getPositiveHighlightColor()
        };

        if (!text.equals(lastHintText) || !hintPanel.hasHint(activeHintSlot)) {
            LabelAPI label = hintPanel.setHint(activeHintSlot, text, Misc.getBasePlayerColor());
            if (label != null) {
                label.setHighlight(highlights);
                label.setHighlightColors(highlightColors);
            }
            lastHintText = text;
        }
    }

    @Override
    public boolean isDone() {
        return isDone;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (isDone) return;
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return;

        if (!isBuffActive()) {
            unapplyStatsFromFleet();
            MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
            if (mem != null) {
                mem.unset(KEY_TIMESTAMP);
                mem.unset(KEY_DURATION);
                mem.unset(KEY_FACTION);
                mem.unset(KEY_STATION);
            }
            clearCampaignHint();
            data.campaign.OS_OfficerFriendship.applyLoyalBonus(Global.getSector().getPlayerFleet());
            
            if (Global.getSector().getCampaignUI() != null) {
                Global.getSector().getCampaignUI().addMessage("Your fleet's Shore Leave perks have expired.", Misc.getNegativeHighlightColor());
            }

            isDone = true;
            return;
        }

        // Ensure hint panel is clean (CampaignUIRenderingListener handles the HUD overlay)
        if (activeHintSlot >= 0) {
            clearCampaignHint();
        }

        // Throttle ship sync to once per second to maintain zero performance drag
        checkTimer += amount;
        if (checkTimer >= 1.0f) {
            checkTimer = 0f;
            applyStatsToFleet();
        }
    }

    /** Called by Java deserialization if an instance was serialized in older savegames. */
    protected Object readResolve() {
        isDone = true;
        return this;
    }
}

package data.campaign.rulecmd;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetLogisticsAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import data.campaign.buffs.OS_ShoreLeaveBuff;

public class OS_FetchFleetStatus extends BaseCommandPlugin {

    public static class FleetMetrics {
        public int supplies;
        public float suppliesPerDay;
        public int daysOfSupplies;
        public int machinery;

        public List<FleetMemberAPI> damagedShips = new ArrayList<>();
        public FleetMemberAPI mostDamaged = null;
        public int totalShips = 0;

        public int fuel;
        public int maxFuel;
        public float fuelPerLY;
        public float maxRangeLY;
        public int minBurn;

        public int crew;
        public int minCrew;
        public int maxPersonnel;
        public int surplusCrew;

        public int carrierCount = 0;
        public int totalFighterWings = 0;
        public FleetMemberAPI primaryCarrier = null;

        public int maxCapacity;
        public int spaceLeft;
        public int spaceUsed;
        public int cargoPercent;

        public int food;
        public int volatiles;
        public int metals;
        public int rareMetals;
        public int credits;
        public FleetMemberAPI primaryFreighter = null;

        public int marines;
        public int handWeapons;
    }

    public static FleetMetrics calculateMetrics() {
        FleetMetrics m = new FleetMetrics();
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return m;

        CargoAPI cargo = fleet.getCargo();
        FleetDataAPI fleetData = fleet.getFleetData();
        FleetLogisticsAPI logistics = fleet.getLogistics();

        m.supplies = (int) cargo.getSupplies();
        m.suppliesPerDay = logistics.getTotalSuppliesPerDay();
        m.daysOfSupplies = m.suppliesPerDay > 0.01f ? (int) (m.supplies / m.suppliesPerDay) : 999;
        m.machinery = (int) cargo.getCommodityQuantity("heavy_machinery");

        float lowestCondition = 1.0f;
        for (FleetMemberAPI member : fleetData.getMembersListCopy()) {
            if (member.isFighterWing()) continue;
            m.totalShips++;
            float cr = member.getRepairTracker().getCR();
            float maxCr = member.getRepairTracker().getMaxCR();
            float repairFraction = member.getRepairTracker().computeRepairednessFraction();
            float condition = Math.min(repairFraction, cr / Math.max(0.1f, maxCr));

            if (repairFraction < 0.99f || cr < (maxCr - 0.05f)) {
                m.damagedShips.add(member);
                if (condition < lowestCondition) {
                    lowestCondition = condition;
                    m.mostDamaged = member;
                }
            }
        }

        m.fuel = (int) cargo.getFuel();
        m.maxFuel = (int) cargo.getMaxFuel();
        m.fuelPerLY = logistics.getFuelCostPerLightYear();
        m.maxRangeLY = m.fuelPerLY > 0.01f ? (m.fuel / m.fuelPerLY) : 999f;
        m.minBurn = (int) fleetData.getMinBurnLevel();

        m.crew = cargo.getCrew();
        m.minCrew = (int) fleetData.getMinCrew();
        m.maxPersonnel = (int) cargo.getMaxPersonnel();
        m.surplusCrew = Math.max(0, m.crew - m.minCrew);

        for (FleetMemberAPI member : fleetData.getMembersListCopy()) {
            if (member.isFighterWing()) continue;
            int wings = member.getNumFlightDecks();
            if (wings > 0) {
                m.carrierCount++;
                m.totalFighterWings += wings;
                if (m.primaryCarrier == null || member.getHullSpec().getHullSize().ordinal() > m.primaryCarrier.getHullSpec().getHullSize().ordinal()) {
                    m.primaryCarrier = member;
                }
            }
        }

        m.maxCapacity = (int) cargo.getMaxCapacity();
        m.spaceLeft = (int) cargo.getSpaceLeft();
        m.spaceUsed = Math.max(0, m.maxCapacity - m.spaceLeft);
        m.cargoPercent = m.maxCapacity > 0 ? Math.round(((float) m.spaceUsed / m.maxCapacity) * 100f) : 0;

        m.food = (int) cargo.getCommodityQuantity("food");
        m.volatiles = (int) cargo.getCommodityQuantity("volatiles");
        m.metals = (int) cargo.getCommodityQuantity("metals");
        m.rareMetals = (int) cargo.getCommodityQuantity("rare_metals");
        m.credits = (int) cargo.getCredits().get();

        for (FleetMemberAPI member : fleetData.getMembersListCopy()) {
            if (member.isFighterWing()) continue;
            if (member.isCivilian() || member.getCargoCapacity() > 150) {
                if (m.primaryFreighter == null || member.getCargoCapacity() > m.primaryFreighter.getCargoCapacity()) {
                    m.primaryFreighter = member;
                }
            }
        }

        m.marines = cargo.getMarines();
        m.handWeapons = (int) cargo.getCommodityQuantity("hand_weapons");

        return m;
    }

    public static void populateTelemetryMemory(Map<String, MemoryAPI> memoryMap) {
        if (memoryMap == null) return;
        MemoryAPI local = memoryMap.get(MemKeys.LOCAL);
        if (local == null) return;

        FleetMetrics m = calculateMetrics();
        local.set("$os_eng_supplies", String.format("%,d", m.supplies), 0);
        local.set("$os_eng_daily", String.format("%.1f", m.suppliesPerDay), 0);
        local.set("$os_eng_days", String.valueOf(m.daysOfSupplies), 0);
        local.set("$os_eng_machinery", String.format("%,d", m.machinery), 0);
        local.set("$os_nav_fuel", String.format("%,d", m.fuel), 0);
        local.set("$os_nav_maxFuel", String.format("%,d", m.maxFuel), 0);
        local.set("$os_nav_fuelPerLY", String.format("%.1f", m.fuelPerLY), 0);
        local.set("$os_nav_rangeLY", String.format("%.1f", m.maxRangeLY), 0);
        local.set("$os_nav_minBurn", String.valueOf(m.minBurn), 0);
        local.set("$os_crew_total", String.format("%,d", m.crew), 0);
        local.set("$os_crew_min", String.format("%,d", m.minCrew), 0);
        local.set("$os_crew_max", String.format("%,d", m.maxPersonnel), 0);
        local.set("$os_cargo_used", String.format("%,d", m.spaceUsed), 0);
        local.set("$os_cargo_max", String.format("%,d", m.maxCapacity), 0);
        local.set("$os_cargo_left", String.format("%,d", m.spaceLeft), 0);
        local.set("$os_cargo_percent", String.valueOf(m.cargoPercent), 0);
        local.set("$os_cargo_food", String.format("%,d", m.food), 0);
        local.set("$os_cargo_volatiles", String.format("%,d", m.volatiles), 0);
        local.set("$os_cargo_metals", String.format("%,d", m.metals), 0);
        local.set("$os_cargo_rareMetals", String.format("%,d", m.rareMetals), 0);
        local.set("$os_cargo_credits", Misc.getDGSCredits(m.credits), 0);
        local.set("$os_marine_count", String.format("%,d", m.marines), 0);
        local.set("$os_marine_weapons", String.format("%,d", m.handWeapons), 0);
    }

    /**
     * Arranges the 5 department stat cards directly into dialog menu items with live figures & tooltips.
     */
    public static void populateDepartmentCardMenu(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return;
        OptionPanelAPI options = dialog.getOptionPanel();
        options.clearOptions();

        FleetMetrics m = calculateMetrics();

        // 1. Engineering Stat Card
        String engStatus = m.damagedShips.isEmpty() ? "Nominal" : (m.damagedShips.size() + " repairing");
        String optEng = "[Engineering] Supplies: " + String.format("%,d", m.supplies) + " (" + m.daysOfSupplies + "d) | Hulls: " + engStatus;
        options.addOption(optEng, "os_status_engineering");
        options.setTooltip("os_status_engineering",
            "ENGINEERING & DAMAGE CONTROL CARD\n" +
            "- Supplies: " + String.format("%,d", m.supplies) + " units (Daily burn: " + String.format("%.1f", m.suppliesPerDay) + " | " + m.daysOfSupplies + " days buffer)\n" +
            "- Hull Status: " + (m.damagedShips.isEmpty() ? "Nominal (" + m.totalShips + " hulls)" : (m.damagedShips.size() + " hulls repairing")) + "\n" +
            "- Heavy Machinery: " + String.format("%,d", m.machinery) + " units on hand\n\n" +
            "Click to display full engineering and repair diagnostic logs."
        );

        // 2. Astrogation Stat Card
        int fuelPct = m.maxFuel > 0 ? (m.fuel * 100 / m.maxFuel) : 0;
        String optNav = "[Astrogation] Fuel: " + String.format("%,d", m.fuel) + " (" + fuelPct + "%) | Range: " + String.format("%.0f", m.maxRangeLY) + " LY | Burn " + m.minBurn;
        options.addOption(optNav, "os_status_navigation");
        options.setTooltip("os_status_navigation",
            "ASTROGATION & PROPULSION CARD\n" +
            "- Fuel Reserves: " + String.format("%,d", m.fuel) + " / " + String.format("%,d", m.maxFuel) + " units (" + fuelPct + "% capacity)\n" +
            "- Hyperspace Cruising Reach: " + String.format("%.1f", m.maxRangeLY) + " LY (Burn rate: " + String.format("%.1f", m.fuelPerLY) + " fuel/LY)\n" +
            "- Cruising Velocity: Sustained Burn " + m.minBurn + "\n\n" +
            "Click to display full astrogation and drive diagnostic logs."
        );

        // 3. Personnel Stat Card
        String optPers = "[Personnel] Crew: " + String.format("%,d", m.crew) + " (" + m.minCrew + " min) | Wings: " + m.totalFighterWings + " | Marines: " + m.marines;
        options.addOption(optPers, "os_status_personnel");
        options.setTooltip("os_status_personnel",
            "PERSONNEL & FLIGHT OPERATIONS CARD\n" +
            "- Active Spacers: " + String.format("%,d", m.crew) + " crew (Skeleton threshold: " + String.format("%,d", m.minCrew) + " | Surplus: " + String.format("%,d", m.surplusCrew) + " berths)\n" +
            "- Flight Decks: " + m.totalFighterWings + " strike craft wings across " + m.carrierCount + " carriers\n" +
            "- Marine Detachment: " + String.format("%,d", m.marines) + " combat marines | " + String.format("%,d", m.handWeapons) + " heavy weapons\n\n" +
            "Click to display full watch roster and flight operations logs."
        );

        // 4. Quartermaster Stat Card
        String optCargo = "[Quartermaster] Cargo: " + String.format("%,d", m.spaceUsed) + "/" + String.format("%,d", m.maxCapacity) + " (" + m.cargoPercent + "%) | " + Misc.getDGSCredits(m.credits) + " credits";
        options.addOption(optCargo, "os_status_cargo");
        options.setTooltip("os_status_cargo",
            "QUARTERMASTER & LOGISTICS CARD\n" +
            "- Cargo Capacity: " + String.format("%,d", m.spaceUsed) + " / " + String.format("%,d", m.maxCapacity) + " units (" + m.cargoPercent + "% full | " + String.format("%,d", m.spaceLeft) + " free)\n" +
            "- Strategic Stockpiles: " + m.food + " Food | " + m.volatiles + " Volatiles | " + m.metals + " Metals | " + m.rareMetals + " Rare Metals\n" +
            "- Liquid Treasury: " + Misc.getDGSCredits(m.credits) + " credits\n\n" +
            "Click to display full cargo manifest and inventory ledger."
        );

        // 5. Fleet Morale Stat Card
        String optMorale;
        if (OS_ShoreLeaveBuff.isBuffActive()) {
            int days = (int) Math.ceil(OS_ShoreLeaveBuff.getDaysRemaining());
            String faction = OS_ShoreLeaveBuff.getActiveFaction();
            String meal = OS_ShoreLeaveBuff.getMealName(faction);
            optMorale = "[Fleet Morale] Shore Leave: " + meal + " (" + days + "d left)";
            options.addOption(optMorale, "os_status_morale");
            options.setTooltip("os_status_morale",
                "FLEET MORALE & PROVISIONS CARD\n" +
                "- Shore Leave: ACTIVE (" + days + " days remaining)\n" +
                "- Active Spread: " + meal + "\n" +
                "- Morale Bonuses: -5% Monthly Supplies, +5% Max CR, +10% CR Recovery rate\n" +
                "- Downsides: +10% Sensor Profile, -5% Fleet Acceleration\n\n" +
                "Click to display full morale and dietary status report."
            );
        } else {
            optMorale = "[Fleet Morale] Cold Rations (Ready for shore leave)";
            options.addOption(optMorale, "os_status_morale");
            options.setTooltip("os_status_morale",
                "FLEET MORALE & PROVISIONS CARD\n" +
                "- Shore Leave: INACTIVE (Operating on standard cold shipboard rations)\n" +
                "- Recommendation: Sponsoring a meal at 'The Orbiting Spoon' grants crew morale, officer XP, and combat bonuses.\n\n" +
                "Click to display full morale records."
            );
        }

        // Navigation Options
        options.addOption("Return to the table", "orbiting_spoon_start_dispatch");
        options.setTooltip("orbiting_spoon_start_dispatch", "Return to your dining companion and meal options at the table.");
    }

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        String mode = "overview";
        if (params != null && !params.isEmpty()) {
            mode = params.get(0).getString(memoryMap);
        }

        // Never show the flagship in the visual panel; preserve the seated officer's portrait
        restoreVisual(dialog, memoryMap);

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return false;
        FleetMetrics m = calculateMetrics();
        populateTelemetryMemory(memoryMap);

        TextPanelAPI text = dialog.getTextPanel();
        text.setFontSmallInsignia();

        if ("department_menu".equalsIgnoreCase(mode) || "overview".equalsIgnoreCase(mode) || "status".equalsIgnoreCase(mode)) {
            text.addParagraph("You pull up your fleet's encrypted comms channels on your data-slate. Each department head has filed an operational summary card:", Misc.getTextColor());
            populateDepartmentCardMenu(dialog, memoryMap);
            text.setFontInsignia();
            return true;
        }

        if ("engineering".equalsIgnoreCase(mode) || "technical".equalsIgnoreCase(mode)) {
            text.addParagraph("ENGINEERING & MAINTENANCE DIVISION (CHIEF ENGINEER'S LOG)", Misc.getBasePlayerColor());

            String supStr = String.format("%,d", m.supplies) + " units";
            String dailyStr = String.format("%.1f", m.suppliesPerDay) + " units/day";
            String daysStr = m.daysOfSupplies + " days";
            text.addParagraph("- Supplies Stockpile: " + supStr + " (Daily burn: " + dailyStr + " | " + daysStr + " operating buffer)");
            text.highlightInLastPara(Misc.getHighlightColor(), supStr, dailyStr, daysStr);

            String machStr = String.format("%,d", m.machinery) + " units";
            text.addParagraph("- Heavy Machinery: " + machStr + " operational for field repairs and salvage.");
            text.highlightInLastPara(Misc.getHighlightColor(), machStr);

            if (m.damagedShips.isEmpty()) {
                text.addParagraph("- Hull Maintenance: Nominal. All " + m.totalShips + " vessels in peak fighting trim.");
                text.highlightInLastPara(Misc.getPositiveHighlightColor(), "Nominal.");
                text.highlightInLastPara(Misc.getHighlightColor(), "All " + m.totalShips + " vessels");
            } else {
                text.addParagraph("- Maintenance Alert: " + m.damagedShips.size() + " of " + m.totalShips + " vessels undergoing active repairs:");
                text.highlightInLastPara(Misc.getNegativeHighlightColor(), "Maintenance Alert:");
                text.highlightInLastPara(Misc.getHighlightColor(), m.damagedShips.size() + " of " + m.totalShips + " vessels");

                m.damagedShips.sort((a, b) -> {
                    float crA = a.getRepairTracker().getCR();
                    float repA = a.getRepairTracker().computeRepairednessFraction();
                    float condA = Math.min(repA, crA / Math.max(0.1f, a.getRepairTracker().getMaxCR()));
                    float crB = b.getRepairTracker().getCR();
                    float repB = b.getRepairTracker().computeRepairednessFraction();
                    float condB = Math.min(repB, crB / Math.max(0.1f, b.getRepairTracker().getMaxCR()));
                    return Float.compare(condA, condB);
                });

                int shown = 0;
                for (FleetMemberAPI sm : m.damagedShips) {
                    if (shown >= 3) break;
                    float cr = sm.getRepairTracker().getCR();
                    float repair = sm.getRepairTracker().computeRepairednessFraction();
                    int hullPct = Math.round(repair * 100f);
                    int crPct = Math.round(cr * 100f);
                    String shipName = sm.getShipName();
                    String hullClass = sm.getHullSpec().getHullName() + "-class";

                    if (hullPct < 99) {
                        text.addParagraph("   - " + shipName + " (" + hullClass + "): " + hullPct + "% Hull, " + crPct + "% CR (Repairing)");
                        text.highlightInLastPara(Misc.getHighlightColor(), shipName, hullClass, hullPct + "% Hull", crPct + "% CR");
                    } else {
                        text.addParagraph("   - " + shipName + " (" + hullClass + "): " + crPct + "% CR (CR recovery)");
                        text.highlightInLastPara(Misc.getHighlightColor(), shipName, hullClass, crPct + "% CR");
                    }
                    shown++;
                }

                if (m.damagedShips.size() > 3) {
                    int remaining = m.damagedShips.size() - 3;
                    text.addParagraph("   - ... and " + remaining + " other vessels undergoing routine maintenance.");
                    text.highlightInLastPara(Misc.getHighlightColor(), remaining + " other vessels");
                }
            }

            populateDepartmentCardMenu(dialog, memoryMap);

        } else if ("navigation".equalsIgnoreCase(mode) || "astrogation".equalsIgnoreCase(mode)) {
            text.addParagraph("ASTROGATION & PROPULSION DIVISION (CHIEF NAVIGATOR'S LOG)", Misc.getBasePlayerColor());

            String fuelStr = String.format("%,d", m.fuel) + " / " + String.format("%,d", m.maxFuel) + " units";
            int fuelPct = m.maxFuel > 0 ? Math.round(((float) m.fuel / m.maxFuel) * 100f) : 0;
            String fuelPctStr = fuelPct + "%";
            String effStr = String.format("%.1f", m.fuelPerLY) + " fuel/LY";
            String rangeStr = String.format("%.1f", m.maxRangeLY) + " Light-Years";
            text.addParagraph("- Fuel Reserves: " + fuelStr + " (" + fuelPctStr + " capacity | " + rangeStr + " range at " + effStr + ")");
            text.highlightInLastPara(Misc.getHighlightColor(), fuelStr, fuelPctStr, rangeStr, effStr);

            String burnStr = "Burn " + m.minBurn;
            text.addParagraph("- Sustained Drive Velocity: Cruising at standard " + burnStr + ".");
            text.highlightInLastPara(Misc.getHighlightColor(), burnStr);

            if (m.maxRangeLY > 60f) {
                text.addParagraph("- Range Assessment: Optimal. Ample fuel buffer for deep-space hyperspace transit.");
                text.highlightInLastPara(Misc.getPositiveHighlightColor(), "Optimal.");
            } else if (m.maxRangeLY >= 25f) {
                text.addParagraph("- Range Assessment: Moderate. Adequate for local star-system hops.");
                text.highlightInLastPara(Misc.getHighlightColor(), "Moderate.");
            } else {
                text.addParagraph("- Range Assessment: Critical! Refueling urgently recommended before jumping system.");
                text.highlightInLastPara(Misc.getNegativeHighlightColor(), "Critical!");
            }

            populateDepartmentCardMenu(dialog, memoryMap);

        } else if ("personnel".equalsIgnoreCase(mode) || "operations".equalsIgnoreCase(mode) || "marines".equalsIgnoreCase(mode)) {
            text.addParagraph("PERSONNEL & FLIGHT OPERATIONS (BOSUN & FLIGHT BOSS)", Misc.getBasePlayerColor());

            String crewStr = String.format("%,d", m.crew) + " active spacers";
            String minStr = String.format("%,d", m.minCrew);
            String maxStr = String.format("%,d", m.maxPersonnel);
            String surpStr = String.format("%,d", m.surplusCrew) + " surplus berths";
            text.addParagraph("- Active Crew: " + crewStr + " (Skeleton: " + minStr + " | Max: " + maxStr + " | " + surpStr + ")");
            text.highlightInLastPara(Misc.getHighlightColor(), crewStr, minStr, maxStr, surpStr);

            if (m.crew == 0 && m.minCrew == 0) {
                text.addParagraph("- Watch Roster: Fully automated drone fleet. Zero biological crew required.");
                text.highlightInLastPara(Misc.getHighlightColor(), "Fully automated drone fleet.");
            } else if (m.crew < m.minCrew) {
                text.addParagraph("- Watch Roster: CRITICAL UNDERSTAFFING. Below skeleton crew requirements!");
                text.highlightInLastPara(Misc.getNegativeHighlightColor(), "CRITICAL UNDERSTAFFING.");
            } else if (m.crew < (m.minCrew * 1.25f)) {
                text.addParagraph("- Watch Roster: Tight rotations. Skeleton threshold met, but small casualty buffer.");
                text.highlightInLastPara(Misc.getHighlightColor(), "Tight rotations.");
            } else {
                text.addParagraph("- Watch Roster: Optimal staffing. Full 3-shift rotation with fresh damage-control reserves.");
                text.highlightInLastPara(Misc.getPositiveHighlightColor(), "Optimal staffing.");
            }

            if (m.carrierCount > 0) {
                String wingStr = m.totalFighterWings + " strike craft wings";
                String carrStr = m.carrierCount + (m.carrierCount == 1 ? " carrier hull" : " carrier hulls");
                text.addParagraph("- Flight Decks: " + wingStr + " operational across " + carrStr + ".");
                text.highlightInLastPara(Misc.getHighlightColor(), wingStr, carrStr);
            }

            String marineStr = String.format("%,d", m.marines) + " combat marines";
            String wepStr = String.format("%,d", m.handWeapons) + " Heavy Armaments";
            text.addParagraph("- Marine Detachment: " + marineStr + " | " + wepStr + " secured in armory.");
            text.highlightInLastPara(Misc.getHighlightColor(), marineStr, wepStr);

            populateDepartmentCardMenu(dialog, memoryMap);

        } else if ("cargo".equalsIgnoreCase(mode) || "quartermaster".equalsIgnoreCase(mode) || "manifest".equalsIgnoreCase(mode)) {
            text.addParagraph("QUARTERMASTER & LOGISTICS DIVISION (CARGO MASTER'S LEDGER)", Misc.getBasePlayerColor());

            String holdStr = String.format("%,d", m.spaceUsed) + " / " + String.format("%,d", m.maxCapacity) + " units";
            String pctStr = m.cargoPercent + "% full";
            String freeStr = String.format("%,d", m.spaceLeft) + " units free";
            text.addParagraph("- Cargo Capacity: " + holdStr + " (" + pctStr + " | " + freeStr + " free)");
            text.highlightInLastPara(Misc.getHighlightColor(), holdStr, pctStr, freeStr);

            String foodStr = String.format("%,d", m.food) + " Food";
            String volStr = String.format("%,d", m.volatiles) + " Volatiles";
            String metStr = String.format("%,d", m.metals) + " Metals";
            String rareStr = String.format("%,d", m.rareMetals) + " Rare Metals";
            text.addParagraph("- Strategic Commodities: " + foodStr + " | " + volStr + " | " + metStr + " | " + rareStr);
            text.highlightInLastPara(Misc.getHighlightColor(), foodStr, volStr, metStr, rareStr);

            String credStr = Misc.getDGSCredits(m.credits) + " credits";
            text.addParagraph("- Fleet Treasury: " + credStr + " liquid capital in secure accounts.");
            text.highlightInLastPara(Misc.getHighlightColor(), credStr);

            populateDepartmentCardMenu(dialog, memoryMap);

        } else if ("morale".equalsIgnoreCase(mode) || "shoreleave".equalsIgnoreCase(mode)) {
            text.addParagraph("FLEET MORALE & PROVISIONS REPORT", Misc.getBasePlayerColor());

            if (OS_ShoreLeaveBuff.isBuffActive()) {
                int daysLeft = (int) Math.ceil(OS_ShoreLeaveBuff.getDaysRemaining());
                String buffDays = daysLeft + (daysLeft == 1 ? " day" : " days");
                String activeFaction = OS_ShoreLeaveBuff.getActiveFaction();
                String activeMeal = OS_ShoreLeaveBuff.getMealName(activeFaction);
                String activePerk = OS_ShoreLeaveBuff.getMealPerkSummary(activeFaction);

                text.addParagraph("- Active Morale: Shore Leave (" + activeMeal + " | " + buffDays + " remaining).", Misc.getPositiveHighlightColor());
                text.highlightInLastPara(Misc.getHighlightColor(), activeMeal, buffDays);

                text.addParagraph("- Combat Readiness & Upkeep: -5% Monthly Supplies, +5% Max CR, +10% CR Recovery rate.");
                text.highlightInLastPara(Misc.getPositiveHighlightColor(), "-5% Monthly Supplies", "+5% Max CR", "+10% CR Recovery");

                text.addParagraph("- Operational Trade-offs: +10% Sensor Profile (noisy communications), -5% Acceleration.");
                text.highlightInLastPara(Misc.getNegativeHighlightColor(), "+10% Sensor Profile", "-5% Acceleration");

                text.addParagraph("- Regional Culture Impact: " + activePerk, Misc.getHighlightColor());

                if (OS_ShoreLeaveBuff.isDigesting()) {
                    float digRemain = OS_ShoreLeaveBuff.getDigestionDaysRemaining();
                    String digStr = String.format("%.1f", digRemain) + " days";
                    text.addParagraph("- Satiation / Digestion: Leftovers running (" + digStr + " until fresh appetite).", Misc.getHighlightColor());
                    text.highlightInLastPara(Misc.getHighlightColor(), digStr);
                }
            } else {
                text.addParagraph("- Fleet Morale: Standard cold rations. No active shore leave benefits.", Misc.getTextColor());
                text.addParagraph("- Note: Dining at portside mess halls grants crew morale, officer XP, and combat bonuses.", Misc.getGrayColor());
            }

            populateDepartmentCardMenu(dialog, memoryMap);
        }

        text.setFontInsignia();
        return true;
    }

    public static void restoreVisual(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return;
        PersonAPI person = OS_PickRandomOfficer.getActiveOfficerFromMemory(memoryMap);
        if (person != null) {
            dialog.getVisualPanel().showPersonInfo(person, true);
        } else {
            dialog.getVisualPanel().fadeVisualOut();
        }
    }
}

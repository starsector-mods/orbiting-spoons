package data.campaign.intel;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import data.campaign.OS_OfficerFriendship;
import data.campaign.buffs.OS_ShoreLeaveBuff;
import data.campaign.rulecmd.OS_IsVanillaFaction;
import data.campaign.rulecmd.OS_PickRandomOfficer;

/**
 * Tactical and logistical directory for 'The Orbiting Spoon' food fabricators.
 * Combines the Sector Registry, Burndain's Field Notes, and Fleet Morale & Experience Dashboard
 * into a single unified Fleet Log intel interface.
 *
 * Tab 1: [ 1. Sector Registry Table ] - Real-time proximity-sorted directory with map tracking.
 * Tab 2: [ 2. Sectors Unknown (Field Notes) ] - Sector culinary field notes by Anton 'Bones' Burndain.
 * Tab 3: [ 3. Fleet Morale & Field Guide ] - Live interactive morale dashboard, wardroom mentoring & regulations.
 */
public class OS_SpoonDirectoryIntel extends BaseIntelPlugin {

    public static final String INTEL_ID = "os_spoon_directory_intel";

    public static final String TAB_REGISTRY = "tab_registry";
    public static final String TAB_REVIEWS = "tab_reviews";
    public static final String TAB_REGULATIONS = "tab_regulations";
    public static final String TAB_MORALE = "tab_morale";

    public static enum DirectoryTab {
        REGISTRY,
        REVIEWS,
        REGULATIONS
    }

    private DirectoryTab activeTab = DirectoryTab.REGISTRY;
    private transient Boolean lastBuffActive = null;
    private transient String lastBuffFaction = null;
    private transient boolean checkedLegacyIntel = false;

    public static void addIntelIfNeeded() {
        if (Global.getSector() == null || Global.getSector().getIntelManager() == null) return;
        if (!Global.getSector().getIntelManager().hasIntelOfClass(OS_SpoonDirectoryIntel.class)) {
            Global.getSector().getIntelManager().addIntel(new OS_SpoonDirectoryIntel());
        }
    }

    public OS_SpoonDirectoryIntel() {
    }

    /** Called by Java deserialization - ensures fields added after initial release get sane defaults. */
    protected Object readResolve() {
        if (activeTab == null) activeTab = DirectoryTab.REGISTRY;
        checkedLegacyIntel = false;
        return this;
    }

    @Override
    protected void advanceImpl(float amount) {
        super.advanceImpl(amount);

        // Ensure OS_SpoonDirectoryIntel is the ONLY intel item spawned by this mod; purge Shore Leave intel once per session
        if (!checkedLegacyIntel) {
            checkedLegacyIntel = true;
            OS_ShoreLeaveIntel.cleanupLegacyIntel();
        }

        // Notify intel log when morale state changes
        boolean active = OS_ShoreLeaveBuff.isBuffActive();
        String faction = OS_ShoreLeaveBuff.getActiveFaction();
        if (lastBuffActive != null && (lastBuffActive != active || !Objects.equals(lastBuffFaction, faction))) {
            sendUpdateIfPlayerHasIntel(new Object(), false);
        }
        lastBuffActive = active;
        lastBuffFaction = faction;
    }

    @Override
    public String getName() {
        return "The Orbiting Spoon: Spacer's Registry & Field Guide";
    }

    @Override
    public String getSmallDescriptionTitle() {
        return "The Orbiting Spoon: Spacer's Registry & Field Guide";
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        Color titleColor = getTitleColor(mode);
        info.addPara(getName(), titleColor, 0f);
        addBulletPoints(info, mode);
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, Color tc, float pad) {
        // Bullet 1: Nearest diner with proximity & faction color
        SectorEntityToken nearest = getMapLocation(null);
        if (nearest != null && nearest.getMarket() != null) {
            MarketAPI m = nearest.getMarket();
            SectorEntityToken player = Global.getSector().getPlayerFleet();
            float distLY = 0f;
            boolean inSys = false;
            if (player != null && player.getLocationInHyperspace() != null && m.getPrimaryEntity() != null && m.getPrimaryEntity().getLocationInHyperspace() != null) {
                inSys = player.getStarSystem() != null && player.getStarSystem() == m.getStarSystem();
                if (inSys) {
                    distLY = 0f;
                } else {
                    distLY = Misc.getDistanceLY(player.getLocationInHyperspace(), m.getPrimaryEntity().getLocationInHyperspace());
                }
            }
            String distStr = inSys ? "in-system" : String.format("%.1f LY away", distLY);
            String factionShort = m.getFaction() != null ? getShortFactionName(m.getFactionId(), m.getFaction().getDisplayName()) : "Independent";
            Color factionColor = getFactionColorSafe(m);

            LabelAPI b1 = info.addPara("Nearest diner: " + m.getName() + " (" + factionShort + ", " + distStr + ")", pad);
            b1.setHighlight(m.getName(), factionShort, distStr);
            b1.setHighlightColors(Misc.getHighlightColor(), factionColor, inSys ? Misc.getPositiveHighlightColor() : Misc.getHighlightColor());
        } else {
            LabelAPI b1 = info.addPara("Nearest diner: None detected in civilized space", pad);
            b1.setHighlight("None detected");
            b1.setHighlightColors(Misc.getGrayColor());
        }

        // Bullet 2: Fleet Morale state (Active with days remaining and buffs, or 'Ready to dine')
        if (OS_ShoreLeaveBuff.isBuffActive()) {
            float rem = OS_ShoreLeaveBuff.getDaysRemaining();
            String daysStr = String.format("%.1f", rem);
            String text = "Fleet Morale: Active (" + daysStr + "d remaining - -5% Upkeep, +5% Max CR, +10% CR Recovery)";
            LabelAPI b2 = info.addPara(text, pad);
            b2.setHighlight("Active", daysStr + "d remaining", "-5% Upkeep", "+5% Max CR", "+10% CR Recovery");
            b2.setHighlightColors(Misc.getPositiveHighlightColor(), Misc.getHighlightColor(), Misc.getPositiveHighlightColor(), Misc.getPositiveHighlightColor(), Misc.getPositiveHighlightColor());
        } else {
            String text = "Fleet Morale: Ready to dine (Standard baseline rations)";
            LabelAPI b2 = info.addPara(text, pad);
            b2.setHighlight("Ready to dine");
            b2.setHighlightColors(Misc.getHighlightColor());
        }

        // Bullet 3: Next debriefing recommendation
        List<PersonAPI> officers = OS_PickRandomOfficer.getHumanOfficers();
        if (officers.isEmpty()) {
            String text = "Next Debrief: Recruit bridge officers to unlock wardroom mentoring";
            LabelAPI b3 = info.addPara(text, pad);
            b3.setHighlight("Recruit bridge officers");
            b3.setHighlightColors(Misc.getHighlightColor());
        } else {
            PersonAPI retrainReady = null;
            PersonAPI closestOfficer = null;
            int closestDiff = 1000;

            for (PersonAPI officer : officers) {
                int fs = OS_OfficerFriendship.getFriendship(officer);
                if (fs >= OS_OfficerFriendship.THRESHOLD_RETRAIN) {
                    if (retrainReady == null) {
                        retrainReady = officer;
                    }
                } else {
                    int diff = OS_OfficerFriendship.THRESHOLD_RETRAIN - fs;
                    if (diff < closestDiff) {
                        closestDiff = diff;
                        closestOfficer = officer;
                    }
                }
            }

            if (retrainReady != null) {
                int fs = OS_OfficerFriendship.getFriendship(retrainReady);
                String tier = OS_OfficerFriendship.getTierName(fs);
                String text = "Next Debrief: " + retrainReady.getNameString() + " (" + tier + ") ready for tactical skill retraining";
                LabelAPI b3 = info.addPara(text, pad);
                b3.setHighlight(retrainReady.getNameString(), tier, "ready for tactical skill retraining");
                b3.setHighlightColors(Misc.getHighlightColor(), Misc.getHighlightColor(), Misc.getPositiveHighlightColor());
            } else if (closestOfficer != null) {
                int fs = OS_OfficerFriendship.getFriendship(closestOfficer);
                String text = "Next Debrief: Dine with " + closestOfficer.getNameString() + " (" + fs + "% Camaraderie) to unlock skill debriefs (50%)";
                LabelAPI b3 = info.addPara(text, pad);
                b3.setHighlight(closestOfficer.getNameString(), fs + "% Camaraderie", "50%");
                b3.setHighlightColors(Misc.getHighlightColor(), Misc.getHighlightColor(), Misc.getPositiveHighlightColor());
            } else {
                String text = "Next Debrief: Sponsoring meals mentors officers (Officer XP & Fleet Bonus XP)";
                LabelAPI b3 = info.addPara(text, pad);
                b3.setHighlight("Officer XP", "Fleet Bonus XP");
                b3.setHighlightColors(Misc.getPositiveHighlightColor(), Misc.getPositiveHighlightColor());
            }
        }
    }

    @Override
    protected void bullet(TooltipMakerAPI info) {
        info.setBulletedListMode(INDENT);
        info.setTextWidthOverride(0f);
    }

    @Override
    protected void indent(TooltipMakerAPI info) {
        info.setBulletedListMode(INDENT);
        info.setTextWidthOverride(0f);
    }

    @Override
    protected void unindent(TooltipMakerAPI info) {
        info.setBulletedListMode(null);
        info.setTextWidthOverride(0f);
    }

    @Override
    public boolean doesButtonHaveConfirmDialog(Object buttonId) {
        if (TAB_REGISTRY.equals(buttonId) || TAB_REVIEWS.equals(buttonId) || TAB_REGULATIONS.equals(buttonId) || TAB_MORALE.equals(buttonId)) {
            return false;
        }
        return super.doesButtonHaveConfirmDialog(buttonId);
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (TAB_REGISTRY.equals(buttonId)) {
            activeTab = DirectoryTab.REGISTRY;
            ui.updateUIForItem(this);
        } else if (TAB_REVIEWS.equals(buttonId)) {
            activeTab = DirectoryTab.REVIEWS;
            ui.updateUIForItem(this);
        } else if (TAB_REGULATIONS.equals(buttonId) || TAB_MORALE.equals(buttonId)) {
            activeTab = DirectoryTab.REGULATIONS;
            ui.updateUIForItem(this);
        } else {
            super.buttonPressConfirmed(buttonId, ui);
        }
    }

    @Override
    public boolean hasSmallDescription() {
        return true;
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        createDescriptionContent(info, width - 12f, height, false);
    }

    @Override
    public boolean hasLargeDescription() {
        return true;
    }

    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        float scrollbarPad = 14f;
        TooltipMakerAPI desc = panel.createUIElement(width, height, true);
        createDescriptionContent(desc, width - scrollbarPad, height, true);
        panel.addUIElement(desc).inTL(0, 0);
    }

    /**
     * Shared renderer for both small intel pane and expanded sub-window.
     */
    protected void createDescriptionContent(TooltipMakerAPI info, float width, float height, boolean isExpanded) {
        float opad = 10f;
        float spad = 3f;
        float contentWidth = Math.max(260f, width - 12f);

        info.setParaInsigniaLarge();

        // Intro narrative
        info.addPara(
            "An underground spacer directory and culinary field guide tracking intact Domain-era automated food fabricators-colloquially known as 'The Orbiting Spoon'. Installed in station concourses and port bars across the Core Worlds, these indestructible machines dispense warm regional meals to deckhands and fleet commanders alike.",
            opad
        );

        // Horizontal Left-to-Right Tab Navigation Bar
        Color base = Misc.getBasePlayerColor();
        Color bg = Misc.getDarkPlayerColor();
        Color dark = Misc.getDarkPlayerColor();
        Color bright = Misc.getBrightPlayerColor();

        float btnHeight = 26f;
        float gap = 4f;
        float btnWidth = (contentWidth - gap * 2f) / 3f;

        info.addSectionHeading("Directory Navigation (Select Tab)", Alignment.MID, opad);

        TooltipMakerAPI t1 = info.beginSubTooltip(btnWidth);
        t1.setButtonFontVictor14();
        t1.addButton(
            (activeTab == DirectoryTab.REGISTRY ? "> " : "") + "1. Registry",
            TAB_REGISTRY,
            activeTab == DirectoryTab.REGISTRY ? bright : base,
            activeTab == DirectoryTab.REGISTRY ? dark : bg,
            btnWidth, btnHeight, 0f
        );
        info.endSubTooltip();

        TooltipMakerAPI t2 = info.beginSubTooltip(btnWidth);
        t2.setButtonFontVictor14();
        t2.addButton(
            (activeTab == DirectoryTab.REVIEWS ? "> " : "") + "2. Sectors Unknown",
            TAB_REVIEWS,
            activeTab == DirectoryTab.REVIEWS ? bright : base,
            activeTab == DirectoryTab.REVIEWS ? dark : bg,
            btnWidth, btnHeight, 0f
        );
        info.endSubTooltip();

        TooltipMakerAPI t3 = info.beginSubTooltip(btnWidth);
        t3.setButtonFontVictor14();
        t3.addButton(
            (activeTab == DirectoryTab.REGULATIONS ? "> " : "") + "3. Fleet Morale",
            TAB_REGULATIONS,
            activeTab == DirectoryTab.REGULATIONS ? bright : base,
            activeTab == DirectoryTab.REGULATIONS ? dark : bg,
            btnWidth, btnHeight, 0f
        );
        info.endSubTooltip();

        t1.setHeightSoFar(btnHeight);
        t2.setHeightSoFar(btnHeight);
        t3.setHeightSoFar(btnHeight);

        info.addCustom(t1, spad);
        info.addCustomDoNotSetPosition(t2).getPosition().rightOfTop(t1, gap);
        info.addCustomDoNotSetPosition(t3).getPosition().rightOfTop(t2, gap);

        UIComponentAPI spacer = info.addSpacer(opad);
        spacer.getPosition().belowLeft(t1, opad);

        // Render Active Tab Content
        switch (activeTab) {
            case REGISTRY:
                renderRegistryTab(info, contentWidth, opad, spad);
                break;
            case REVIEWS:
                renderReviewsTab(info, contentWidth, opad, spad);
                break;
            case REGULATIONS:
                renderMoraleAndFieldGuideTab(info, contentWidth, opad, spad);
                break;
        }
    }

    /**
     * TAB 1: Sector Registry Table (Read from left to right)
     */
    protected void renderRegistryTab(TooltipMakerAPI info, float width, float opad, float spad) {
        info.setParaInsigniaLarge();
        info.addSectionHeading("Operational Fabricators (Sorted by Proximity)", Alignment.MID, opad);
        info.addPara("Review operational facilities below. Centering this log on the Star Map immediately pans to and tracks the nearest operational diner.", Misc.getGrayColor(), spad);

        SectorEntityToken player = Global.getSector().getPlayerFleet();
        List<SpoonDinerEntry> diners = new ArrayList<>();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.getSize() >= 4 && !market.isHidden() && market.getPrimaryEntity() != null) {
                String factionId = market.getFactionId();
                if (!OS_IsVanillaFaction.isVanilla(factionId)) continue;

                SpoonDinerEntry entry = new SpoonDinerEntry();
                entry.market = market;
                entry.dishName = getSignatureDish(factionId);
                entry.price = getDishPrice(factionId);

                if (player != null && player.getLocationInHyperspace() != null && market.getPrimaryEntity() != null && market.getPrimaryEntity().getLocationInHyperspace() != null) {
                    boolean sameSystem = player.getStarSystem() != null && player.getStarSystem() == market.getStarSystem();
                    entry.inSystem = sameSystem;
                    if (sameSystem) {
                        entry.distLY = 0f;
                    } else {
                        entry.distLY = Misc.getDistanceLY(player.getLocationInHyperspace(), market.getPrimaryEntity().getLocationInHyperspace());
                    }
                } else {
                    entry.distLY = 999f;
                    entry.inSystem = false;
                }
                diners.add(entry);
            }
        }

        Collections.sort(diners);

        float usableWidth = Math.max(260f, width);
        float colColony = usableWidth * 0.22f;
        float colSystem = usableWidth * 0.16f;
        float colFaction = usableWidth * 0.16f;
        float colDish = usableWidth * 0.24f;
        float colPrice = usableWidth * 0.10f;
        float colDist = usableWidth * 0.12f;

        Color base = Misc.getBasePlayerColor();
        Color dark = Misc.getDarkPlayerColor();
        Color bright = Misc.getBrightPlayerColor();

        info.beginTable(base, dark, bright, 20f,
            "Colony", colColony,
            "System", colSystem,
            "Faction", colFaction,
            "Regional Specialty", colDish,
            "Price", colPrice,
            "Distance", colDist
        );

        boolean isFirst = true;
        for (SpoonDinerEntry entry : diners) {
            MarketAPI m = entry.market;
            String sysName = m.getStarSystem() != null ? m.getStarSystem().getBaseName() : "Deep Space";
            Color factionColor = getFactionColorSafe(m);
            Color colonyColor = factionColor;
            String factionDisplay = m.getFaction() != null ? getShortFactionName(m.getFactionId(), m.getFaction().getDisplayName()) : "Independent";
            String priceStr = entry.price + " credits";

            String distStr;
            Color distColor;
            if (entry.inSystem) {
                distStr = "Here";
                distColor = Misc.getPositiveHighlightColor();
            } else {
                distStr = String.format("%.1f LY", entry.distLY);
                distColor = isFirst ? Misc.getPositiveHighlightColor() : Misc.getTextColor();
            }

            if (isFirst) {
                info.addRowWithGlow(
                    Alignment.LMID, colonyColor, m.getName(),
                    Alignment.LMID, Misc.getTextColor(), sysName,
                    Alignment.LMID, factionColor, factionDisplay,
                    Alignment.LMID, Misc.getHighlightColor(), entry.dishName,
                    Alignment.RMID, Misc.getHighlightColor(), priceStr,
                    Alignment.RMID, distColor, distStr
                );
                isFirst = false;
            } else {
                info.addRow(
                    Alignment.LMID, colonyColor, m.getName(),
                    Alignment.LMID, Misc.getTextColor(), sysName,
                    Alignment.LMID, factionColor, factionDisplay,
                    Alignment.LMID, Misc.getTextColor(), entry.dishName,
                    Alignment.RMID, Misc.getHighlightColor(), priceStr,
                    Alignment.RMID, distColor, distStr
                );
            }
        }

        info.addTable("No operational Orbiting Spoon units found in civilized space.", 0, 5f);
        info.addSpacer(8f);
    }

    /**
     * TAB 2: Galley Confidential across the Persean Sector
     * Field notes by Anton 'Bones' Burndain (Chief Mess Attendant, Hegemony Auxiliaries, Dishonorably Discharged)
     * Book: "Sectors Unknown: Galley Confidential across the Persean Void"
     */
    protected void renderReviewsTab(TooltipMakerAPI info, float width, float opad, float spad) {
        info.setParaInsigniaLarge();
        info.addSectionHeading("Sectors Unknown: Galley Confidential across the Persean Void", Alignment.MID, opad);

        info.addPara(
            "\"Your flagship is not a Domain shrine. It's a pressurized iron bucket hurtling through a radioactive graveyard. Eat dirty, drink deep, tip the galley crew, and never-ever-order refrigerated seafood when the spaceport's reactor tap is blinking amber. Life is short, the Path is angry, and hyperspace doesn't care if you died on an empty stomach.\"",
            Misc.getTextColor(),
            opad
        );
        LabelAPI authLabel = info.addPara("- Anton 'Bones' Burndain (Chief Mess Attendant, Hegemony Auxiliaries, Dishonorably Discharged)", Misc.getGrayColor(), spad);
        authLabel.setHighlight("Anton 'Bones' Burndain");
        authLabel.setHighlightColors(Misc.getHighlightColor());
        info.addSpacer(4f);

        // 1. Sindrian Diktat
        addReviewCard(info, opad, spad,
            "[No Transponders: Cruorian Reeds] Sindrian Diktat: Volturnian Lobster Feast (600 credits)",
            getFactionColorSafe("sindrian_diktat"),
            "Volturnian blue-shell lobster poached in fiery clarified pepper butter, served beneath glowing banners of the Lion's Guard. The meat is sweet, tender, and dripping in decadent fat. The catch? It takes thirty patrol cutters burning military-grade fuel to keep poachers from ever touching the reefs. Every bite is seasoned with the bitter sweat of Askonia's dockworkers and the paranoia of an authoritarian petrol-state. Pure, unadulterated decadence.",
            "Rating: 4.5 / 5 Spoons - \"Eat like a tyrant before the fuel tanks blow.\"",
            "Bones's Field Rule: \"Never eat shellfish on a station where the security guards look twitchy. If the local ratings are eating turnip mash, you order turnip mash.\""
        );

        // 2. Luddic Church
        addReviewCard(info, opad, spad,
            "[Sectors Unknown: Gilead's Breadbasket] Luddic Church: Pilgrim's Hearth Harvest Feast (200 credits)",
            getFactionColorSafe("luddic_church"),
            "Real bread. Real stone-ground barley. Real butter churned by human hands on Gilead, served in a quiet station refectory over solemn prayers of thanks. No chemical synthesizers, no nutrient paste, no corporate bullshit. Taking a bite of warm crumb that grew in honest soil will make even the most hardened Tri-Tachyon mercenary stare at the floor and reconsider their life choices. In a dying sector, this is holy.",
            "Rating: 5.0 / 5 Spoons - \"Church for your stomach. Amen.\"",
            "Bones's Field Rule: \"If the bread is holy, don't argue with the deacons. Chew slow, shut your mouth, and leave a modest tithe in the wooden bowl.\""
        );

        // 3. Persean League
        addReviewCard(info, opad, spad,
            "[A Cook's Burn: Kazeron's Terraces] Persean League: Archon's Grand Mezze Banquet (350 credits)",
            getFactionColorSafe("persean"),
            "Crisp flatbread, rosemary-rubbed waterfowl skewers, and chilled citrus liqueur served on glazed earthenware behind linen awnings. It is breezy, fragrant, and meticulously refined to stroke the egos of independent merchant factors while they haggle over tariff exemptions. A bit pretentious, but the citrus cut through engine grease like nothing else in the Core.",
            "Rating: 4.0 / 5 Spoons - \"Good diplomacy on an empty stomach.\"",
            "Bones's Field Rule: \"Sip the citrus wine, laugh at the oligarch's terrible jokes, and make sure your nav-officer checks the docking tariff fine print before the second course.\""
        );

        // 4. Independents
        addReviewCard(info, opad, spad,
            "[Galley Confidential: The 24-Hour Airlock] Independents: Loaded Spacer's Full-Burn Set (200 credits)",
            getFactionColorSafe("independent"),
            "The eternal backbone of the void. Battered blue Domain fabricator, cracked vinyl booths, thick root-vegetable beef stew, a stack of hot waffles slathered in butter, and scalded coffee. It's what freelance haulers, asteroid miners, and war-weary captains have been eating since the Collapse. Honest, greasy, and guaranteed to carry your crew through three consecutive hyperspace burns.",
            "Rating: 4.0 / 5 Spoons - \"Old reliable. The grease holds the void together.\"",
            "Bones's Field Rule: \"The uglier the diner and the more dented the dispenser chute, the better the waffles. If the counter stool doesn't wobble, you're on the wrong station.\""
        );

        // 5. Tri-Tachyon
        addReviewCard(info, opad, spad,
            "[Sectors Unknown: Port Tse Corporate Lounge] Tri-Tachyon: Executive Synth-Steak Suite (450 credits)",
            getFactionColorSafe("tritachyon"),
            "A sterile, terrifying miracle of bio-molecular synthesis. Zero connective tissue, mathematically perfect fat-marbling, and laser-seared in an acoustic-damped lounge while glowing corporate tickers blink over your head. It tastes like whatever quarterly profit margin Tri-Tachyon's algorithmic flavor-models decided you wanted to taste. It's paired with nootropics that make you want to sign a non-disclosure agreement. Clean, expensive, and completely devoid of humanity.",
            "Rating: 3.5 / 5 Spoons - \"Don't ask what culture vat it came from.\"",
            "Bones's Field Rule: \"If a Tri-Tachyon food engineer tells you a meat cut is 'conceptually delicious and biochemically optimized', do not sign the receipt.\""
        );

        // 6. Hegemony
        addReviewCard(info, opad, spad,
            "[The Nasty Chits: Chicomoztoc Gantry Mess] Hegemony: Commissary Auxiliary Rations (150 credits)",
            getFactionColorSafe("hegemony"),
            "Dense hardtack that could plug a hull breach, and a bowl of iron-soy sludge hot enough to strip paint off a bulkhead. It has no garlic, no pepper, no joy, and precisely the caloric density demanded by High Hegemon naval ordinance 44-A. It tastes like grey primer and obedience. Eat it with black chicory coffee before a twelve-hour patrol shift. It won't warm your soul, but your weapons officer won't faint at the tactical console either.",
            "Rating: 2.5 / 5 Spoons - \"Eat it standing up. Tastes like martial law.\"",
            "Bones's Field Rule: \"Never complain about the hardtack to the cook. The cook has a wrench, an apron covered in soy broth, and friends in the military police.\""
        );

        // 7. Pirates
        addReviewCard(info, opad, spad,
            "[No Transponders: Donkers Salvage Yards] Pirates: Fringe Scavenger Platter (100 credits)",
            getFactionColorSafe("pirates"),
            "Mystery ribs charred over open engine manifolds, drowned in peppery fungal sludge to cover up the rot, slammed onto sheet metal next to moonshine that could dissolve your shields. Scarred corsairs kick the machine when it jams and roll dice for the scraps. You will experience either profound animal euphoria or catastrophic gastrointestinal failure within forty minutes. Roll the dice, spacer.",
            "Rating: 2.0 / 5 Spoons - \"Hazard pay required. I loved every filthy bite.\"",
            "Bones's Field Rule: \"Keep your back to the bulkhead, your sidearm thumb-break unclasped, and never ask what animal had four ribs that thick.\""
        );

        // 8. Luddic Path
        addReviewCard(info, opad, spad,
            "[Sectors Unknown: Cell Bunker Galley] Luddic Path: Ascetic Penance Broth (50 credits)",
            getFactionColorSafe("luddic_path"),
            "Tastes like crushed gravel simmered in uncalibrated reactor runoff. The cell fighters hammer off the fabricator's seasoning injectors with chisels because pleasure is a sin of the Machine. Salt is considered an unholy distraction from holy martyrdom. If their torpedo aim was as crude as this turnip mash, the Sector would finally have peace. Buy it only if you are twenty light-years out of fuel and facing active starvation.",
            "Rating: 0.5 / 5 Spoons - \"Penance, indeed. May the Prophet forgive the cook.\"",
            "Bones's Field Rule: \"Do not reach for the salt shaker. There is no salt shaker, and asking for one will start a thirty-minute theological tribunal.\""
        );

        info.addSpacer(10f);
    }

    protected void addReviewCard(TooltipMakerAPI info, float opad, float spad, String heading, Color factionColor, String review, String rating, String proTip) {
        info.addSectionHeading(heading, Alignment.LMID, opad);
        info.setParaInsigniaLarge();
        info.addPara(review, Misc.getTextColor(), spad);
        LabelAPI rLabel = info.addPara(rating, Misc.getHighlightColor(), spad);
        rLabel.setHighlightColor(Misc.getBrightPlayerColor());
        LabelAPI tipLabel = info.addPara(proTip, Misc.getGrayColor(), spad);
        tipLabel.setHighlight("Bones's Field Rule:");
        tipLabel.setHighlightColors(Misc.getHighlightColor());
    }

    /**
     * TAB 3: Fleet Morale & Field Guide
     * Live interactive status dashboard:
     * - Section A: Current Fleet Morale & Shore Leave Status
     * - Section B: Wardroom Experience & Tactical Mentoring
     * - Section C: Commissary Regulations & Port Facilities
     */
    protected void renderMoraleAndFieldGuideTab(TooltipMakerAPI info, float width, float opad, float spad) {
        info.setTextWidthOverride(0f);
        info.setBulletedListMode(null);
        info.setParaInsigniaLarge();
        info.addSectionHeading("Fleet Morale & Naval Field Guide", Alignment.MID, opad);

        // Section A: Current Fleet Morale & Shore Leave Status
        info.addSectionHeading("Section A: Current Fleet Morale & Shore Leave Status", Alignment.LMID, opad);

        boolean isActive = OS_ShoreLeaveBuff.isBuffActive();
        if (isActive) {
            float rem = OS_ShoreLeaveBuff.getDaysRemaining();
            String daysStr = String.format("%.1f days remaining", rem);
            String faction = OS_ShoreLeaveBuff.getActiveFaction();
            String meal = OS_ShoreLeaveBuff.getMealName(faction);
            String station = getStationOfOrigin(faction);

            // Bright dashboard status card
            info.addPara("STATUS: SHORE LEAVE ACTIVE (" + daysStr + ")", Misc.getPositiveHighlightColor(), spad);

            float usableWidth = Math.max(260f, width);
            float colParam = Math.round(usableWidth * 0.35f);
            float colVal = usableWidth - colParam - 4f;

            info.beginTable(Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), 22f,
                "Metric", colParam,
                "Current Fleet Status", colVal
            );
            info.addRowWithGlow(Alignment.LMID, Misc.getTextColor(), "Time Remaining", Alignment.LMID, Misc.getPositiveHighlightColor(), daysStr);
            info.addRow(Alignment.LMID, Misc.getTextColor(), "Active Meal / Cuisine", Alignment.LMID, Misc.getHighlightColor(), meal);
            info.addRow(Alignment.LMID, Misc.getTextColor(), "Station of Origin", Alignment.LMID, Misc.getHighlightColor(), station);

            boolean isDigesting = OS_ShoreLeaveBuff.isDigesting();
            if (isDigesting) {
                float digRem = OS_ShoreLeaveBuff.getDigestionDaysRemaining();
                String digStr = String.format("%.1f days remaining", digRem);
                info.addRow(Alignment.LMID, Misc.getTextColor(), "Digestion Status", Alignment.LMID, Misc.getHighlightColor(), "Leftover provisions running: " + digStr);
            } else {
                info.addRow(Alignment.LMID, Misc.getTextColor(), "Digestion Status", Alignment.LMID, Misc.getPositiveHighlightColor(), "Leftover provisions cleared - Fleet ready for another feast");
            }
            info.addTable("", 0, 5f);
            info.addSpacer(6f);

            info.addPara("Active Campaign Modifiers & Morale Stats:", Misc.getHighlightColor(), spad);
            bullet(info);
            addBullet(info, "Supply Maintenance: -5% Monthly Supply Upkeep across all fleet ships.", spad, Misc.getPositiveHighlightColor(), "-5%");
            addBullet(info, "Combat Readiness: +5% Max Combat Readiness (CR) across all combat hulls.", spad, Misc.getPositiveHighlightColor(), "+5% Max Combat Readiness (CR)");
            addBullet(info, "Readiness Recovery: +10% Daily CR Recovery Rate across all fleet ships.", spad, Misc.getPositiveHighlightColor(), "+10% Daily CR Recovery Rate");
            addBullet(info, "Sensor Profile: +10% Fleet Sensor Profile (Lax radio discipline & station chatter).", spad, Misc.getNegativeHighlightColor(), "+10% Fleet Sensor Profile");
            addBullet(info, "Sub-Light Maneuvering: -5% Campaign Fleet Acceleration (Sluggish watchstanders).", spad, Misc.getNegativeHighlightColor(), "-5% Campaign Fleet Acceleration");
            unindent(info);
        } else {
            // Expired / Inactive
            info.addPara("STATUS: FLEET READY FOR SHORE LEAVE", Misc.getHighlightColor(), spad);
            info.addPara(
                "Your fleet is currently operating on standard baseline rations with no active Shore Leave perks. Crew spirits and readiness can be elevated by taking shore leave at an authentic Space Diner.", Misc.getTextColor(), spad);
            info.addSpacer(4f);
            info.addPara("Advice on Finding the Nearest Space Diner:", Misc.getHighlightColor(), spad);
            bullet(info);
            addBullet(info, "Switch to Tab 1 (Sector Registry Table) to view all operational Domain fabricators sorted by real-time hyperspace proximity.", spad, Misc.getHighlightColor(), "Tab 1 (Sector Registry Table)");
            addBullet(info, "Centering this guide on the Star Map immediately tracks and highlights the nearest operational station.", spad, Misc.getHighlightColor(), "Star Map");
            addBullet(info, "Dock at any Market Size 4+ colony of a recognized Core faction and open the Bar to visit 'The Orbiting Spoon'.", spad, Misc.getHighlightColor(), "Market Size 4+", "Bar");
            addBullet(info, "Ordering a hot meal spread grants 14 to 21 days of Shore Leave (-5% Upkeep, +5% Max CR, +10% CR Recovery) and opens wardroom mentoring.", spad, Misc.getPositiveHighlightColor(), "14 to 21 days of Shore Leave", "-5% Upkeep, +5% Max CR, +10% CR Recovery");
            unindent(info);
        }
        info.addSpacer(8f);

        // Section B: Wardroom Experience & Tactical Mentoring
        info.addSectionHeading("Section B: Wardroom Experience & Tactical Mentoring", Alignment.LMID, opad);

        info.addPara("Informal Officer Mentoring (Officer XP & Fleet Bonus XP):", Misc.getHighlightColor(), spad);
        info.addPara(
            "Inviting bridge officers to join you at 'The Orbiting Spoon' serves as an informal mentoring environment away from bridge klaxons. Sponsoring a hot meal spread awards direct Officer XP to your dining companion and grants Fleet Bonus XP (providing 100% bonus XP return when Story Points are spent). Dining also builds Camaraderie, advancing relationships from Professional Acquaintance to Trusted Comrade (50%) and Loyal Confidant (75%).", Misc.getTextColor(), spad);
        info.addSpacer(4f);

        info.addPara("Wardroom Tactical Debriefs (Skill Retraining):", Misc.getHighlightColor(), spad);
        info.addPara(
            "Once an officer reaches Trusted Comrade status (50% Camaraderie), commanders can hold a private Wardroom Tactical Debrief over hot dinner plates. By reviewing telemetry logs, simulator records, and flight habits, the officer can unlearn an unwanted combat skill to refund 1 skill point, allowing immediate reallocation in the fleet officer screen.", Misc.getTextColor(), spad);
        bullet(info);
        addBullet(info, "Debriefing Cost: 1 Story Point (100% bonus XP returned) and 100,000 credits to cover private catering, flight recorder collation, and tactical telemetry data.", spad, Misc.getHighlightColor(), "1 Story Point", "100,000 credits");
        addBullet(info, "Elite Skill Refund: If the unlearned skill was Elite, the Story Point previously spent to elevate it is fully refunded.", spad, Misc.getPositiveHighlightColor(), "Story Point previously spent to elevate it is fully refunded");
        addBullet(info, "Camaraderie Strain: Retraining challenges an officer's tactical habits, temporarily reducing Camaraderie by -10% to -20% depending on personality.", spad, Misc.getNegativeHighlightColor(), "-10% to -20%");
        unindent(info);
        info.addSpacer(4f);

        info.addPara("Loyal Confidant Campaign Doctrines (75% Camaraderie):", Misc.getHighlightColor(), spad);
        info.addPara("Officers who achieve Loyal Confidant status unlock persistent fleetwide campaign doctrines based on their personality:", Misc.getTextColor(), spad);
        bullet(info);
        addBullet(info, "Steady - Disciplined Watchstanding: +5% Daily CR Recovery Rate, -15% Solar Storm / Hazard Damage", spad, Misc.getPositiveHighlightColor(), "Steady", "Disciplined Watchstanding");
        addBullet(info, "Timid - Vigilant Early Warning: +10% Fleet Sensor Range, -5% Fleet Sensor Profile", spad, Misc.getPositiveHighlightColor(), "Timid", "Vigilant Early Warning");
        addBullet(info, "Cautious - Preventive Fleet Maintenance: -5% Fleet Supply Upkeep, +10% Ship Repair Rate", spad, Misc.getPositiveHighlightColor(), "Cautious", "Preventive Fleet Maintenance");
        addBullet(info, "Aggressive - Relentless Transit & Salvage: +1 Sustained Fleet Burn, +10% Post-Battle Salvage", spad, Misc.getPositiveHighlightColor(), "Aggressive", "Relentless Transit & Salvage");
        addBullet(info, "Reckless - Audacious Reactor Tuning: -10% Hyper Fuel Consumption, -15% Emergency Burn & Jump CR Cost", spad, Misc.getPositiveHighlightColor(), "Reckless", "Audacious Reactor Tuning");
        unindent(info);
        info.addSpacer(6f);

        // Live Fleet Officer Status Roster
        info.addPara("Current Fleet Officer Roster & Mentoring Status:", Misc.getHighlightColor(), spad);
        List<PersonAPI> roster = OS_PickRandomOfficer.getHumanOfficers();
        if (roster.isEmpty()) {
            info.addPara("No human bridge officers currently assigned to the fleet. Recruit officers to unlock wardroom mentoring.", Misc.getGrayColor(), spad);
        } else {
            bullet(info);
            for (PersonAPI officer : roster) {
                int fs = OS_OfficerFriendship.getFriendship(officer);
                String tier = OS_OfficerFriendship.getTierName(fs);
                boolean canRetrain = OS_OfficerFriendship.canRetrain(officer);
                boolean isLoyal = OS_OfficerFriendship.isLoyal(officer);
                String personality = officer.getPersonalityAPI() != null ? officer.getPersonalityAPI().getDisplayName() : "Steady";

                String statusNote;
                Color statusColor;
                if (isLoyal) {
                    statusNote = "Loyal Confidant (Campaign Doctrine Active: " + OS_OfficerFriendship.getLoyalPerkName(officer) + ")";
                    statusColor = Misc.getPositiveHighlightColor();
                } else if (canRetrain) {
                    statusNote = "Trusted Comrade (Eligible for Wardroom Tactical Debrief)";
                    statusColor = Misc.getHighlightColor();
                } else {
                    statusNote = "Building Trust (" + fs + "% / 50% needed for debriefing)";
                    statusColor = Misc.getTextColor();
                }

                addBullet(info, officer.getNameString() + " (Lvl " + officer.getStats().getLevel() + ", " + personality + ", " + fs + "% Camaraderie): " + statusNote, spad,
                    statusColor, officer.getNameString(), statusNote);
            }
            unindent(info);
        }
        info.addSpacer(8f);

        // Section C: Commissary Regulations & Port Facilities
        info.addSectionHeading("Section C: Commissary Regulations & Port Facilities", Alignment.LMID, opad);

        info.addPara("Spaceport Infrastructure Requirements (Market Size 4+):", Misc.getHighlightColor(), spad);
        info.addPara(
            "'The Orbiting Spoon' culinary fabricators are massive, jury-rigged Domain-era relics requiring high-voltage industrial reactor taps and dedicated coolant lines. Consequently, they are exclusively installed in portside bars of developed stations and colonies with Market Size 4 or larger belonging to recognized Core factions (Hegemony, Tri-Tachyon, Sindrian Diktat, Luddic Church, Luddic Path, Persean League, Pirates, Independents, and Player-founded colonies). Frontier outposts, hidden caches, and small settlements cannot supply the necessary power to keep them operational.", Misc.getTextColor(), spad);
        info.addSpacer(4f);

        info.addPara("Standardized Commissary Tariffs (Flat 50 to 600 Credits):", Misc.getHighlightColor(), spad);
        info.addPara(
            "To ensure all spacers and commanders can afford hot food regardless of fleet tonnage, diner meals are covered by port authority commissary subsidies. Prices are flat and standardized between 50 and 600 credits (averaging 200 credits), without punishing per-crew scaling multipliers:", Misc.getTextColor(), spad);
        bullet(info);
        addBullet(info, "Luddic Path: Ascetic Penance Rations (50 credits)", spad, Misc.getHighlightColor(), "50 credits");
        addBullet(info, "Pirates: Fringe Scavenger Platter (100 credits)", spad, Misc.getHighlightColor(), "100 credits");
        addBullet(info, "Hegemony: Commissary Auxiliary Rations (150 credits)", spad, Misc.getHighlightColor(), "150 credits");
        addBullet(info, "Luddic Church: Pilgrim's Hearth Harvest Feast (200 credits)", spad, Misc.getHighlightColor(), "200 credits");
        addBullet(info, "Independents / Player: Loaded Spacer's Full-Burn Set (200 credits)", spad, Misc.getHighlightColor(), "200 credits");
        addBullet(info, "Persean League: Archon's Grand Mezze Banquet (350 credits)", spad, Misc.getHighlightColor(), "350 credits");
        addBullet(info, "Tri-Tachyon: Executive Synth-Steak Suite (450 credits)", spad, Misc.getHighlightColor(), "450 credits");
        addBullet(info, "Sindrian Diktat: Supreme Executor's Volturnian Feast (600 credits)", spad, Misc.getHighlightColor(), "600 credits");
        unindent(info);
        info.addSpacer(4f);

        info.addPara("Cargo Hold Banquets & Humanitarian Relief:", Misc.getHighlightColor(), spad);
        bullet(info);
        addBullet(info, "Volturnian Blue-Shell Lobster (1 unit): Sponsoring an azure lobster banquet directly from cargo extends Shore Leave to 21 days with +15% to +25% Camaraderie.", spad, Misc.getPositiveHighlightColor(), "1 unit", "21 days", "+15% to +25%");
        addBullet(info, "Pre-Collapse Luxury Goods (1 unit): Unsealing vintage luxury wines and preserved meats grants 21 days of Shore Leave and +12% to +20% Camaraderie.", spad, Misc.getPositiveHighlightColor(), "1 unit", "21 days", "+12% to +20%");
        addBullet(info, "Communal Famine Relief Stew (10 units Food): During port food shortages, donating bulk grain brews hot gratitude stew for starving dockers (+5 Faction Rep, 14 days Shore Leave).", spad, Misc.getPositiveHighlightColor(), "10 units Food", "+5 Faction Rep", "14 days Shore Leave");
        unindent(info);
        info.addSpacer(10f);
        info.setTextWidthOverride(0f);
        info.setBulletedListMode(null);
    }

    @Deprecated
    protected void renderRegulationsTab(TooltipMakerAPI info, float width, float opad, float spad) {
        renderMoraleAndFieldGuideTab(info, width, opad, spad);
    }

    public static String getStationOfOrigin(String factionId) {
        if (Global.getSector() != null) {
            MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
            if (mem != null && mem.contains("$os_shore_leave_station")) {
                String s = mem.getString("$os_shore_leave_station");
                if (s != null && !s.isEmpty()) return s;
            }
        }
        String f = factionId != null ? factionId.toLowerCase().trim() : "independent";
        switch (f) {
            case "hegemony": return "Hegemony Naval Spaceport Commissary";
            case "tritachyon": case "tri_tachyon": return "Tri-Tachyon Corporate Concourse";
            case "sindrian_diktat": case "diktat": case "sindrian": return "Cruor / Volturn Spaceport Bar";
            case "luddic_church": case "church": return "Gilead Refectory / Church Port";
            case "luddic_path": case "path": return "Fringe Concealed Station Berth";
            case "pirates": case "pirate": return "Pirate Asteroid Concourse";
            case "persean": case "persean_league": case "league": return "Persean League High Port";
            case "lobster_feast": return "Volturn Deep-Sea Fisheries (Private Reserve)";
            case "luxury_feast": return "Pre-Collapse Core Vaults (Command Reserve)";
            case "famine_stew": return "Portside Cantina Relief Cauldron";
            default: return "Independent Core Spaceport Concourse";
        }
    }

    public static Color getFactionColorSafe(String factionId) {
        if (Global.getSector() != null && factionId != null) {
            FactionAPI f = Global.getSector().getFaction(factionId);
            if (f != null && f.getBaseUIColor() != null) return f.getBaseUIColor();
        }
        return Misc.getTextColor();
    }

    public static Color getFactionColorSafe(MarketAPI market) {
        if (market != null && market.getFaction() != null && market.getFaction().getBaseUIColor() != null) {
            return market.getFaction().getBaseUIColor();
        }
        return Misc.getTextColor();
    }

    @Override
    public String getSortString() {
        return "The Orbiting Spoon: Spacer's Registry & Field Guide";
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "fleet_log");
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = new HashSet<>();
        tags.add(Tags.INTEL_FLEET_LOG);
        return tags;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        MarketAPI closest = null;
        float minDist = Float.MAX_VALUE;
        SectorEntityToken player = Global.getSector().getPlayerFleet();

        if (player == null || player.getLocationInHyperspace() == null) return null;

        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (m.getSize() >= 4 && !m.isHidden() && m.getPrimaryEntity() != null && m.getPrimaryEntity().getLocationInHyperspace() != null) {
                if (!OS_IsVanillaFaction.isVanilla(m.getFactionId())) continue;

                float dist = Misc.getDistance(player.getLocationInHyperspace(), m.getPrimaryEntity().getLocationInHyperspace());
                if (dist < minDist) {
                    minDist = dist;
                    closest = m;
                }
            }
        }

        if (closest != null) {
            return closest.getPrimaryEntity();
        }
        return null;
    }

    @Override
    public IntelSortTier getSortTier() {
        return IntelSortTier.TIER_0;
    }

    public static String getSignatureDish(String factionId) {
        if ("hegemony".equals(factionId)) return "Commissary Auxiliary Rations";
        if ("tritachyon".equals(factionId) || "tri_tachyon".equals(factionId)) return "Executive Synth-Steak Suite";
        if ("sindrian_diktat".equals(factionId) || "sindrian".equals(factionId) || "diktat".equals(factionId)) return "Volturnian Lobster Feast";
        if ("luddic_church".equals(factionId) || "church".equals(factionId)) return "Pilgrim's Hearth Harvest Feast";
        if ("luddic_path".equals(factionId) || "path".equals(factionId)) return "Ascetic Penance Broth";
        if ("pirates".equals(factionId) || "pirate".equals(factionId)) return "Fringe Scavenger Platter";
        if ("persean".equals(factionId) || "persean_league".equals(factionId) || "league".equals(factionId)) return "Archon's Grand Mezze Banquet";
        return "Loaded Spacer's Full-Burn Diner Set";
    }

    public static int getDishPrice(String factionId) {
        if ("hegemony".equals(factionId)) return 150;
        if ("tritachyon".equals(factionId) || "tri_tachyon".equals(factionId)) return 450;
        if ("sindrian_diktat".equals(factionId) || "sindrian".equals(factionId) || "diktat".equals(factionId)) return 600;
        if ("luddic_church".equals(factionId) || "church".equals(factionId)) return 200;
        if ("luddic_path".equals(factionId) || "path".equals(factionId)) return 50;
        if ("pirates".equals(factionId) || "pirate".equals(factionId)) return 100;
        if ("persean".equals(factionId) || "persean_league".equals(factionId) || "league".equals(factionId)) return 350;
        return 200;
    }

    public static String getShortFactionName(String factionId, String defaultName) {
        if ("sindrian_diktat".equals(factionId) || "sindrian".equals(factionId) || "diktat".equals(factionId)) return "Diktat";
        if ("luddic_church".equals(factionId) || "church".equals(factionId)) return "Church";
        if ("luddic_path".equals(factionId) || "path".equals(factionId)) return "Path";
        if ("persean".equals(factionId) || "persean_league".equals(factionId) || "league".equals(factionId)) return "League";
        return defaultName;
    }

    public static class SpoonDinerEntry implements Comparable<SpoonDinerEntry> {
        public MarketAPI market;
        public String dishName;
        public int price;
        public float distLY;
        public boolean inSystem;

        @Override
        public int compareTo(SpoonDinerEntry o) {
            return Float.compare(this.distLY, o.distLY);
        }
    }

    private void addBullet(TooltipMakerAPI info, String text, float pad, Color highlightColor, String... highlights) {
        info.setTextWidthOverride(0f);
        info.setParaInsigniaLarge();
        LabelAPI label = info.addPara(text, pad);
        if (highlights != null && highlights.length > 0) {
            label.setHighlight(highlights);
            if (highlightColor != null) {
                label.setHighlightColor(highlightColor);
            }
        }
    }
}

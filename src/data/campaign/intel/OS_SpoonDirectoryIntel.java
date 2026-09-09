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
            if (m == null) return;
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
        if (buttonId instanceof String[] && ((String[])buttonId)[0].equals("full_review")) return false;
        if (TAB_REGISTRY.equals(buttonId) || TAB_REVIEWS.equals(buttonId) || TAB_REGULATIONS.equals(buttonId) || TAB_MORALE.equals(buttonId)) {
            return false;
        }
        return super.doesButtonHaveConfirmDialog(buttonId);
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (buttonId instanceof String[] && ((String[])buttonId)[0].equals("full_review")) {
            String[] arr = (String[]) buttonId;
            String heading = arr[1];
            String fullReview = arr[2];
            String rating = arr.length > 3 ? arr[3] : null;
            String proTip = arr.length > 4 ? arr[4] : null;
            ui.showDialog(Global.getSector().getPlayerFleet(), new OS_FullReviewDialogPlugin(heading, fullReview, rating, proTip));
            return;
        }
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
        /**
     * TAB 2: Galley Confidential across the Persean Sector
     * Field notes by Anton 'Bones' Burndain (Chief Mess Attendant, Hegemony Auxiliaries, Dishonorably Discharged)
     * Book: "Sectors Unknown: Galley Confidential across the Persean Void"
     */
    protected void renderReviewsTab(TooltipMakerAPI info, float width, float opad, float spad) {
        info.setParaInsigniaLarge();
        info.addSectionHeading("Sectors Unknown: Galley Confidential across the Persean Void", Alignment.MID, opad);

        info.addPara(
            "\"The Persean Sector is a beautiful, filthy, irradiated mess, crawling with frauds, warlords, and self-appointed 'culinary commentators' who haven't stepped outside a Core World orbital lounge in twenty cycles. Chief among these hacks is my insufferable former mess-mate, Gordon 'Transverse' Stumps. Back in Cycle 204 at Eos Exodus, I had my hand on the manual airlock override, fully prepared to vent Stumps into the cold, uncaring void mid-soliloquy over 'sous-vide squab.' I only aborted because the chief engineer swore that venting Stumps's enormous ego would permanently clog the atmospheric intake scrubbers. This dispatch isn't for Stumps and his silk-robed sycophants. This is for the captains, the exhaust-huffing engineers, and the grease-monkeys living in pressurized iron buckets hurtling through a cosmic graveyard. Eat dirty, drink deep, tip your mess attendants, and never, ever order the refrigerated seafood when the station's reactor tap is flashing amber.\"",
            Misc.getTextColor(),
            opad
        );
        LabelAPI authLabel = info.addPara("- Anton 'Bones' Burndain (Chief Mess Attendant, Hegemony Auxiliaries, Dishonorably Discharged)", Misc.getGrayColor(), spad);
        authLabel.setHighlight("Anton 'Bones' Burndain");
        authLabel.setHighlightColors(Misc.getHighlightColor());
        info.addSpacer(4f);

        // 1. Sindrian Diktat
        addReviewCard(info, opad, spad,
            "[No Transponders: Cruorian Reeds] Sindrian Diktat: Volturnian Lobster Feast (200 credits)",
            getFactionColorSafe("sindrian_diktat"),
            "Sweet, tender blue-shell lobster poached in fiery clarified pepper butter. Every bite is seasoned with the sweat of Askonia's dockworkers and the paranoia of an authoritarian petrol-state. Pure decadence.",
            "Sweet, tender blue-shell lobster poached in volcanic clarified pepper butter, served with an Askonia root mash that tastes like it was fertilized with antimatter fuel runoff. Every bite is seasoned with the sweat of overworked dockhands, the relentless hum of atmospheric fuel refineries, and the suffocating paranoia of an authoritarian petrol-state where muttering 'this tail is slightly rubbery' gets you reassigned to a heavy-water mining detail.\n\nThe self-appointed gastronomes at the Askonia Ministry of Cultural Rectitude—led by none other than Gordon 'Transverse' Stumps on a paid junket—awarded this dish 'Six Golden Lion Laurels for Ideological Purity.' What a staggering load of synthetic tripe. I once tried to sabotage Stumps's tasting session by replacing his dipping butter with Grade-B maneuvering thruster lubricant. The idiot didn't even notice; he smacked his lips on holo-feed and praised its 'daring, petroleum-forward finish.' You cannot defeat a man whose palate has been replaced with asbestos.\n\nIs it decadent? Absolutely. Is it bathed in blood and heavy fuel oil? Unquestionably. Eat like an absolute tyrant while you can, because when the fuel tanks inevitably blow, nobody is going to remember the parsley garnish.",
            "Rating: 4.5 / 5 Spoons - \"Eat like a tyrant before the fuel tanks blow.\"",
            "Bones's Field Rule: \"Never eat shellfish when the guards look twitchy.\""
        );

        // 2. Luddic Church
        addReviewCard(info, opad, spad,
            "[Sectors Unknown: Gilead's Breadbasket] Luddic Church: Pilgrim's Hearth Harvest Feast (200 credits)",
            getFactionColorSafe("luddic_church"),
            "Real stone-ground barley bread and hand-churned butter from Gilead. No chemical synthesizers or corporate nonsense. Taking a bite of warm crumb that grew in honest soil feels holy.",
            "Real stone-ground barley bread, salted hand-churned butter from Gilead's river valleys, and a thick earthenware pot of root vegetable stew simmered over crackling charcoal. No chemical synthesizers, no corporate branding, no bio-engineered enzyme stabilizers. Taking a bite of dense, steaming crumb that grew out of honest, sun-drenched planetary dirt feels less like eating lunch and more like receiving a personal apology from the Creator for the invention of hyperspace.\n\nMeanwhile, Gordon Stumps and his pampered gaggle of League food columnists dismiss this as 'primitive agrarian peasant gruel lacking modern aromatic complexity.' Those delicate Core World dilettantes can go choke on their nitrogen-foamed emulsion gels. When your entire career is spent breathing recycled flatulence and scorched capacitor ozone in a steel corridor, real soil-grown food will bring tears to a hardened gunner's eyes. Stumps wouldn't recognize true spiritual beauty unless it came wrapped in a five-thousand-credit corporate sponsorship ribbon.\n\nChew slowly, keep your cynical mouth shut, and leave a generous tithe for the deacon who baked it.",
            "Rating: 5.0 / 5 Spoons - \"Church for your stomach. Amen.\"",
            "Bones's Field Rule: \"Chew slow, shut your mouth, and leave a modest tithe.\""
        );

        // 3. Persean League
        addReviewCard(info, opad, spad,
            "[A Cook's Burn: Kazeron's Terraces] Persean League: Archon's Grand Mezze Banquet (200 credits)",
            getFactionColorSafe("persean"),
            "Rosemary-rubbed waterfowl skewers and chilled citrus liqueur. Meticulously refined to stroke merchant egos, but the citrus cuts through engine grease like nothing else in the Core.",
            "Rosemary-rubbed waterfowl skewers, chilled mountain citrus liqueur, and wood-fired flatbread blistered to crisp perfection. It is meticulously, relentlessly refined—obviously engineered from top to bottom to stroke the fragile egos of bloated merchant princes, oligarchs, and hereditary Archons who think owning three orbital gantries makes them Domain royalty.\n\nStumps was awarded an honorary silver banquet medal on Kazeron after penning a three-thousand-word master's thesis on the 'metaphorical cadence of the wild oregano.' I was so disgusted that during our EVA transfer across the orbital spire, I used a sharpened oyster shucker to poke a micro-puncture into Stumps's environmental suit. I prayed the slow hiss of venting oxygen would finally humble him. To my eternal fury, the man's neck was so insulated with layers of catered foie gras and self-regard that the suit's emergency gel sealed the breach instantly. He thought it was 'a refreshing mountain draft.'\n\nSip the vintage, laugh politely at their atrocious jokes, and always double-check the docking tariff invoice before you pay.",
            "Rating: 4.0 / 5 Spoons - \"Good diplomacy on an empty stomach.\"",
            "Bones's Field Rule: \"Sip the wine, laugh at terrible jokes, and check docking tariffs.\""
        );

        // 4. Independents
        addReviewCard(info, opad, spad,
            "[Galley Confidential: The 24-Hour Airlock] Independents: Loaded Spacer's Full-Burn Set (200 credits)",
            getFactionColorSafe("independent"),
            "The eternal backbone of the void. Thick root-vegetable beef stew, a stack of hot waffles slathered in butter, and scalded coffee. Honest, greasy, and carries you through three hyperspace burns.",
            "The eternal, grease-slicked backbone of the entire Persean Sector. A steaming bowl of root-vegetable beef stew that clings to your ribs, a stack of golden waffles drowned in artificial maple syrup, and a chipped mug of black coffee hot and acidic enough to etch hull armor. It is honest, it is violently unpretentious, and it packs enough pure caloric horsepower to drag an exhausted watchstander through three back-to-back storm jumps without blinking.\n\nStumps once published a syndicated column calling Independent diners 'unhygienic bio-hazard troughs for the uncultured.' I spent three weeks planning to swap his cabin's emergency oxygen bottle with pressurized wastewater vapor, but my quartermaster talked me down because wasting wastewater is a court-martial offense. Stumps wouldn't last four minutes in a leaky frigate engine room during a coolant rupture, let alone appreciate the structural integrity of real diner grease.\n\nIf the vinyl booth doesn't have at least two patched knife slashes and the stool doesn't wobble, walk right back out—you're in a tourist trap.",
            "Rating: 4.0 / 5 Spoons - \"Old reliable. The grease holds the void together.\"",
            "Bones's Field Rule: \"If the counter stool doesn't wobble, you're on the wrong station.\""
        );

        // 5. Tri-Tachyon
        addReviewCard(info, opad, spad,
            "[Sectors Unknown: Port Tse Corporate Lounge] Tri-Tachyon: Executive Synth-Steak Suite (200 credits)",
            getFactionColorSafe("tritachyon"),
            "A terrifying miracle of bio-molecular synthesis. Laser-seared and mathematically perfect, it tastes like quarterly profit margins and is paired with nootropics. Clean, expensive, and devoid of humanity.",
            "A terrifying, clinical triumph of bio-molecular synthesizer science. Laser-seared to a mathematically flawless medium-rare, perfectly marbling synthetic lipids with lab-grown myofibrils. It tastes of quarterly earnings calls, boardroom sterility, non-disclosure agreements, and the faint electric tingle of cognitive-enhancing nootropic side-dishes designed to keep middle managers awake for forty-eight-hour fiscal audits.\n\nGordon Stumps was paid fifty thousand credits by Tri-Tachyon PR to host the keynote product launch for this steak. Out of sheer malice, I hacked the demo unit's matter compiler to print Stumps a slice of vulcanized EVA boot leather infused with MSG and grill marks. The bastard chewed through it on live tri-cast without missing a beat, declaring it had 'a bold, muscular, uncompromising mouthfeel' and giving it a standing ovation. The man is immune to shame, poison, and basic culinary dignity.\n\nEat it if your corporation is footing the expense account, enjoy the eerie hyper-focus from the stimulants, and for the love of the Domain, never ask which bio-reactor vat the protein culture was harvested from.",
            "Rating: 3.5 / 5 Spoons - \"Don't ask what culture vat it came from.\"",
            "Bones's Field Rule: \"If food is 'biochemically optimized', do not sign the receipt.\""
        );

        // 6. Hegemony
        addReviewCard(info, opad, spad,
            "[The Nasty Chits: Chicomoztoc Gantry Mess] Hegemony: Commissary Auxiliary Rations (200 credits)",
            getFactionColorSafe("hegemony"),
            "Dense hardtack that could plug a hull breach, and boiling iron-soy sludge. No garlic, no pepper, no joy. Tastes like grey primer and obedience. Eat before your twelve-hour shift.",
            "Dense composite hardtack with the tensile strength to patch a micrometeorite hull breach, accompanied by a steaming bowl of grey iron-soy slurry. Zero garlic, zero pepper, zero joy. It tastes of industrial primer paint, emergency bulkhead sealant, martial law, and unthinking obedience to the naval chain of command. You eat it standing up at a stainless steel trough in under six minutes before your next twelve-hour gantry shift begins.\n\nWhen we were both ensigns in the Auxiliary Mess Corps, Stumps gave a brown-nosing speech to the Fleet Logistics Board claiming this grey paste 'instills moral fiber and patriotic stoicism.' I loosened the quick-release pin on his mess-bench, aiming to dump three liters of boiling soy mash directly into his lap. Naturally, a passing patrol commander bumped the table first, caught the tray, and praised Stumps for 'alert readiness under mess-hall turbulence.' I got assigned two weeks of latrine duty. Stumps got promoted.\n\nDon't complain to the mess attendant. The cook is usually a retired chief master-at-arms whose best friend runs the station brig.",
            "Rating: 2.5 / 5 Spoons - \"Eat it standing up. Tastes like martial law.\"",
            "Bones's Field Rule: \"Never complain. The cook has friends in the military police.\""
        );

        // 7. Pirates
        addReviewCard(info, opad, spad,
            "[No Transponders: Donkers Salvage Yards] Pirates: Fringe Scavenger Platter (200 credits)",
            getFactionColorSafe("pirates"),
            "Mystery ribs charred over open engine manifolds, drowned in peppery fungal sludge to cover the rot. You will experience either animal euphoria or catastrophic gastrointestinal failure. Roll the dice.",
            "Unidentified ribs charred black over the open exhaust manifold of a jury-rigged destroyer engine, drenched in blistering, peppery fungal mash specifically engineered to mask whatever creeping biological decay is occurring beneath the crust. Downed with a tin cup of moonshine distilled from de-icing fluid and fermented hydroponic waste. You will experience either a surge of raw, feral adrenaline or violent, catastrophic gastrointestinal rebellion within the hour. It is a fifty-fifty coin flip—much like trusting a pirate commodore with your transponder codes.\n\nI once deliberately fed Stumps false coordinates to Donkers Salvage, telling him it was an 'exclusive underground artisanal pop-up.' I genuinely hoped the scrapper cook would throw him into the rendering vats. Instead, the pirates stole his boots, emptied his cred-stick, forced him to wash dishes for a week, and sent him back on a freighter. The insufferable fraud returned to Chicomoztoc and wrote an award-winning article about 'immersive, authentic fringe street theater.' You cannot kill this man.\n\nKeep your sidearm holster unbuttoned, never ask what species the meat used to belong to, and never look directly into the brew kettle.",
            "Rating: 2.0 / 5 Spoons - \"Hazard pay required. I loved every filthy bite.\"",
            "Bones's Field Rule: \"Keep your sidearm unclasped, and never ask what animal it was.\""
        );

        // 8. Luddic Path
        addReviewCard(info, opad, spad,
            "[Sectors Unknown: Cell Bunker Galley] Luddic Path: Ascetic Penance Broth (200 credits)",
            getFactionColorSafe("luddic_path"),
            "Tastes like crushed gravel simmered in reactor runoff. Cell fighters hammered off the seasoning injectors because pleasure is a sin. Buy only if facing active starvation.",
            "Tastes like crushed silicate gravel simmered in irradiated coolant runoff, paired with rock-hard unfermented root cakes that require a combat knife to fracture. The cell's kitchen zealots deliberately took a welding torch to the flavor injectors because experiencing physical enjoyment during mortal existence is classified as heresy against Ludd's divine balance. It is bleak, abrasive, and tastes distinctly of impending explosive martyrdom.\n\nGordon Stumps wouldn't come within three star-systems of a Pather mess hall, and for once, his cowardice serves him well. Walking into an active cell bunker with a notepad and asking for the chef's tasting menu is an express ticket to being welded inside a torpedo tube. I am the only idiot in the Sector who ate this twice—once out of curiosity, and once because I was hiding from Stumps's lawyers after the boot-leather incident on Port Tse.\n\nConsume only if you are experiencing Stage 4 active starvation, or if you feel an urgent psychological need to punish yourself for your worldly sins. And whatever you do, do not ask the server for table salt—it will trigger an immediate theological tribunal.",
            "Rating: 0.5 / 5 Spoons - \"Penance, indeed. May the Prophet forgive the cook.\"",
            "Bones's Field Rule: \"Do not ask for salt. It will start a theological tribunal.\""
        );

        info.addSpacer(10f);
    }

    protected void addReviewCard(TooltipMakerAPI info, float opad, float spad, String heading, Color factionColor, String shortReview, String fullReview, String rating, String proTip) {
        info.addSectionHeading(heading, Alignment.LMID, opad);
        info.setParaInsigniaLarge();
        info.addPara(shortReview, Misc.getTextColor(), spad);
        
        TooltipMakerAPI btnInfo = info.beginSubTooltip(140f);
        btnInfo.setButtonFontVictor14();
        btnInfo.addButton("Read Full Review", new String[]{"full_review", heading, fullReview, rating, proTip}, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), 140f, 20f, 0f);
        info.endSubTooltip();
        info.addCustom(btnInfo, spad);
        
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
            "Sponsoring a hot meal spread awards Officer XP and grants Fleet Bonus XP. Dining builds Camaraderie, advancing relationships to Trusted Comrade (50%) and Loyal Confidant (75%).", Misc.getTextColor(), spad);
        info.addSpacer(4f);

        info.addPara("Wardroom Tactical Debriefs (Skill Retraining):", Misc.getHighlightColor(), spad);
        info.addPara(
            "At Trusted Comrade status (50% Camaraderie), officers can unlearn a combat skill to refund 1 skill point.", Misc.getTextColor(), spad);
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
            "Fabricators require high-voltage industrial reactor taps. They are exclusively installed in portside bars of developed Core faction colonies (Market Size 4+).", Misc.getTextColor(), spad);
        info.addSpacer(4f);

        info.addPara("Standardized Commissary Tariffs (Flat 200 Credits):", Misc.getHighlightColor(), spad);
        info.addPara(
            "Diner meals are subsidized, avoiding per-crew scaling multipliers. Prices are flat based on the local faction:", Misc.getTextColor(), spad);
        bullet(info);
        addBullet(info, "Luddic Path: Ascetic Penance Rations (200 credits)", spad, Misc.getHighlightColor(), "50 credits");
        addBullet(info, "Pirates: Fringe Scavenger Platter (200 credits)", spad, Misc.getHighlightColor(), "100 credits");
        addBullet(info, "Hegemony: Commissary Auxiliary Rations (200 credits)", spad, Misc.getHighlightColor(), "150 credits");
        addBullet(info, "Luddic Church: Pilgrim's Hearth Harvest Feast (200 credits)", spad, Misc.getHighlightColor(), "200 credits");
        addBullet(info, "Independents / Player: Loaded Spacer's Full-Burn Set (200 credits)", spad, Misc.getHighlightColor(), "200 credits");
        addBullet(info, "Persean League: Archon's Grand Mezze Banquet (200 credits)", spad, Misc.getHighlightColor(), "350 credits");
        addBullet(info, "Tri-Tachyon: Executive Synth-Steak Suite (200 credits)", spad, Misc.getHighlightColor(), "450 credits");
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

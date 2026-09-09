package data.campaign.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.campaign.OS_OfficerFriendship;
import data.campaign.buffs.OS_ShoreLeaveBuff;
import data.campaign.rulecmd.OS_OfficerMentoring;
import data.campaign.rulecmd.OS_PickRandomOfficer;

/**
 * Native graphical custom dialog overlay for browsing and selecting food courses
 * at "The Orbiting Spoon". Supports regional cuisines, cargo hold banquets,
 * famine relief stews, officer palate analysis, and live fleet morale perks.
 */
public class OS_CustomFoodDialogDelegate extends BaseCustomDialogDelegate {

    public static class MealOption {
        public String id;
        public String buffKey;
        public String title;
        public String category;
        public String costString;
        public int creditCost;
        public String commodityId;
        public int commodityCost;
        public String iconSprite;
        public String description;
        public String perkSummary;
        public String doctrineImpact;
        public String officerOpinion;
        public boolean canAfford;
        public String unaffordableReason;
        public float durationDays;
        public boolean isEmergencyRelief;
    }

    private final InteractionDialogAPI dialog;
    private final Map<String, MemoryAPI> memoryMap;
    private CustomDialogDelegate.CustomDialogCallback callback;
    private final Map<String, MealOption> mealOptions = new LinkedHashMap<>();
    private final Map<String, ButtonAPI> radioButtons = new LinkedHashMap<>();
    private String selectedMealId = null;
    private boolean orderExecuted = false;

    private PersonAPI officer;
    private String factionId;
    private boolean isWardroom;

    public OS_CustomFoodDialogDelegate(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        this.dialog = dialog;
        this.memoryMap = memoryMap;

        this.officer = OS_OfficerMentoring.getActiveOfficer(memoryMap);
        if (this.officer == null) {
            this.officer = OS_PickRandomOfficer.getActiveOfficerFromMemory(memoryMap);
        }
        this.factionId = OS_PickRandomOfficer.getFactionId(dialog, memoryMap);
        this.isWardroom = (this.officer != null);

        initMealOptions();
    }

    private void initMealOptions() {
        mealOptions.clear();

        CampaignFleetAPI playerFleet = Global.getSector() != null ? Global.getSector().getPlayerFleet() : null;
        long playerCredits = (playerFleet != null && playerFleet.getCargo() != null) ? (long) playerFleet.getCargo().getCredits().get() : 0;
        int lobsterInCargo = (playerFleet != null && playerFleet.getCargo() != null) ? (int) playerFleet.getCargo().getCommodityQuantity(Commodities.LOBSTER) : 0;
        int luxuryInCargo = (playerFleet != null && playerFleet.getCargo() != null) ? (int) playerFleet.getCargo().getCommodityQuantity(Commodities.LUXURY_GOODS) : 0;
        int foodInCargo = (playerFleet != null && playerFleet.getCargo() != null) ? (int) playerFleet.getCargo().getCommodityQuantity(Commodities.FOOD) : 0;

        FactionAPI faction = Global.getSector().getFaction(factionId);
        MarketAPI market = dialog.getInteractionTarget() != null ? dialog.getInteractionTarget().getMarket() : null;

        // Check food shortage / deficit
        boolean hasFoodShortage = false;
        if (market != null) {
            if (market.hasCondition(Conditions.EVENT_FOOD_SHORTAGE)) {
                hasFoodShortage = true;
            } else if (market.getCommodityData(Commodities.FOOD) != null && market.getCommodityData(Commodities.FOOD).getDeficitQuantity() > 0) {
                hasFoodShortage = true;
            }
        }
        int scarcityMult = hasFoodShortage ? 2 : 1;

        // Multiplier: Flat 1x for Option 2 (Roleplay Premium)
        int multiplier = 1;

        // 1. Regional Specialty
        List<OS_PickRandomOfficer.MenuItem> items = OS_PickRandomOfficer.getMenuItems(factionId);
        OS_PickRandomOfficer.MenuItem regionalMeal = !items.isEmpty() ? items.get(0) :
                new OS_PickRandomOfficer.MenuItem("orbiting_spoon_order_generic_set", "Loaded Spacer's Full-Burn Diner Set", 18);

        int finalRegionalPrice = regionalMeal.basePrice * multiplier * scarcityMult;

        MealOption regional = new MealOption();
        regional.id = "meal_regional";
        regional.buffKey = factionId;
        regional.title = (isWardroom ? "The Wardroom Cut: " : "Fleet Galley Spread: ") + regionalMeal.name;
        regional.category = "Station Regional Specialty" + (hasFoodShortage ? " [Famine Rate x2]" : "");
        regional.creditCost = finalRegionalPrice;
        regional.costString = String.format("%,d credits", finalRegionalPrice);

        String crest = (faction != null) ? faction.getCrest() : null;
        regional.iconSprite = (crest != null && !crest.isEmpty()) ? crest : "graphics/factions/crest_independent.png";

        regional.description = getRegionalDescription(factionId, isWardroom);
        regional.perkSummary = "-5% Supply Upkeep, +5% Max CR & +10% CR Recovery (Combat Ships), +10% Sensor Profile, -5% Acceleration";
        regional.doctrineImpact = OS_ShoreLeaveBuff.getMealPerkSummary(factionId);
        regional.durationDays = "independent".equalsIgnoreCase(factionId) ? OS_ShoreLeaveBuff.INDIE_DURATION : OS_ShoreLeaveBuff.DEFAULT_DURATION;

        if (officer != null) {
            regional.officerOpinion = officer.getNameString() + "'s Palate: " + OS_OfficerFriendship.getOfficerFoodPreferenceHint(officer, factionId);
        } else {
            regional.officerOpinion = "Bulk galley preparation ladled in pressure cauldrons for off-duty watchstanders.";
        }

        regional.canAfford = (playerCredits >= finalRegionalPrice);
        regional.unaffordableReason = regional.canAfford ? null :
                "Insufficient credits (Requires " + String.format("%,d", finalRegionalPrice) + " credits; fleet purser has " + String.format("%,d", playerCredits) + ")";

        mealOptions.put(regional.id, regional);

        // 2. Volturnian Lobster Feast
        MealOption lobster = new MealOption();
        lobster.id = "meal_lobster";
        lobster.buffKey = "lobster_feast";
        lobster.title = "Volturnian Blue-Shell Lobster Feast";
        lobster.category = "Cargo Hold Delicacy";
        lobster.commodityId = Commodities.LOBSTER;
        lobster.commodityCost = 1;
        lobster.costString = "1x Lobster (Holds: " + lobsterInCargo + ")";

        CommoditySpecAPI lobSpec = Global.getSettings().getCommoditySpec(Commodities.LOBSTER);
        lobster.iconSprite = (lobSpec != null) ? lobSpec.getIconName() : "graphics/commodities/lobster.png";

        lobster.description = "Live azure-shelled lobster retrieved from cargo cryo-preservation, butter-poached with crushed garlic, coastal sea salt, and wild fennel. The gold standard of high-frontier culinary prestige.";
        lobster.perkSummary = "-5% Supply Upkeep, +5% Max CR & +10% CR Recovery (Combat Ships), +10% Sensor Profile, -5% Acceleration";
        lobster.doctrineImpact = "Persean High-Living: Extends Shore Leave duration to a full 3 weeks (21 days) with massive wardroom bonding.";
        lobster.durationDays = 21f;

        if (officer != null) {
            lobster.officerOpinion = officer.getNameString() + "'s Palate: Universally revered (+15% Camaraderie). Highly celebrated by Steady captains and predatory Aggressive vanguard officers.";
        } else {
            lobster.officerOpinion = "An unforgettable luxury banquet that elevates the entire fleet's morale across every deck.";
        }

        lobster.canAfford = (lobsterInCargo >= 1);
        lobster.unaffordableReason = lobster.canAfford ? null : "Requires 1 unit of Volturnian Lobster in fleet cargo holds (Holds: " + lobsterInCargo + ")";
        mealOptions.put(lobster.id, lobster);

        // 3. Domain Luxury Banquet
        MealOption luxury = new MealOption();
        luxury.id = "meal_luxury";
        luxury.buffKey = "luxury_feast";
        luxury.title = "Domain-Era Vintage Luxury Banquet";
        luxury.category = "Cargo Hold Delicacy";
        luxury.commodityId = Commodities.LUXURY_GOODS;
        luxury.commodityCost = 1;
        luxury.costString = "1x Luxury Goods (Holds: " + luxuryInCargo + ")";

        CommoditySpecAPI luxSpec = Global.getSettings().getCommoditySpec(Commodities.LUXURY_GOODS);
        luxury.iconSprite = (luxSpec != null) ? luxSpec.getIconName() : "graphics/commodities/luxury_goods.png";

        luxury.description = "Air-locked canisters of pre-Collapse vintage wines, cured wild game, and vacuum-sealed heritage spices unsealed for the table. An extraordinary reminder of Old Earth splendor before the Gate Collapse.";
        luxury.perkSummary = "-5% Supply Upkeep, +5% Max CR & +10% CR Recovery (Combat Ships), +10% Sensor Profile, -5% Acceleration";
        luxury.doctrineImpact = "Old Earth Splendor: Extends Shore Leave to a full 3 weeks (21 days), rekindling ancient pride and deep wardroom solidarity.";
        luxury.durationDays = 21f;

        if (officer != null) {
            luxury.officerOpinion = officer.getNameString() + "'s Palate: Universally appreciated (+12% Camaraderie). A solemn and refined reminder of what the Sector strives to rebuild.";
        } else {
            luxury.officerOpinion = "A rare taste of pre-Collapse civilization shared among your command crew and senior ratings.";
        }

        luxury.canAfford = (luxuryInCargo >= 1);
        luxury.unaffordableReason = luxury.canAfford ? null : "Requires 1 unit of Luxury Goods in fleet cargo holds (Holds: " + luxuryInCargo + ")";
        mealOptions.put(luxury.id, luxury);

        // 4. Communal Famine Relief Stew (Shown when station has food shortage)
        if (hasFoodShortage) {
            MealOption famine = new MealOption();
            famine.id = "meal_famine";
            famine.buffKey = "famine_stew";
            famine.title = "Communal Famine Relief Stew";
            famine.category = "Emergency Relief [Station Food Crisis]";
            famine.commodityId = Commodities.FOOD;
            famine.commodityCost = 10;
            famine.costString = "10x Bulk Food (Holds: " + foodInCargo + ")";

            CommoditySpecAPI foodSpec = Global.getSettings().getCommoditySpec(Commodities.FOOD);
            famine.iconSprite = (foodSpec != null) ? foodSpec.getIconName() : "graphics/commodities/food.png";

            famine.description = "Roll 10 pallets of staple grain and preserved rations from your hold into the cantina's depleted pantry. Giant pressure cauldrons brew a piping-hot communal stew for starving dockworkers, families, and ratings.";
            famine.perkSummary = "-5% Supply Upkeep, +5% Max CR & +10% CR Recovery (Combat Ships), +10% Sensor Profile, -5% Acceleration (14 Days)";
            famine.doctrineImpact = "Frontier Solidarity: Grants +5 Faction Reputation with " + (market != null ? market.getFaction().getDisplayName() : "the station") + " for humanitarian relief during a food crisis.";
            famine.durationDays = 14f;
            famine.isEmergencyRelief = true;

            if (officer != null) {
                famine.officerOpinion = officer.getNameString() + "'s Palate: Deeply respected (+10% Camaraderie). Even hardened combat commanders honor leadership that protects civilian lives.";
            } else {
                famine.officerOpinion = "The crew and local dockworkers break bread together in solemn, unforgettable gratitude.";
            }

            famine.canAfford = (foodInCargo >= 10);
            famine.unaffordableReason = famine.canAfford ? null : "Requires 10 units of Bulk Food in cargo holds (Holds: " + foodInCargo + ")";
            mealOptions.put(famine.id, famine);
        }

        // Set default selected meal: first affordable option or regional
        boolean isDigesting = OS_ShoreLeaveBuff.isDigesting();
        selectedMealId = "meal_regional";
        if (!isDigesting) {
            for (MealOption opt : mealOptions.values()) {
                if (opt.canAfford) {
                    selectedMealId = opt.id;
                    break;
                }
            }
        }
    }

    private String getRegionalDescription(String factionId, boolean isWardroom) {
        String f = (factionId != null) ? factionId.toLowerCase().trim() : "independent";
        switch (f) {
            case "hegemony":
                return isWardroom ?
                        "A thick bowl of iron-rich soy stew served with dense shipboard biscuits, sharp aged synth-cheese, and steaming black chicory coffee on polished naval tableware." :
                        "Piping hot iron-rich soy stew ladled over dense hardtack biscuits, washed down with scalded chicory coffee engineered to keep watchstanders sharp through a twelve-hour patrol burn.";
            case "tritachyon":
            case "tri_tachyon":
                return isWardroom ?
                        "A matte-black composite tray unseals to reveal laser-seared prime synth-fillet, iridescent amino-gel cubes, and chilled nootropic soda. Mathematically optimized to sharpen neural latency without gastrointestinal drag." :
                        "High-efficiency synth-steaks and electrolyte gels served in sealed contractor pods with chilled nootropic sodas to maintain shift alertness.";
            case "sindrian_diktat":
            case "diktat":
            case "sindrian":
                return isWardroom ?
                        "A polished brass platter of split Volturnian lobster tail glistening in fiery pepper butter, skewers of spiced flatfish, and a chilled carafe of purple reef wine beneath the Lion's banners." :
                        "Fiery pepper-butter flatfish skewers, savory marine flatbread, and carafes of chilled reef wine served under azure display tanks.";
            case "luddic_church":
            case "church":
                return isWardroom ?
                        "A heavy ceramic bowl of roasted fowl and barley pie, stone-ground sourdough with yellow churned butter, and steaming spiced orchard cider. Honest, grounding hearth food." :
                        "Thick sourdough loaves, roasted root vegetables, and hot spiced cider shared along communal wooden trestles away from the hum of machinery.";
            case "luddic_path":
            case "path":
                return isWardroom ?
                        "Dented tin mess kits with boiled wild turnip mash, tough salted mutton, bitter broth, and dark herbal tea. Coarse, unspiced fuel meant only to keep combatants alive and upright." :
                        "Coarse turnip mash and scorched bitter broth ladled straight from iron cauldrons to sustain fighters in the field.";
            case "pirates":
            case "pirate":
                return isWardroom ?
                        "Blackened mystery ribs sizzling beside a peppery bowl of fungal goulash and caustic moonshine that stings the eyes. Hot, heavy, and packed with enough calories to fuel a raid." :
                        "Charred ribs, peppery fungal mash, and harsh moonshine served on greasy sheet-metal trays over clattering dice games.";
            case "persean":
            case "persean_league":
            case "league":
                return isWardroom ?
                        "Rosemary-rubbed lamb skewers, herbed flatbread, saffron waterfowl rice, and chilled Kazeron citrus liqueur served on broad earthenware under breezy awnings." :
                        "Roasted lamb skewers, herbed flatbread, and citrus aperitifs shared in a bustling taverna overlooking the harbor.";
            default:
                return isWardroom ?
                        "Thick root-vegetable beef stew, golden waffles dripping in churned butter, and scalded black coffee in sturdy ceramic mugs. Heavy, honest spacer comfort." :
                        "Piping-hot spacer's stew, griddled waffles, and endless black coffee engineered to fuel a watch through three consecutive hyperspace transitions.";
        }
    }

    private String getMealArrivalNarration(String factionId, boolean isWardroom) {
        String f = (factionId != null) ? factionId.toLowerCase().trim() : "independent";
        switch (f) {
            case "hegemony":
                return "The antique Domain fabricator shudders with a deep hydraulic clunk, venting ozone as its heavy brass dispenser tray slides out. A stamped metal platter holds thick iron-rich soy stew, dense hardtack, and scalding chicory coffee. Austere, piping hot, and engineered to fuel a twelve-hour patrol burn.";
            case "tritachyon":
            case "tri_tachyon":
                return "The modified Domain chassis hums at a high molecular frequency before unsealing its magnetic delivery hatch with a soft hiss of coolant vapor. A matte-black composite tray presents laser-seared synth-fillet, an iridescent amino-gel matrix, and chilled nootropic soda. Clean and mathematically optimized.";
            case "sindrian_diktat":
            case "diktat":
            case "sindrian":
                return "The brass-vented Domain machine roars like an orbital thruster as its steam pistons crack open. A polished platter emerges carrying split Volturnian lobster tail in fiery pepper butter, spiced aquaculture flatfish, and purple reef wine. Rich, decadent, and smelling of clarified butter and brine.";
            case "luddic_church":
            case "church":
                return "Spacers watch with quiet reverence as the Church-consecrated Domain thermal unit unlatches its heavy insulated door. A fragrant ceramic bowl of barley and roasted fowl pie emerges alongside dense stone-ground sourdough, churned butter, and steaming spiced cider. It tastes of fertile soil and honest labor.";
            case "luddic_path":
            case "path":
                return "The battered, chip-hammered Domain unit groans in protest, its uncalibrated chute sputtering grey penance broth into a dented tin mess kit beside boiled turnip mash and salted mutton. Coarse, harsh, and completely unspiced: fuel meant only to keep a holy combatant upright.";
            case "pirates":
            case "pirate":
                return "Someone kicks the side of the junk-rigged Domain fabricator. It rattles violently, spits an acrid puff of grease smoke, and clangs open to dump sizzling blackened ribs, peppery fungal goulash, and caustic moonshine onto sheet metal. Fumes sting your eyes, but the heavy calories fuel a salvage run.";
            case "persean":
            case "persean_league":
            case "league":
                return "The polished Domain fabricator smoothly uncouples its pressurized amphora line with a chime, dispensing rosemary-rubbed lamb skewers, herbed flatbread, saffron waterfowl rice, and chilled citrus liqueur onto earthenware. Crisp, fragrant, and reflecting the flourishing trade routes of the League.";
            default:
                return "The battered blue Domain fabricator hums reassuringly as its heavy dispensing hatch slides forward. A sturdy ceramic platter holds thick root-vegetable beef stew, golden waffles slathered in butter, and a mug of scalded black coffee. Honest, dependable spacer fare engineered for long burns.";
        }
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogDelegate.CustomDialogCallback callback) {
        this.callback = callback;
        radioButtons.clear();

        float width = panel.getPosition().getWidth();
        float height = panel.getPosition().getHeight();
        if (width <= 0) width = 840f;
        if (height <= 0) height = 600f;

        float contentWidth = width - 40f;

        TooltipMakerAPI main = panel.createUIElement(width, height, true);
        main.getPosition().inTL(0f, 0f);

        // Header
        main.addSectionHeading("THE ORBITING SPOON: JURY-RIGGED DOMAIN CULINARY FABRICATOR", Alignment.MID, 0f);
        main.addSpacer(8f);

        // Context info
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        long playerCredits = playerFleet != null ? (long) playerFleet.getCargo().getCredits().get() : 0;
        int lobsterInCargo = playerFleet != null ? (int) playerFleet.getCargo().getCommodityQuantity(Commodities.LOBSTER) : 0;
        int luxuryInCargo = playerFleet != null ? (int) playerFleet.getCargo().getCommodityQuantity(Commodities.LUXURY_GOODS) : 0;
        int foodInCargo = playerFleet != null ? (int) playerFleet.getCargo().getCommodityQuantity(Commodities.FOOD) : 0;

        String diningPartyText;
        if (officer != null) {
            int fs = OS_OfficerFriendship.getFriendship(officer);
            String tier = OS_OfficerFriendship.getTierName(fs);
            diningPartyText = "Dining Party: Wardroom Table with " + officer.getNameString() + " (Level " + officer.getStats().getLevel() + ", Camaraderie: " + fs + "% [" + tier + "])";
        } else {
            int crew = Math.max(1, (int) playerFleet.getCargo().getCrew());
            diningPartyText = "Dining Party: General Mess Hall with Fleet Crew (" + crew + " Spacers)";
        }
        main.addPara(diningPartyText, 2f, Misc.getHighlightColor(), officer != null ? officer.getNameString() : "Fleet Crew");

        String purserText = "Fleet Purser: " + String.format("%,d", playerCredits) + " credits | Holds: " +
                lobsterInCargo + "x Lobster, " + luxuryInCargo + "x Luxury Goods, " + foodInCargo + "x Food";
        main.addPara(purserText, 2f, Misc.getGrayColor());
        main.addSpacer(6f);

        // Morale / Digestion status
        boolean isDigesting = OS_ShoreLeaveBuff.isDigesting();
        if (isDigesting) {
            float digRemain = OS_ShoreLeaveBuff.getDigestionDaysRemaining();
            String activeMeal = OS_ShoreLeaveBuff.getMealName(OS_ShoreLeaveBuff.getActiveFaction());
            main.addPara(
                    "Crew Satiation: The fleet is still working through leftovers from " + activeMeal + " (" + String.format("%.1f", digRemain) + " days remaining). Satiated spacers cannot stomach another full meal spread.",
                    Misc.getNegativeHighlightColor(),
                    4f
            );
        } else if (OS_ShoreLeaveBuff.isBuffActive()) {
            int buffDays = (int) Math.ceil(OS_ShoreLeaveBuff.getDaysRemaining());
            String activeMeal = OS_ShoreLeaveBuff.getMealName(OS_ShoreLeaveBuff.getActiveFaction());
            main.addPara(
                    "Fleet Morale: Shore Leave currently active (" + activeMeal + " - " + buffDays + " days remaining). Placing a new order will refresh and update your fleet's morale perks.",
                    Misc.getPositiveHighlightColor(),
                    4f
            );
        } else {
            main.addPara(
                    "Galley Status: Hearth fired and ready to serve. Select a course below to feed your dining party.",
                    Misc.getPositiveHighlightColor(),
                    4f
            );
        }
        main.addSpacer(10f);

        // Meal Option Cards
        for (MealOption opt : mealOptions.values()) {
            // Section heading for category
            main.addSectionHeading(opt.category.toUpperCase() + ": " + opt.title, Alignment.LMID, 6f);
            main.addSpacer(4f);

            // Card body with image + text - pass contentWidth so paragraph text strictly wraps
            TooltipMakerAPI card = main.beginImageWithText(opt.iconSprite, 48f, contentWidth, false);

            card.addPara("Cost / Provisions: %s", 0f, Misc.getTextColor(), Misc.getHighlightColor(), opt.costString);
            card.addPara(opt.description, 3f);
            card.addPara("Fleet Morale Impact: %s", 3f, Misc.getTextColor(), Misc.getPositiveHighlightColor(), opt.perkSummary);
            card.addPara("Cultural / Doctrine Impact: %s", 2f, Misc.getTextColor(), Misc.getHighlightColor(), opt.doctrineImpact);

            if (opt.officerOpinion != null && !opt.officerOpinion.isEmpty()) {
                card.addPara("%s", 3f, Misc.getTextColor(), Misc.getHighlightColor(), opt.officerOpinion);
            }

            if (!opt.canAfford) {
                card.addPara("Status: %s", 3f, Misc.getNegativeHighlightColor(), Misc.getNegativeHighlightColor(), opt.unaffordableReason);
            }

            main.addImageWithText(4f);
            main.addSpacer(4f);

            // Radio Button
            String radioLabel = "Select: " + opt.title + " (" + opt.costString + ")";
            ButtonAPI radioBtn = main.addAreaCheckbox(
                    radioLabel,
                    opt.id,
                    Misc.getBasePlayerColor(),
                    Misc.getDarkPlayerColor(),
                    Misc.getBrightPlayerColor(),
                    contentWidth,
                    24f,
                    2f,
                    true
            );

            boolean isSelected = opt.id.equals(selectedMealId);
            radioBtn.setChecked(isSelected);

            if (isDigesting) {
                radioBtn.setEnabled(false);
                radioBtn.setShowTooltipWhileInactive(true);
            } else if (!opt.canAfford) {
                radioBtn.setEnabled(false);
                radioBtn.setShowTooltipWhileInactive(true);
            } else {
                radioBtn.setEnabled(true);
            }

            radioButtons.put(opt.id, radioBtn);
            main.addSpacer(10f);
        }

        panel.addUIElement(main).inTL(0f, 0f);
    }

    @Override
    public boolean hasCancelButton() {
        return true;
    }

    @Override
    public String getConfirmText() {
        return "Place Order";
    }

    @Override
    public String getCancelText() {
        return "Close Menu";
    }

    @Override
    public void customDialogConfirm() {
        if (orderExecuted) return;

        if (OS_ShoreLeaveBuff.isDigesting()) {
            float digRemain = OS_ShoreLeaveBuff.getDigestionDaysRemaining();
            String activeMeal = OS_ShoreLeaveBuff.getMealName(OS_ShoreLeaveBuff.getActiveFaction());
            dialog.getTextPanel().addParagraph(
                    "Your crew is still digesting leftover " + activeMeal + " (" + String.format("%.1f", digRemain) + " days remaining). Satiated spacers cannot stomach another full meal spread.",
                    Misc.getNegativeHighlightColor()
            );
            refreshDialogOptions();
            return;
        }

        MealOption chosen = mealOptions.get(selectedMealId);
        if (chosen == null) {
            dialog.getTextPanel().addParagraph("No meal was selected.", Misc.getNegativeHighlightColor());
            refreshDialogOptions();
            return;
        }

        if (!chosen.canAfford) {
            dialog.getTextPanel().addParagraph(
                    chosen.unaffordableReason != null ? chosen.unaffordableReason : "You cannot afford this meal.",
                    Misc.getNegativeHighlightColor()
            );
            refreshDialogOptions();
            return;
        }

        executeMealOrder(chosen);
        orderExecuted = true;
    }

    @Override
    public void customDialogCancel() {
        refreshDialogOptions();
    }

    @Override
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return new BaseCustomUIPanelPlugin() {
            @Override
            public void buttonPressed(Object buttonId) {
                if (buttonId instanceof String) {
                    String id = (String) buttonId;
                    if (mealOptions.containsKey(id)) {
                        selectedMealId = id;
                        for (Map.Entry<String, ButtonAPI> entry : radioButtons.entrySet()) {
                            entry.getValue().setChecked(entry.getKey().equals(id));
                        }
                    }
                }
            }
        };
    }

    private void executeMealOrder(MealOption opt) {
        CampaignFleetAPI playerFleet = Global.getSector() != null ? Global.getSector().getPlayerFleet() : null;
        if (playerFleet == null || playerFleet.getCargo() == null) return;

        // Deduct payment or commodities
        if (opt.commodityId != null && opt.commodityCost > 0) {
            playerFleet.getCargo().removeCommodity(opt.commodityId, opt.commodityCost);
            AddRemoveCommodity.addCommodityLossText(opt.commodityId, opt.commodityCost, dialog.getTextPanel());
        } else if (opt.creditCost > 0) {
            float currentCreds = playerFleet.getCargo().getCredits().get();
            if (currentCreds >= opt.creditCost) {
                playerFleet.getCargo().getCredits().subtract(opt.creditCost);
                AddRemoveCommodity.addCreditsLossText(opt.creditCost, dialog.getTextPanel());
            } else {
                playerFleet.getCargo().getCredits().subtract(currentCreds);
                if ((int) currentCreds > 0) {
                    AddRemoveCommodity.addCreditsLossText((int) currentCreds, dialog.getTextPanel());
                }
            }
        }

        // Famine Relief reputation bonus
        if (opt.isEmergencyRelief) {
            MarketAPI market = dialog.getInteractionTarget() != null ? dialog.getInteractionTarget().getMarket() : null;
            if (market != null && market.getFaction() != null) {
                CoreReputationPlugin.CustomRepImpact impact = new CoreReputationPlugin.CustomRepImpact();
                impact.delta = 0.05f;
                Global.getSector().adjustPlayerReputation(
                        new CoreReputationPlugin.RepActionEnvelope(CoreReputationPlugin.RepActions.CUSTOM, impact, null, dialog.getTextPanel(), true),
                        market.getFactionId()
                );
            }
        }

        // Grant Shore Leave buff
        OS_ShoreLeaveBuff.applyBuff(dialog, opt.buffKey, opt.durationDays);

        // Narrative meal arrival description
        String arrivalText;
        if ("lobster_feast".equals(opt.buffKey)) {
            arrivalText = "You feed a pressurized cryo-crate of live blue-shell lobster into the raw-material hopper of 'The Orbiting Spoon'. The ancient machine's processors chime with forgotten Domain haute-cuisine protocols, steaming the butter-poached tails and cracking claws with surgical precision. The lavish aroma commands instant, reverent silence across the table.";
        } else if ("luxury_feast".equals(opt.buffKey)) {
            arrivalText = "You unseal vintage pre-Collapse luxury canisters into the fabricator's auxiliary gourmet bay. Centuries-old preservation seals pop as century-aged vintages breathe in decanters beside smoked delicacies. Sharing authentic Domain-era culinary luxury in this war-torn cycle is a profound reaffirmation of why we fight.";
        } else if ("famine_stew".equals(opt.buffKey)) {
            arrivalText = "Pallets of bulk grain and preserved ration bricks are emptied into the fabricator's primary hopper. 'The Orbiting Spoon' hums continuously at maximum output, boiling massive cauldrons of gratitude stew for the station's starving dockworkers.";
        } else {
            arrivalText = getMealArrivalNarration(factionId, isWardroom);
        }
        dialog.getTextPanel().addParagraph(arrivalText, Misc.getTextColor());

        // Officer camaraderie & reaction
        if (officer != null) {
            String reaction = OS_OfficerFriendship.getMealReactionText(officer, opt.buffKey);
            if (reaction != null && !reaction.isEmpty()) {
                dialog.getTextPanel().addParagraph(reaction, Misc.getTextColor());
            }
            int delta = OS_OfficerFriendship.getMealCamaraderieDelta(officer, opt.buffKey);
            OS_OfficerFriendship.addFriendship(officer, delta, dialog);
        }

        // Award Dining Experience (Officer XP + Fleet Bonus XP or Crew Bonus XP)
        OS_OfficerMentoring.awardDiningExperience(dialog, officer);

        // Play UI sound
        Global.getSoundPlayer().playUISound("ui_char_increase_skill", 1f, 1f);

        // Refresh interaction dialog options to reflect new state
        refreshDialogOptions();
    }

    private void refreshDialogOptions() {
        int officerCount = OS_PickRandomOfficer.getHumanOfficers().size();
        OS_PickRandomOfficer.populateMainMenu(dialog, officer, officerCount, factionId);
    }
}

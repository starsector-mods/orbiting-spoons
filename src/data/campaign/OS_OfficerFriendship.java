package data.campaign;

import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.Misc;

public class OS_OfficerFriendship {

    public static final String KEY_FRIENDSHIP = "$os_friendship";
    public static final String BUFF_ID_OFFICER = "os_officer_camaraderie";

    public static final int THRESHOLD_RETRAIN = 50;
    public static final int THRESHOLD_LOYAL = 75;

    public static int getFriendship(PersonAPI person) {
        if (person == null) return 0;
        MemoryAPI mem = person.getMemoryWithoutUpdate();
        if (mem == null) return 0;
        if (!mem.contains(KEY_FRIENDSHIP)) {
            // Default base relationship for newly recruited officers
            mem.set(KEY_FRIENDSHIP, 15);
            return 15;
        }
        return Math.max(0, Math.min(100, mem.getInt(KEY_FRIENDSHIP)));
    }

    public static void setFriendship(PersonAPI person, int value) {
        if (person == null) return;
        MemoryAPI mem = person.getMemoryWithoutUpdate();
        if (mem != null) {
            mem.set(KEY_FRIENDSHIP, Math.max(0, Math.min(100, value)));
        }
    }

    public static int addFriendship(PersonAPI person, int delta, InteractionDialogAPI dialog) {
        if (person == null) return 0;
        int current = getFriendship(person);
        int next = Math.max(0, Math.min(100, current + delta));
        setFriendship(person, next);

        if (dialog != null && dialog.getTextPanel() != null && delta != 0) {
            String tierOld = getTierName(current);
            String tierNew = getTierName(next);

            dialog.getTextPanel().setFontSmallInsignia();
            if (delta > 0) {
                String deltaStr = "+" + delta;
                if (!tierOld.equals(tierNew)) {
                    dialog.getTextPanel().addParagraph(
                        person.getNameString() + "'s camaraderie increased by " + deltaStr + "%! Reached " + tierNew + " (" + next + "%).",
                        Misc.getPositiveHighlightColor()
                    );
                    dialog.getTextPanel().highlightInLastPara(Misc.getHighlightColor(), deltaStr + "%", tierNew, next + "%");
                } else {
                    dialog.getTextPanel().addParagraph(
                        person.getNameString() + "'s camaraderie increased by " + deltaStr + "% (Current: " + next + "% - " + tierNew + ").",
                        Misc.getPositiveHighlightColor()
                    );
                    dialog.getTextPanel().highlightInLastPara(Misc.getHighlightColor(), deltaStr + "%", next + "%");
                }
            } else {
                String deltaStr = String.valueOf(delta);
                if (!tierOld.equals(tierNew)) {
                    dialog.getTextPanel().addParagraph(
                        person.getNameString() + "'s camaraderie decreased by " + deltaStr + "%! Dropped to " + tierNew + " (" + next + "%).",
                        Misc.getNegativeHighlightColor()
                    );
                    dialog.getTextPanel().highlightInLastPara(Misc.getNegativeHighlightColor(), deltaStr + "%", tierNew, next + "%");
                    if (current >= THRESHOLD_RETRAIN && next < THRESHOLD_RETRAIN) {
                        dialog.getTextPanel().addParagraph(
                            "Warning: Camaraderie has fallen below Trusted Comrade. Tactical doctrine retraining is locked until trust is restored.",
                            Misc.getNegativeHighlightColor()
                        );
                    }
                } else {
                    dialog.getTextPanel().addParagraph(
                        person.getNameString() + "'s camaraderie decreased by " + deltaStr + "% (Current: " + next + "% - " + tierNew + ").",
                        Misc.getNegativeHighlightColor()
                    );
                    dialog.getTextPanel().highlightInLastPara(Misc.getNegativeHighlightColor(), deltaStr + "%", next + "%");
                }
            }
            dialog.getTextPanel().setFontInsignia();
        }
        return next;
    }

    public static String getTierName(int friendship) {
        if (friendship >= THRESHOLD_LOYAL) {
            return "Loyal Confidant";
        } else if (friendship >= THRESHOLD_RETRAIN) {
            return "Trusted Comrade";
        } else if (friendship >= 25) {
            return "Favorable Colleague";
        } else {
            return "Professional Acquaintance";
        }
    }

    public static boolean canRetrain(PersonAPI person) {
        return getFriendship(person) >= THRESHOLD_RETRAIN;
    }

    public static boolean isLoyal(PersonAPI person) {
        return getFriendship(person) >= THRESHOLD_LOYAL;
    }

    public static String getPersonalityId(PersonAPI person) {
        if (person == null || person.getPersonalityAPI() == null) return "steady";
        return person.getPersonalityAPI().getId().toLowerCase().trim();
    }

    public static int getMealCamaraderieDelta(PersonAPI officer, String factionId) {
        if (officer == null) return 10;
        String p = getPersonalityId(officer);
        String f = factionId != null ? factionId.toLowerCase().trim() : "independent";

        boolean isNative = officer.getFaction() != null && officer.getFaction().getId().equalsIgnoreCase(f);
        int nativeBonus = isNative ? 5 : 0;

        // Cargo banquets and famine relief
        if ("lobster_feast".equals(f)) {
            if ("cautious".equals(p) || "timid".equals(p)) {
                return 20;
            }
            return 25;
        }

        if ("luxury_feast".equals(f)) {
            if ("cautious".equals(p) || "timid".equals(p) || "steady".equals(p)) {
                return 20;
            }
            return 15;
        }

        if ("famine_stew".equals(f)) {
            if ("timid".equals(p) || "steady".equals(p)) {
                return 15;
            }
            return 10;
        }

        switch (p) {
            case "reckless":
                // Likes: charred ribs (pirates), greasy diner platter (independent), volcanic fish & fuel schnapps (sindrian_diktat)
                if ("pirates".equals(f) || "pirate".equals(f) || "independent".equals(f) || "sindrian_diktat".equals(f) || "diktat".equals(f)) {
                    return 15 + nativeBonus;
                }
                // Dislikes: synthetic foam (tritachyon), penance broth (luddic_path), finger food (persean)
                if ("tritachyon".equals(f) || "tri_tachyon".equals(f) || "luddic_path".equals(f) || "path".equals(f) || "persean".equals(f) || "persean_league".equals(f)) {
                    return isNative ? 0 : -5;
                }
                return 10 + nativeBonus;

            case "timid":
                // Likes: comforting hearth bread & pie (luddic_church), warm diner classic (independent)
                if ("luddic_church".equals(f) || "church".equals(f) || "independent".equals(f)) {
                    return 15 + nativeBonus;
                }
                // Dislikes: pulsating fungus (pirates), grim penance mud (luddic_path), throat-burning peppers/schnapps (sindrian_diktat)
                if ("pirates".equals(f) || "pirate".equals(f) || "luddic_path".equals(f) || "path".equals(f) || "sindrian_diktat".equals(f) || "diktat".equals(f)) {
                    return isNative ? 0 : -5;
                }
                return 10 + nativeBonus;

            case "aggressive":
                // Likes: iron combat brisket (hegemony), fiery flatfish & schnapps (sindrian_diktat), charred scraper ribs (pirates)
                if ("hegemony".equals(f) || "sindrian_diktat".equals(f) || "diktat".equals(f) || "pirates".equals(f) || "pirate".equals(f)) {
                    return 15 + nativeBonus;
                }
                // Dislikes: watery turnip slop (luddic_path), fake polymer gel (tritachyon), sweet pastry (luddic_church)
                if ("luddic_path".equals(f) || "path".equals(f) || "tritachyon".equals(f) || "tri_tachyon".equals(f) || "luddic_church".equals(f) || "church".equals(f)) {
                    return isNative ? 0 : -5;
                }
                return 10 + nativeBonus;

            case "cautious":
                // Likes: sterile optimized synth-steak (tritachyon), clean lean skewers (persean), mil-spec navy ration (hegemony)
                if ("tritachyon".equals(f) || "tri_tachyon".equals(f) || "persean".equals(f) || "persean_league".equals(f) || "hegemony".equals(f)) {
                    return 15 + nativeBonus;
                }
                // Dislikes: uninspected gutter biohazard (pirates), heavy artery grease (independent), unpasteurized dysentery risk (luddic_path)
                if ("pirates".equals(f) || "pirate".equals(f) || "independent".equals(f) || "luddic_path".equals(f) || "path".equals(f)) {
                    return isNative ? 0 : -5;
                }
                return 10 + nativeBonus;

            case "steady":
            default:
                // Likes: honest diner classic (independent), dependable naval staple (hegemony), native cuisine
                if ("independent".equals(f) || "hegemony".equals(f) || isNative) {
                    return 15 + nativeBonus;
                }
                // Dislikes: punishment turnip water (luddic_path), artificial amino paste (tritachyon)
                if ("luddic_path".equals(f) || "path".equals(f) || "tritachyon".equals(f) || "tri_tachyon".equals(f)) {
                    return isNative ? 0 : -5;
                }
                return 10 + nativeBonus;
        }
    }

    public static String getOfficerFoodPreferenceHint(PersonAPI officer, String factionId) {
        if (officer == null) return "";
        String p = getPersonalityId(officer);
        String f = factionId != null ? factionId.toLowerCase().trim() : "independent";
        int delta = getMealCamaraderieDelta(officer, factionId);
        boolean isNative = officer.getFaction() != null && officer.getFaction().getId().equalsIgnoreCase(f);

        if ("lobster_feast".equals(f)) {
            return "[Banquet | +" + delta + "% Camaraderie]\nFresh Volturnian catch: a legendary morale feast that cements deep wardroom loyalty.";
        }
        if ("luxury_feast".equals(f)) {
            return "[Banquet | +" + delta + "% Camaraderie]\nPre-Collapse vintage: an extraordinary reminder of Domain culture that inspires long-term commitment.";
        }

        String tag;
        if (delta >= 15) {
            tag = "[Liked | +" + delta + "% Camaraderie]";
        } else if (delta < 0) {
            tag = "[Disliked | " + delta + "% Camaraderie]";
        } else {
            tag = "[Neutral | +" + delta + "% Camaraderie]";
        }

        String doctrineReason;
        switch (p) {
            case "reckless":
                if (delta >= 15) {
                    doctrineReason = "Reckless Doctrine: Enjoys raw heat, charred meats, and high-proof spirits that keep watchstanders sharp on the edge.";
                } else if (delta < 0) {
                    doctrineReason = "Reckless Doctrine: Frustrated by sterile synthetic matrices, bland penance broths, and fussy ceremonial finger food.";
                } else {
                    doctrineReason = "Reckless Doctrine: Accepts any hot meal that keeps the ship burning.";
                }
                break;
            case "timid":
                if (delta >= 15) {
                    doctrineReason = "Timid Doctrine: Values warm hearth food, fresh-baked bread, and a peaceful meal away from cockpit combat klaxons.";
                } else if (delta < 0) {
                    doctrineReason = "Timid Doctrine: Anxious around uninspected dive food, blistering spices, or caustic spirits that slow escort intercept reflexes.";
                } else {
                    doctrineReason = "Timid Doctrine: Appreciates a quiet, uninterrupted meal to settle frayed nerves.";
                }
                break;
            case "aggressive":
                if (delta >= 15) {
                    doctrineReason = "Aggressive Doctrine: Craves dense combat protein and heavy calories to fuel aggressive forward momentum.";
                } else if (delta < 0) {
                    doctrineReason = "Aggressive Doctrine: Disdains watery rations, corporate gel matrices, and comfort pastries that dull offensive edge.";
                } else {
                    doctrineReason = "Aggressive Doctrine: Accepts standard provisions as functional fuel for the vanguard.";
                }
                break;
            case "cautious":
                if (delta >= 15) {
                    doctrineReason = "Cautious Doctrine: Prioritizes hermetically sealed rations, verified hygiene, and controlled nutrition that prevent crew downtime.";
                } else if (delta < 0) {
                    doctrineReason = "Cautious Doctrine: Distrusts uninspected dive grease, heavy artery sludge, and unpasteurized water as unacceptable operational hazards.";
                } else {
                    doctrineReason = "Cautious Doctrine: Accepts reliable standard rations with verified preparation.";
                }
                break;
            case "steady":
            default:
                if (delta >= 15) {
                    doctrineReason = "Steady Doctrine: Values dependable, honest naval staples and authentic regional cooking that foster wardroom unity.";
                } else if (delta < 0) {
                    doctrineReason = "Steady Doctrine: Skeptical of tasteless synthetic cubes and punitive starvation broths that lower wardroom morale.";
                } else {
                    doctrineReason = "Steady Doctrine: Appreciates breaking bread together over a hot, solid meal.";
                }
                break;
        }

        String nativeNote = isNative ? "\n- Native Heritage: +5% bonus for dining on regional comfort food." : "";
        return tag + "\n" + doctrineReason + nativeNote;
    }

    public static String getMealReactionText(PersonAPI officer, String factionId) {
        if (officer == null) return "";
        String p = getPersonalityId(officer);
        String f = factionId != null ? factionId.toLowerCase().trim() : "independent";
        String name = officer.getNameString();

        switch (p) {
            case "reckless":
                if ("lobster_feast".equals(f)) {
                    return name + " cracks a claw with practiced ease, pouring a measure of drawn butter. \"Fresh ocean catch in the middle of a broken sector. When you've spent weeks chewing emergency nutrient paste in a depressurized hull, meals like this remind you why you fight to survive another burn.\"";
                } else if ("luxury_feast".equals(f)) {
                    return name + " sips the century-aged vintage, eyeing the amber hue against the light. \"Pre-Collapse vintages... clean, untouched by reactor leaks or recycled water. We might catch a torpedo volley tomorrow, Captain, so let's drink the good stuff tonight.\"";
                } else if ("famine_stew".equals(f)) {
                    return name + " scoops up the thick broth with a piece of crust. \"Hot, heavy, and real. When our supply line collapsed during the siege of Mayasura, we would have killed for a bowl like this. Good on you for feeding the dockers.\"";
                } else if ("tritachyon".equals(f) || "tri_tachyon".equals(f)) {
                    return name + " stares at the shimmering amino-cube with grim disgust. \"Synthetic gel matrices. Tastes identical to the survival paste we rationed when my frigate lost primary life support. If I wanted to feel like I was slowly freezing in a derelict, I'd vent the galley.\"";
                } else if ("luddic_path".equals(f) || "path".equals(f)) {
                    return name + " prods the watery turnip mash with a flat expression. \"Unsalted root slop. I've drifted on battery power eating worse, Captain, but there's no honor in starving your officers before an engagement.\"";
                } else if ("persean".equals(f) || "persean_league".equals(f)) {
                    return name + " shakes their head at the delicate olive plate and miniature skewers. \"Neat little garnishes and decorative skewers. Looks pretty on a League flagship's salon table, but it won't keep your blood warm when the main drive vents heat into the crew deck.\"";
                } else if ("pirates".equals(f) || "pirate".equals(f)) {
                    return name + " grins through the smoke, pulling tender meat from charred ribs. \"Charred over open heat, smelling of sulfur, and washed down with moonshine that strips reactor carbon. That's real void-rat fuel. Hits the gut like a hammer.\"";
                } else if ("sindrian_diktat".equals(f) || "diktat".equals(f)) {
                    return name + " downs the fiery liquor without blinking, savoring the blistering chili fish. \"Peppers hot enough to sear your lungs and liquor that smells like thruster propellant. That'll wake up a watchstander's reflexes faster than a battle klaxon.\"";
                } else if ("independent".equals(f)) {
                    return name + " digs into the thick stew and drains half the scalding coffee. \"Heavy beef, dense starch, and coffee strong enough to wake a dead helmsman. Honest fuel for officers who know any jump might be their last.\"";
                } else {
                    return name + " finishes their plate with steady determination, leaning back. \"Real heat, real flavor, Captain. As long as the engines turn and the mess serves hot food, I'm ready to take the ship into whatever awaits us.\"";
                }

            case "timid":
                if ("lobster_feast".equals(f)) {
                    return name + " exhales quietly, savoring the butter-poached lobster. \"A rare luxury out here, Captain. On escort duty, your ears ring with radar pings and proximity warnings for hours. Sitting down to a feast like this... it settles the tremors in your hands.\"";
                } else if ("luxury_feast".equals(f)) {
                    return name + " studies the delicate pastries and pre-Collapse vintage with genuine appreciation. \"Quiet elegance away from combat stations. When you spend every watch anticipating incoming torpedo spreads, a peaceful, dignified table is the best medicine a commander can ask for.\"";
                } else if ("famine_stew".equals(f)) {
                    return name + " quietly breaks bread over the warm communal bowl. \"It's good to provide stability where we can, Captain. Defensive screening isn't just about weapon mounts: it's about keeping vulnerable people safe from the wolves in the dark.\"";
                } else if ("pirates".equals(f) || "pirate".equals(f)) {
                    return name + " eyes the smoking platter with disciplined caution. \"Unsealed grease, volatile moonshine, and an active dive full of armed corsairs. If we suffer an emergency deployment now, our reaction times on the point-defense grid will be severely compromised. I'll stick to water, Captain.\"";
                } else if ("luddic_path".equals(f) || "path".equals(f)) {
                    return name + " pushes the cloudy broth around their tin cup, brow furrowed. \"Grim rations, Captain. Defensive maneuvers demand absolute mental clarity and rapid tactical calculations. Living on thin root water is an invitation to pilot fatigue when the missiles start flying.\"";
                } else if ("sindrian_diktat".equals(f) || "diktat".equals(f)) {
                    return name + " wipes their brow after a tentative taste of the spiced skewers. \"The heat is overpowering, Captain... and high-proof schnapps will slow my intercept reflexes on the bridge. I need a clear head when we're tracking saturation missile barrages.\"";
                } else if ("luddic_church".equals(f) || "church".equals(f)) {
                    return name + " leans back against the wooden bench with visible relief, savoring the warm sourdough and cider. \"Fresh bread, real butter, and quiet. When you live under the constant howl of cockpit missile warnings, a peaceful hearth meal feels like heaven. Thank you, Captain.\"";
                } else if ("independent".equals(f)) {
                    return name + " warms their hands against the coffee mug, nodding softly. \"Simple, hearty, and dependable. No tactical alarms, no incoming ordnance warnings: just solid food that lets an escort skipper breathe for an hour.\"";
                } else {
                    return name + " eats steadily, keeping an eye on the mess hall layout. \"Good, wholesome food, Captain. It's comforting to have a quiet meal before we take up screening positions around the fleet again.\"";
                }

            case "aggressive":
                if ("lobster_feast".equals(f)) {
                    return name + " efficiently dismantles the lobster tail, nodding approval. \"High-grade protein and morale fuel, Captain. Feeding the division like victors reinforces the expectation of victory. Let the crew taste success; then let's put them on the enemy's throat.\"";
                } else if ("luxury_feast".equals(f)) {
                    return name + " cuts a portion of cured game, washing it down with vintage wine. \"Quality provisions demonstrate fleet supremacy, Captain. Our gun crews know we command the field when the wardroom eats like this. Now let's turn that morale into forward momentum.\"";
                } else if ("famine_stew".equals(f)) {
                    return name + " downs the dense communal stew without hesitation. \"Field rations. Fills the tank and keeps the body operational. Good for garrison troops, but when we jump, my vanguard needs high-density calories to sustain aggressive combat maneuvers.\"";
                } else if ("luddic_path".equals(f) || "path".equals(f)) {
                    return name + " pushes the tin tray forward with sharp disdain. \"Watery turnip broth and boiled weeds. You can't sustain aggressive assault doctrine on negative caloric intake, Captain. My division burns energy pushing through enemy firing arcs; they need real protein, not ascetic starvation.\"";
                } else if ("tritachyon".equals(f) || "tri_tachyon".equals(f)) {
                    return name + " cuts into the synth-fillet with mechanical precision. \"Chemically processed nutrient paste. It hits the macronutrient targets on a datapad, but it does nothing for wardroom aggression. Tactical commanders need real fuel before we breach an enemy line, not corporate polymer.\"";
                } else if ("luddic_church".equals(f) || "church".equals(f)) {
                    return name + " breaks a piece of bread, eyeing the pastries with impatience. \"Wholesome harvest food, but too soft for an assault division. Comfort meals breed hesitation, Captain. I want my gunners hungry, focused, and ready to seize the initiative the moment shields drop.\"";
                } else if ("hegemony".equals(f)) {
                    return name + " slices through the dense iron brisket with steady, purposeful motions. \"Heavy protein, dense calories, and chicory coffee that sharpens the edge. That's proper combat fuel. Keeps the bridge division focused through high-G flanking burns.\"";
                } else if ("sindrian_diktat".equals(f) || "diktat".equals(f)) {
                    return name + " strips the spiced aquaculture skewer in one motion and nods firmly. \"Pungent spices and concentrated fuel. Sharpens your aggression and clears the sinuses. That's the energy we need when spearheading an assault formation.\"";
                } else if ("pirates".equals(f) || "pirate".equals(f)) {
                    return name + " consumes the charred ribs with brisk efficiency. \"Direct heat, dense meat, and liquor with teeth. No wasted pleasantries, no ceremonial nonsense: just raw fuel to keep the ship moving forward under fire.\"";
                } else {
                    return name + " finishes their meal with disciplined speed, checking their chronometer. \"Solid fuel, Captain. My division's caloric intake is set. Let's finish fleet prep, get out of dock, and take the fight to the enemy.\"";
                }

            case "cautious":
                if ("lobster_feast".equals(f)) {
                    return name + " inspects the fresh seafood before dining, nodding with measured satisfaction. \"Controlled cold-chain logistics from Volturn to our table. High in lean protein and essential electrolytes without risking gastrointestinal distress during high-G maneuvers. A sound logistical expenditure, Captain.\"";
                } else if ("luxury_feast".equals(f)) {
                    return name + " checks the hermetic seals on the pre-Collapse containers before pouring. \"Flawless seal integrity preserved across cycles. High-grade logistics prevent spoilage, and reliable provisions keep bridge crews at peak alertness. An exceptional, risk-free ration.\"";
                } else if ("famine_stew".equals(f)) {
                    return name + " eats a measured portion of the boiled stew. \"Prolonged boiling guarantees sterilization, and stabilizing portside goodwill secures our supply lines during port calls. Sound operational foresight, Captain.\"";
                } else if ("pirates".equals(f) || "pirate".equals(f)) {
                    return name + " pushes the unsealed platter away with firm professionalism. \"Uninspected meat prepared in a poorly ventilated asteroid slipway is an unacceptable operational risk, Captain. Contracting dysentery or acute food poisoning mid-transit is an unforced tactical disaster. I will take sealed field rations aboard the flagship.\"";
                } else if ("independent".equals(f)) {
                    return name + " dabs away surface grease with a napkin, assessing the diner spread methodically. \"Heavy grease and uncalibrated sodium. Traditional spacer comfort, certainly, but sluggish reaction times on the tactical grid during an emergency hyperspace breakout are entirely preventable hazards. Moderation is essential.\"";
                } else if ("luddic_path".equals(f) || "path".equals(f)) {
                    return name + " declines the murky broth with a shake of the head. \"Unfiltered cistern water and uninspected tubers. Compromising fleet readiness with waterborne pathogens before an operational sortie violates every standard of combat logistics. We cannot afford crew downtime.\"";
                } else if ("tritachyon".equals(f) || "tri_tachyon".equals(f)) {
                    return name + " examines the sealed synthetic packet with methodical approval. \"Hermetically sealed, standardized nutrient density, and zero biological contaminants. Corporate synthetics might lack culinary soul, but from a logistics and crew readiness perspective, it eliminates all dietary risk on watch.\"";
                } else if ("persean".equals(f) || "persean_league".equals(f)) {
                    return name + " savors the lean grilled fish and clean citrus water with quiet approval. \"Lean protein, controlled preparation, and verified water purity. High-standard galley discipline that keeps the command team alert without bogging down digestion.\"";
                } else if ("hegemony".equals(f)) {
                    return name + " nods over the mil-spec ration tin. \"Standardized naval packaging, verified shelf life, and tamper-evident seals. Predictable, hygienic, and resilient logistics. Exactly what an officer needs when maintaining standoff discipline in hostile space.\"";
                } else {
                    return name + " eats methodically, pacing each bite with professional discipline. \"Clean, balanced fuel, Captain. Fleet survival depends on operational discipline, beginning with what we put into our bodies before taking the watch.\"";
                }

            case "steady":
            default:
                if ("lobster_feast".equals(f)) {
                    return name + " raises a glass of reef wine in quiet respect. \"An extraordinary spread, Captain. Breaking bread over a feast like this cements trust across the wardroom. A crew that eats together stands together when the hull starts ringing.\"";
                } else if ("luxury_feast".equals(f)) {
                    return name + " pauses, savoring the pre-Collapse vintage with quiet reverence. \"A rare glimpse of what humanity was before the Collapse, Captain. A solemn reminder that we are fighting to preserve something lasting out here in the fringes.\"";
                } else if ("famine_stew".equals(f)) {
                    return name + " sits quietly among the dockworkers, breaking coarse bread. \"Honest hot broth for working people who keep the stations turning. A captain who remembers the deckhands and dockers is a captain worth following into any system.\"";
                } else if ("luddic_path".equals(f) || "path".equals(f)) {
                    return name + " stirs the cloudy root broth with calm patience. \"Ascetic discipline has its place in theology, Captain, but thin water doesn't keep an engineer's hands steady on emergency reactor dampeners. Our people need dependable sustenance to hold the line.\"";
                } else if ("tritachyon".equals(f) || "tri_tachyon".equals(f)) {
                    return name + " chews through the synthetic cube with an impassive expression. \"Calibrated amino paste pressed into a neat mold. It sustains the human machine, Captain, but it lacks the soul that keeps sailors loyal to their ship. We eat it, and we carry on.\"";
                } else if ("independent".equals(f)) {
                    return name + " leans back against the bench with a quiet nod. \"Good, honest spacer fare, Captain. Heavy stew, hot bread, and black coffee. That's the staple diet that opened the hyper-lanes, and it will see us through the next sortie.\"";
                } else if ("hegemony".equals(f)) {
                    return name + " nods calmly over the iron ration tin. \"Standard naval commissary rations. Dependable, unpretentious, and solid. The crew knows what to expect, and the ship stays combat-ready without complaints.\"";
                } else if (officer.getFaction() != null && officer.getFaction().getId().equalsIgnoreCase(f)) {
                    return name + " pauses, a faint smile touching weathered features as the familiar aroma rises. \"The authentic taste of home, Captain. You don't know how much it means to an old spacer to taste home cooking after cycles in the dark.\"";
                } else {
                    return name + " clears their plate and nods across the table with quiet gravity. \"Good hot food, Captain. The fleet runs best when the command staff takes time to break bread together before we burn.\"";
                }
        }
    }

    public static int getRetrainingCamaraderieDelta(PersonAPI officer) {
        if (officer == null) return -15;
        String p = getPersonalityId(officer);
        switch (p) {
            case "cautious": return -10; // Values analytical optimization
            case "timid": return -10;    // Anxious to adapt if it keeps them safe
            case "steady": return -15;   // Professional, but acknowledges strain
            case "aggressive": return -20;// Deeply resents having offensive aggression second-guessed
            case "reckless": return -20; // Furious at being told to hold back
            default: return -15;
        }
    }

    public static String getRetrainingReactionText(PersonAPI officer, String skillName) {
        if (officer == null) return "";
        String p = getPersonalityId(officer);
        String name = officer.getNameString();
        switch (p) {
            case "cautious":
                return name + " adjusts the tactical projection on their datapad, analyzing the engagement playback over black coffee. \"The telemetry logs don't lie, Captain. Retiring " + skillName + " alters our engagement envelope, but if the simulation models prove the doctrine improves fleet survivability margins, I will reprogram my bridge protocols accordingly.\"";
            case "timid":
                return name + " traces the defensive screening perimeter on the datapad, sipping their coffee in the quiet mess. \"If divesting from " + skillName + " lets us lock down our escort grid more effectively, Captain... I'll spend the transit drilling the new firing arcs. Keeping our ships intact and our crews alive is what matters.\"";
            case "steady":
                return name + " sets down their coffee mug and calmly wipes clean the tactical slate between your plates. \"Doctrine adjustments are part of fleet command, Captain. It takes hours of bridge drills to overwrite muscle memory from " + skillName + ", but unity of command comes first. I'll have the watch standing ready for the new regimen.\"";
            case "aggressive":
                return name + " taps a blunt finger against the telemetry plot, frowning slightly before leaning forward. \"Stripping " + skillName + " feels counterintuitive when forward momentum wins engagements, Captain. But looking at our firing vectors across these debrief logs, I see where you're directing the fleet's spearhead. I'll adapt my tactical approach to drive the new doctrine home.\"";
            case "reckless":
                return name + " drains the dregs of their black coffee and studies the scarred tactical slate with a grim, reflective nod. \"I've trusted " + skillName + " to pull my ship through burning reactor compartments and shattered hull plating, Captain. Letting go of hard-learned survival habits stings, but I trust your eye for the big picture. Tell the gun crews to recalibrate; I'll make the new doctrine work.\"";
            default:
                return name + " closes their datapad over empty plates and nods with professional resolve. \"Tactical debrief concluded, Captain. " + skillName + " has been retired from my doctrine profile. Ready to retrain on the next sortie.\"";
        }
    }

    public static String getLoyalPerkName(PersonAPI person) {
        String p = getPersonalityId(person);
        switch (p) {
            case "timid": return "Vigilant Early Warning";
            case "cautious": return "Preventive Fleet Maintenance";
            case "aggressive": return "Relentless Transit & Salvage";
            case "reckless": return "Audacious Reactor Tuning";
            case "steady":
            default: return "Disciplined Watchstanding";
        }
    }

    public static String getLoyalPerkSummary(PersonAPI person) {
        String p = getPersonalityId(person);
        switch (p) {
            case "timid":
                return "+10% Fleet Sensor Range, -5% Fleet Sensor Profile (Strict signature discipline)";
            case "cautious":
                return "-5% Fleet Supply Upkeep, +10% Ship Repair Rate (Methodical preventive logistics)";
            case "aggressive":
                return "+1 Sustained Fleet Burn, +10% Post-Battle Salvage (Aggressive expedition momentum)";
            case "reckless":
                return "-10% Fleet Hyperspace Fuel Consumption, -15% Emergency Burn & Jump CR Cost (Audacious drive tuning)";
            case "steady":
            default:
                return "+5% Daily CR Recovery Rate, -15% Hazard / Solar Storm Damage (Disciplined bridge routines)";
        }
    }

    public static void applyLoyalBonus(CampaignFleetAPI fleet) {
        if (fleet == null || fleet.getFleetData() == null) return;
        unapplyLoyalBonus(fleet);

        List<OfficerDataAPI> officers = fleet.getFleetData().getOfficersCopy();
        if (officers == null || officers.isEmpty()) return;

        // Track personality types already applied to prevent duplicate stacking
        java.util.Set<String> appliedPersonalities = new java.util.HashSet<>();

        // Apply campaign doctrine for any officer who has reached Loyal Confidant status
        for (OfficerDataAPI data : officers) {
            PersonAPI person = data != null ? data.getPerson() : null;
            if (person == null || person.isAICore()) continue;
            if (!isLoyal(person)) continue;

            String p = getPersonalityId(person);
            if (appliedPersonalities.contains(p)) continue; // Fleet-level doctrine: 1 per personality type
            appliedPersonalities.add(p);

            String buffKey = BUFF_ID_OFFICER + "_" + p;
            String name = person.getNameString();

            switch (p) {
                case "timid":
                    fleet.getStats().getSensorRangeMod().modifyPercent(buffKey, 10f, "Vigilant sensor watch (" + name + ")");
                    fleet.getStats().getSensorProfileMod().modifyPercent(buffKey, -5f, "Signature discipline (" + name + ")");
                    break;
                case "cautious":
                    for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
                        m.getStats().getRepairRatePercentPerDay().modifyPercent(buffKey, 10f, "Preventive maintenance (" + name + ")");
                        m.getStats().getSuppliesPerMonth().modifyPercent(buffKey, -5f, "Preventive supply rationing (" + name + ")");
                    }
                    break;
                case "aggressive":
                    fleet.getStats().getDynamic().getMod(Stats.SUSTAINED_BURN_BONUS).modifyFlat(buffKey, 1f, "Aggressive transit momentum (" + name + ")");
                    fleet.getStats().getDynamic().getStat(Stats.BATTLE_SALVAGE_MULT_FLEET).modifyPercent(buffKey, 10f, "Aggressive salvage scouring (" + name + ")");
                    break;
                case "reckless":
                    fleet.getStats().getFuelUseHyperMult().modifyMult(buffKey, 0.90f, "Audacious reactor fuel tuning (" + name + ")");
                    fleet.getStats().getDynamic().getStat(Stats.EMERGENCY_BURN_CR_MULT).modifyMult(buffKey, 0.85f, "Audacious transit maneuvers (" + name + ")");
                    fleet.getStats().getDynamic().getStat(Stats.DIRECT_JUMP_CR_MULT).modifyMult(buffKey, 0.85f, "Audacious transit maneuvers (" + name + ")");
                    break;
                case "steady":
                default:
                    for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
                        m.getStats().getBaseCRRecoveryRatePercentPerDay().modifyPercent(buffKey, 5f, "Disciplined watchstanding (" + name + ")");
                        m.getStats().getDynamic().getStat(Stats.CORONA_EFFECT_MULT).modifyMult(buffKey, 0.85f, "Disciplined storm drills (" + name + ")");
                    }
                    break;
            }
        }
    }

    public static void unapplyLoyalBonus(CampaignFleetAPI fleet) {
        if (fleet == null || fleet.getFleetData() == null) return;

        // Clean up standardized personality keys
        String[] personalities = new String[]{"timid", "cautious", "aggressive", "reckless", "steady"};
        for (String p : personalities) {
            String buffKey = BUFF_ID_OFFICER + "_" + p;

            fleet.getStats().getSensorRangeMod().unmodify(buffKey);
            fleet.getStats().getSensorProfileMod().unmodify(buffKey);
            fleet.getStats().getDynamic().getMod(Stats.SUSTAINED_BURN_BONUS).unmodify(buffKey);
            fleet.getStats().getDynamic().getStat(Stats.BATTLE_SALVAGE_MULT_FLEET).unmodify(buffKey);
            fleet.getStats().getFuelUseHyperMult().unmodify(buffKey);
            fleet.getStats().getDynamic().getStat(Stats.EMERGENCY_BURN_CR_MULT).unmodify(buffKey);
            fleet.getStats().getDynamic().getStat(Stats.DIRECT_JUMP_CR_MULT).unmodify(buffKey);

            for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
                m.getStats().getRepairRatePercentPerDay().unmodify(buffKey);
                m.getStats().getSuppliesPerMonth().unmodify(buffKey);
                m.getStats().getBaseCRRecoveryRatePercentPerDay().unmodify(buffKey);
                m.getStats().getDynamic().getStat(Stats.CORONA_EFFECT_MULT).unmodify(buffKey);
            }
        }

        // Also clean up any legacy officerId-based keys if present
        List<OfficerDataAPI> officers = fleet.getFleetData().getOfficersCopy();
        if (officers != null) {
            for (OfficerDataAPI data : officers) {
                PersonAPI person = data != null ? data.getPerson() : null;
                if (person == null) continue;
                String buffKey = BUFF_ID_OFFICER + "_" + person.getId();

                fleet.getStats().getSensorRangeMod().unmodify(buffKey);
                fleet.getStats().getSensorProfileMod().unmodify(buffKey);
                fleet.getStats().getDynamic().getMod(Stats.SUSTAINED_BURN_BONUS).unmodify(buffKey);
                fleet.getStats().getDynamic().getStat(Stats.BATTLE_SALVAGE_MULT_FLEET).unmodify(buffKey);
                fleet.getStats().getFuelUseHyperMult().unmodify(buffKey);
                fleet.getStats().getDynamic().getStat(Stats.EMERGENCY_BURN_CR_MULT).unmodify(buffKey);
                fleet.getStats().getDynamic().getStat(Stats.DIRECT_JUMP_CR_MULT).unmodify(buffKey);

                for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
                    m.getStats().getRepairRatePercentPerDay().unmodify(buffKey);
                    m.getStats().getSuppliesPerMonth().unmodify(buffKey);
                    m.getStats().getBaseCRRecoveryRatePercentPerDay().unmodify(buffKey);
                    m.getStats().getDynamic().getStat(Stats.CORONA_EFFECT_MULT).unmodify(buffKey);
                }
            }
        }
    }
}

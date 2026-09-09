# The Orbiting Spoon - Game Design Document (GDD)

**A Lightweight Campaign-Layer Roleplay Mod for Starsector**

---

## 1. Executive Summary & High Concept

*The Orbiting Spoon* enriches Starsector's campaign layer by adding a cozy, immersive roleplay venue: the Space Diner. Tucked into the corner of civilized portside bars sits *"The Orbiting Spoon"* - a massive, jury-rigged Domain-era automated culinary fabricator. 

This mod explicitly avoids bloated, complicated logistics systems or heavy math. It provides a simple, cheap, and flavorful way to grab a breather between bounties. Captains and deckhands alike crowd around cracked vinyl booths to eat warm regional dishes, bond with officers, and trigger a modest 14-day morale buff before launching back into the hyperspace storms.

---

## 2. Core Mechanics

### 2.1 The Space Diner Event
- **Trigger:** Accessible via the vanilla spaceport Bar menu.
- **Restrictions:** 
  - **Market Size 4+:** Only spawns on developed colonies/stations. Tiny outposts do not have the infrastructure to support the massive Domain fabricator.
  - **Vanilla Factions Only:** Restricted to Hegemony, Tri-Tachyon, Sindrian Diktat, Luddic Church, Luddic Path, Persean League, Pirates, Independent, and Player colonies. Prevents lore-clashes with custom modded factions.

### 2.2 Flat, Balanced Economy (The Roleplay Premium)
- Meals have a flat, predictable baseline price averaging **200 credits** (ranging from 50 to 600 credits depending on the faction).
- There is no massive `crewCount` multiplier. The flat cost ensures the diner remains a cheap, cozy roleplay activity rather than a punishing "idiot trap" for late-game fleets.

### 2.3 The Menu & Faction Flavor
The diner dynamically checks the controlling faction of the market and adjusts the ambient text and menu accordingly.
- **Hegemony:** Commissary Auxiliary Rations Set (150cr)
- **Tri-Tachyon:** Executive Synth-Steak Suite (450cr)
- **Sindrian Diktat:** Volturnian Feast (600cr)
- **Luddic Church:** Pilgrim's Hearth Harvest Feast (200cr)
- **Luddic Path:** Ascetic Penance Rations (50cr)
- **Pirates:** Fringe Scavenger Platter (100cr)
- **Persean League:** Grand Mezze Banquet (350cr)
- **Independent / Player:** Full-Burn Diner Set (200cr)

### 2.4 Officer Interactions
Players can invite a randomized human officer from their fleet to sit with them. Dialog choices expand into atmospheric conversations based on the officer's personality (*Timid, Cautious, Steady, Aggressive, Reckless*). 
- **Skill Mentoring:** Provides a lore-friendly venue for a "Wardroom Tactical Debrief", allowing players to unlearn an unwanted officer skill over a plate of hot food.

### 2.5 The 14-Day Shore Leave Buff
Dining grants a transient, minor 14-day campaign buff to simulate the crew's boosted morale and rested state.
- **Bonuses:** -5% Supply Upkeep, +5% CR Recovery Rate
- **Penalties:** +10% Sensor Profile, -5% Acceleration (The crew is well-fed and sluggish).
- **Cooldown:** 7-day digestion lockout to prevent UI spamming.

### 2.6 The Spoon Directory (Intel)
- A custom Intel Plugin located in the **Fleet Log** tab.
- Tracks all known authentic space diners (Size 4+ Vanilla markets).
- Clicking the Intel item automatically pans the Star Map to highlight the **nearest** valid diner relative to the player's current hyperspace coordinates.

---

## 3. Technical Implementation
- **Dependencies:** None (Pure Java & Rules.csv).
- **Save Compatibility:** 100% savegame compatible. Uses transient memory (`$os_shore_leave_duration`). Can be added or removed at any time.

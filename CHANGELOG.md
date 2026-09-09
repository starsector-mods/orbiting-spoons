# Changelog

All notable changes to **The Orbiting Spoon** mod for Starsector will be documented in this file.

## [1.1.0] - 2026-09-09

### Changed
- **Mod Scope & Identity Finalization:** 
  - Returned the mod to its intended roots as a lightweight, cozy roleplay and campaign-layer mod. Removed bloated logistics mechanics, exponential math traps, and "luxury" re-brandings.
- **Economic Balancing & Mod Identity (The Flat Roleplay Premium):**
  - Removed the exponential `crewCount` cost multiplier completely (`multiplier = 1`). 
  - Adjusted base menu prices to average ~200 credits (ranging from 50 to 600 credits depending on the faction). This ensures the diner remains a cheap, cozy roleplay activity with a flat cost, regardless of fleet size.
  - Retained the "Wardroom Tactical Debrief" feature, which remains perfectly balanced against vanilla by continuing to strictly require 1 Story Point and 100,000 credits to unlearn a skill.
- **Faction Restrictions:**
  - Added `OS_IsVanillaFaction` checker. The diner will now only spawn in Major Vanilla Factions (Hegemony, Tri-Tachyon, Sindrian Diktat, Luddic Church, Luddic Path, Persean League, Pirates, Independent, Player). Modded factions are gracefully excluded to preserve intended lore and menu accuracy.
- **Market Size Restrictions:**
  - The Orbiting Spoon will now only spawn on developed spaceports of **Market Size 4 or greater**.
- **The Spoon Directory & Field Guide (Intel Tab):**
  - Upgraded the Intel entry under "Fleet Log" to an interactive, 3-tab sub-window interface:
    1. *Sector Registry Table:* Left-to-right multi-column table sorted dynamically by distance to the player fleet, with glow highlights for the nearest diner and map tracking.
    2. *Sectors Unknown (Field Notes):* Full culinary reviews, spoon ratings, and cynical "Bones's Field Rules" for all 8 faction dishes, compiled by in-universe critic Anton 'Bones' Burndain (discharged Hegemony auxiliary cook and cynical void-critic).
    3. *Naval Regulations:* Clear documentation of Shore Leave buffs, sluggishness trade-offs, and Wardroom Tactical Debriefing costs.
  - Enabled full-screen sub-window expansion (`hasLargeDescription()`) for comfortable reading.

## [1.0.0] - Initial Release

- Added The Orbiting Spoon bar event.
- Added 8 faction-specific menus and atmospheric dialogs.
- Added transient 14-day Shore Leave buff (-5% Supply Upkeep, +5% CR Recovery).
- Added officer bonding and dialog branches based on the 5 vanilla personalities.
- Added Wardroom Tactical Debriefs (skill unlearning) via casual dining conversations.

# Seca-Tears

A RuneLite plugin that reminds you to equip your **Magic secateurs** before picking or harvesting crops that benefit from them.

<img width="1254" height="1254" alt="Image" src="https://github.com/user-attachments/assets/7fa6fa75-ba5a-4167-a3fe-31fe1dcb5cdf" />

## What it does

When you do **not** have Magic secateurs equipped or in your inventory:

- **Moves "Pick" or "Harvest" below "Inspect"** in farming patch menus, or below
  "Walk here" for Herbiboar, making accidental mis-clicks less likely.
- **Changes the left-click action** away from "Pick"/"Harvest", so a single click no longer immediately harvests the patch.
- **Warns you** if you still click "Pick" or "Harvest" from a supported menu, via a **chat message**
  and/or **overhead text** above your character.

When you have Magic secateurs equipped or in your inventory, all behaviour returns to normal.

This applies to crops where secateurs actually increase yield:

- Herbs (Pick)
- Limpwurt root (Pick)
- Berry bushes, including poison ivy (Pick)
- Allotments and hops (Harvest)
- Celastrus trees, grape vines, coral nurseries, and Herbiboars

It only considers real farming patch object menus, plus Herbiboar NPC menus.
Inventory, widget, player, overlay, and ground-item menus are ignored, so world
item pickups are not reordered or warned on even if their text looks similar.

It does **not** affect crops where secateurs have no effect, including most
trees, mushrooms, cacti, belladonna, seaweed, hespori, pineapple plants, and
non-limpwurt flowers.

## Why it matters

Picking or harvesting without Magic secateurs reduces your yield. This plugin makes it
nearly impossible to forget them.

## Configuration

Open the RuneLite config panel and search for **Seca-Tears**.

Chat message warning (default: on)
  Show a warning in the chatbox when you pick or harvest without secateurs.

Chat message text
  The chatbox message text. Customisable.

Overhead text warning (default: on)
  Show text above your character when you pick or harvest without secateurs.

Overhead text (default: "No Magic secateurs!")
  The overhead text. Customisable.

Overhead text duration in ticks (default: 5, ~3 s)
  How long the overhead text stays visible. 1 tick = 0.6 s. Range: 1-30.

## Changelog

1.1.0 - 2026-05-02
  - Restricted matching to farming patch object menus and Herbiboar NPC menus
  - Ignored ground items, inventory items, widgets, player menus, and RuneLite
    overlay menus even when their text looks similar
  - Improved warning handling and overhead text cleanup so only this plugin's
    own overhead warning text is cleared
  - Tightened menu text normalization and exact action/target matching to reduce
    false positives while keeping supported Magic secateurs yield sources

1.0.1 - 2026-05-01
  - Fixed overly broad Pick matching so unrelated Pick actions are no longer
    moved or warned on
  - Restricted warnings and menu reordering to explicit supported actions and
    crop targets affected by Magic secateurs
  - Added coverage for current Magic secateurs yield sources: celastrus trees,
    grape vines, coral nurseries, and Herbiboars
  - Improved menu handling performance by sharing reorder logic, reordering
    entries in-place, avoiding regex target cleanup, and caching Magic
    secateurs inventory/equipment checks until those containers change
  - Removed deprecated RuneLite client menu API usage in favour of the current
    Menu API

1.0.0 - 2026-05-01
  - Initial release
  - Moves Pick and Harvest below Inspect in the right-click menu when Magic
    secateurs are not equipped or in inventory
  - Covers herbs, limpwurt root, berry bushes (Pick-from), allotments and hops
  - Left-click action follows the same reorder so single-clicking does not
    immediately harvest
  - Chat message and overhead text warnings when picking without secateurs
  - All notifications are individually toggleable and text is customisable
  - Overhead text duration is configurable (1-30 ticks)

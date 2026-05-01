# Seca-Tears

A RuneLite plugin that reminds you to equip your **Magic secateurs** before picking or harvesting crops that benefit from them.

## What it does

When you do **not** have Magic secateurs equipped or in your inventory:

- **Moves "Pick" or "Harvest" below "Inspect"** in the right-click menu, making accidental mis-clicks less likely.
- **Changes the left-click action** away from "Pick"/"Harvest", so a single click no longer immediately harvests the patch.
- **Warns you** if you still click "Pick" or "Harvest" — via a **chat message** and/or **overhead text** above your character.

When you have Magic secateurs equipped or in your inventory, all behaviour returns to normal.

This applies to crops where secateurs actually increase yield:

- Herbs (Pick)
- Limpwurt root (Pick)
- Berry bushes (Pick)
- Allotments and hops (Harvest)

It does **not** affect crops where secateurs have no effect, including trees, mushrooms,
cacti, belladonna, seaweed, hespori, pineapple plants, and non-limpwurt flowers.

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

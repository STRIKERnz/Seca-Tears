# Seca-Tears

Seca-Tears is a plugin that helps prevent harvesting supported Farming patches without Magic secateurs equipped or in your inventory.

![Seca-Tears icon](icon.png)

## Features

- Moves supported `Pick`, `Pick-from`, and `Harvest` actions below a safer menu
  option when Magic secateurs are missing.
- Changes the default left-click action for supported patches, reducing accidental
  single-click harvests.
- Shows optional chatbox and overhead-text warnings if you still choose the
  protected action.
- Automatically returns menus to normal when Magic secateurs are equipped or in
  your inventory.

## Supported Actions

Seca-Tears only handles actions where Magic secateurs can improve yield:

- Herbs
- Limpwurt roots
- Berry bushes, including poison ivy
- Allotments
- Hops
- Celastrus trees
- Grape vines
- Coral nurseries
- Herbiboar

The plugin ignores inventory items, widgets, players, ground items, overlays, and
unrelated world menus, even when their option text also says `Pick` or `Harvest`.

## Configuration

Open the RuneLite config panel and search for `Seca-Tears`.

By default, Seca-Tears shows both a chatbox warning and overhead text when you
harvest without Magic secateurs. You can turn either warning off, customize the
message text, and set how long overhead text stays visible.

Defaults:

- Chat message warning: on
- Overhead text warning: on
- Overhead text: `No Magic secateurs!`
- Overhead text duration: 5 ticks, configurable from 1-30 ticks

## Changelog

### 1.1.0 - 2026-05-02

- Restricted menu handling to supported farming patch object menus and Herbiboar
  NPC menus.
- Ignored unrelated menus such as ground items, inventory items, widgets,
  players, and RuneLite overlays.
- Improved warning cleanup so only this plugin's overhead text is cleared.
- Tightened action and target matching to reduce false positives.
- Changed startup and shutdown logging to debug level.

### 1.0.1 - 2026-05-01

- Fixed overly broad `Pick` matching.
- Added current Magic secateurs yield sources, including celastrus trees, grape
  vines, coral nurseries, and Herbiboars.
- Improved menu handling performance and migrated to the current RuneLite menu
  API.

### 1.0.0 - 2026-05-01

- Initial release.

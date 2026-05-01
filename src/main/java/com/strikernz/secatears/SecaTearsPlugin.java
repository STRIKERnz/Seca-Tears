package com.strikernz.secatears;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@PluginDescriptor(
	name = "Seca-Tears",
	description = "Reorders the right-click menu and shows warnings when picking or harvesting without Magic secateurs",
	tags = {"farming", "herbs", "secateurs", "magic secateurs", "right-click menu", "overhead text", "chat message"}
)
public class SecaTearsPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private SecaTearsConfig config;

	private int overheadTicksRemaining = 0;

	@Override
	protected void startUp()
	{
		log.info("Seca-Tears plugin started");
	}

	@Override
	protected void shutDown()
	{
		overheadTicksRemaining = 0;
		if (client.getLocalPlayer() != null)
		{
			client.getLocalPlayer().setOverheadText(null);
		}
		log.debug("Seca-Tears plugin stopped");
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (hasMagicSecateurs())
		{
			return;
		}

		MenuEntry[] entries = event.getMenuEntries();
		if (entries == null || entries.length < 2)
		{
			return;
		}

		int actionIndex = -1;
		int inspectIndex = -1;

		for (int i = 0; i < entries.length; i++)
		{
			MenuEntry e = entries[i];
			if (e == null) continue;
			String opt = e.getOption();
			if (opt == null) continue;
			String ol = opt.toLowerCase();

			if (shouldReorder(ol, normalizeTarget(e.getTarget()))) actionIndex = i;
			else if (ol.contains("inspect")) inspectIndex = i;
		}

		if (actionIndex == -1 || inspectIndex == -1 || actionIndex <= inspectIndex)
		{
			return;
		}

		List<MenuEntry> list = new ArrayList<>(Arrays.asList(entries));
		MenuEntry actionEntry = list.remove(actionIndex);
		list.add(inspectIndex, actionEntry);
		event.setMenuEntries(list.toArray(new MenuEntry[0]));
		log.debug("Moved '{}' below 'Inspect'", actionEntry.getOption());
	}

	@Subscribe
	@SuppressWarnings("deprecation")
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (hasMagicSecateurs())
		{
			return;
		}

		if (!shouldReorder(event.getOption().toLowerCase(), normalizeTarget(event.getTarget())))
		{
			return;
		}

		MenuEntry[] entries = client.getMenuEntries();
		if (entries == null || entries.length < 2)
		{
			return;
		}

		int actionIndex = -1;
		int inspectIndex = -1;

		for (int i = 0; i < entries.length; i++)
		{
			MenuEntry e = entries[i];
			if (e == null) continue;
			String opt = e.getOption();
			if (opt == null) continue;
			String ol = opt.toLowerCase();
			if (shouldReorder(ol, normalizeTarget(e.getTarget()))) actionIndex = i;
			else if (ol.contains("inspect")) inspectIndex = i;
		}

		if (actionIndex == -1 || inspectIndex == -1 || actionIndex <= inspectIndex)
		{
			return;
		}

		List<MenuEntry> list = new ArrayList<>(Arrays.asList(entries));
		MenuEntry actionEntry = list.remove(actionIndex);
		list.add(inspectIndex, actionEntry);
		client.setMenuEntries(list.toArray(new MenuEntry[0]));
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (hasMagicSecateurs())
		{
			return;
		}

		String option = event.getMenuOption();
		String target = event.getMenuTarget();

		if (option == null || target == null)
		{
			return;
		}

		if (!shouldReorder(option.toLowerCase(), normalizeTarget(target)))
		{
			return;
		}

		if (config.showChatMessage())
		{
			client.addChatMessage(
				ChatMessageType.GAMEMESSAGE,
				"",
				config.chatMessage(),
				null
			);
		}

		if (config.showOverheadText() && client.getLocalPlayer() != null)
		{
			client.getLocalPlayer().setOverheadText(config.overheadText());
			overheadTicksRemaining = config.overheadTextDuration();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (overheadTicksRemaining > 0)
		{
			overheadTicksRemaining--;
			if (overheadTicksRemaining == 0 && client.getLocalPlayer() != null)
			{
				client.getLocalPlayer().setOverheadText(null);
			}
		}
	}

	/**
	 * Returns true if the menu action/target combination is one where
	 * Magic secateurs increase yield and the menu should be reordered.
	 *
	 * Pick: reorder unless the target is a known non-benefiting patch
	 *   (seaweed, cactus). Herbs, limpwurt root, and berry bushes all pass.
	 *   Other random "Pick" world objects without an Inspect entry are safe
	 *   because the reorder only fires when both Pick and Inspect are present.
	 *
	 * Harvest: reorder unless the target is a known non-benefiting crop:
	 *   - Any tree (fruit trees, calquat, crystal tree)
	 *   - Mushrooms, belladonna, hespori, pineapple, seaweed
	 *   - Non-limpwurt flowers (marigold, rosemary, nasturtium, woad)
	 */
	private static boolean shouldReorder(String optionLower, String normalizedTarget)
	{
		if (optionLower.startsWith("pick") && !optionLower.startsWith("pickpocket"))
		{
			return !isPickExcluded(normalizedTarget);
		}

		if (optionLower.contains("harvest"))
		{
			return !isHarvestExcluded(normalizedTarget);
		}

		return false;
	}

	private static boolean isPickExcluded(String t)
	{
		return t.contains("seaweed")
			|| t.contains("cactus");
	}

	private static boolean isHarvestExcluded(String t)
	{
		return t.contains("tree")
			|| t.contains("mushroom")
			|| t.contains("cactus")
			|| t.contains("belladonna")
			|| t.contains("hespori")
			|| t.contains("pineapple")
			|| t.contains("seaweed")
			|| t.contains("marigold")
			|| t.contains("rosemary")
			|| t.contains("nasturtium")
			|| t.contains("woad");
	}

	private static String normalizeTarget(String t)
	{
		if (t == null) return "";
		return t.replaceAll("<[^>]+>", "").toLowerCase().trim();
	}

	private boolean hasMagicSecateurs()
	{
		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		if (equipment != null)
		{
			for (net.runelite.api.Item item : equipment.getItems())
			{
				if (item != null && item.getId() == ItemID.FAIRY_ENCHANTED_SECATEURS)
				{
					return true;
				}
			}
		}

		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory != null)
		{
			for (net.runelite.api.Item item : inventory.getItems())
			{
				if (item != null && item.getId() == ItemID.FAIRY_ENCHANTED_SECATEURS)
				{
					return true;
				}
			}
		}

		return false;
	}

	@Provides
	SecaTearsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SecaTearsConfig.class);
	}
}


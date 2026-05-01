package com.strikernz.secatears;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Seca-Tears",
	description = "Reorders the right-click menu and shows warnings when picking or harvesting without Magic secateurs",
	tags = {"farming", "herbs", "secateurs", "magic secateurs", "right-click menu", "overhead text", "chat message"}
)
public class SecaTearsPlugin extends Plugin
{
	private static final String PICK = "pick";
	private static final String PICKPOCKET = "pickpocket";
	private static final String HARVEST = "harvest";
	private static final String INSPECT = "inspect";

	private static final String[] SECATEURS_PICK_TARGETS = {
		"herb",
		"limpwurt",
		"berry",
		"poison ivy",
		"grapes"
	};

	private static final String[] SECATEURS_HARVEST_TARGETS = {
		"allotment",
		"potato",
		"onion",
		"cabbage",
		"tomato",
		"sweetcorn",
		"strawberry",
		"watermelon",
		"snape grass",
		"hops",
		"barley",
		"hammerstone",
		"asgarnian",
		"jute",
		"yanillian",
		"krandorian",
		"wildblood",
		"celastrus",
		"grapes",
		"coral nursery",
		"herbiboar"
	};

	@Inject
	private Client client;

	@Inject
	private SecaTearsConfig config;

	private int overheadTicksRemaining = 0;
	private Boolean hasMagicSecateurs;

	@Override
	protected void startUp()
	{
		hasMagicSecateurs = null;
		log.info("Seca-Tears plugin started");
	}

	@Override
	protected void shutDown()
	{
		overheadTicksRemaining = 0;
		hasMagicSecateurs = null;
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
		MenuEntry movedEntry = reorderMenuEntries(entries);
		if (movedEntry == null)
		{
			return;
		}

		event.setMenuEntries(entries);
		log.debug("Moved '{}' below 'Inspect'", movedEntry.getOption());
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (hasMagicSecateurs())
		{
			return;
		}

		if (!isSecateursAction(event.getOption(), event.getTarget()))
		{
			return;
		}

		Menu menu = client.getMenu();
		MenuEntry[] entries = menu.getMenuEntries();
		if (reorderMenuEntries(entries) == null)
		{
			return;
		}

		menu.setMenuEntries(entries);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (hasMagicSecateurs())
		{
			return;
		}

		if (!isSecateursAction(event.getMenuOption(), event.getMenuTarget()))
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

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int containerId = event.getContainerId();
		if (containerId == InventoryID.WORN || containerId == InventoryID.INV)
		{
			hasMagicSecateurs = null;
		}
	}

	/**
	 * Reorders menu entries when the action/target combination is one where
	 * Magic secateurs increase yield.
	 *
	 * Pick: herbs, limpwurt, bushes, and grape vines.
	 *
	 * Harvest: allotments, hops, celastrus, grape vines, coral nurseries,
	 * and Herbiboars.
	 */
	private static MenuEntry reorderMenuEntries(MenuEntry[] entries)
	{
		if (entries == null || entries.length < 2)
		{
			return null;
		}

		int actionIndex = -1;
		int inspectIndex = -1;

		for (int i = 0; i < entries.length; i++)
		{
			MenuEntry entry = entries[i];
			if (entry == null)
			{
				continue;
			}

			String option = entry.getOption();
			if (option == null)
			{
				continue;
			}

			if (isSecateursAction(option, entry.getTarget()))
			{
				actionIndex = i;
			}
			else if (option.equalsIgnoreCase(INSPECT))
			{
				inspectIndex = i;
			}
		}

		if (actionIndex == -1 || inspectIndex == -1 || actionIndex <= inspectIndex)
		{
			return null;
		}

		MenuEntry actionEntry = entries[actionIndex];
		System.arraycopy(entries, inspectIndex, entries, inspectIndex + 1, actionIndex - inspectIndex);
		entries[inspectIndex] = actionEntry;
		return actionEntry;
	}

	private static boolean isSecateursAction(String option, String target)
	{
		if (option == null || target == null)
		{
			return false;
		}

		String optionLower = option.toLowerCase();

		if (optionLower.startsWith(PICK) && !optionLower.startsWith(PICKPOCKET))
		{
			return containsAny(normalizeTarget(target), SECATEURS_PICK_TARGETS);
		}

		if (optionLower.contains(HARVEST))
		{
			return containsAny(normalizeTarget(target), SECATEURS_HARVEST_TARGETS);
		}

		return false;
	}

	private static boolean containsAny(String text, String[] values)
	{
		for (String value : values)
		{
			if (text.contains(value))
			{
				return true;
			}
		}
		return false;
	}

	private static String normalizeTarget(String t)
	{
		StringBuilder normalized = new StringBuilder(t.length());
		boolean inTag = false;
		for (int i = 0; i < t.length(); i++)
		{
			char c = t.charAt(i);
			if (c == '<')
			{
				inTag = true;
			}
			else if (c == '>')
			{
				inTag = false;
			}
			else if (!inTag)
			{
				normalized.append(Character.toLowerCase(c));
			}
		}
		return normalized.toString().trim();
	}

	private boolean hasMagicSecateurs()
	{
		if (hasMagicSecateurs != null)
		{
			return hasMagicSecateurs;
		}

		hasMagicSecateurs = hasMagicSecateursUncached();
		return hasMagicSecateurs;
	}

	private boolean hasMagicSecateursUncached()
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

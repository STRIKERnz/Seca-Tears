package com.strikernz.secatears;

import com.google.inject.Provides;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
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
	private static final String PICK_FROM = "pick-from";
	private static final String HARVEST = "harvest";
	private static final String INSPECT = "inspect";
	private static final String WALK_HERE = "walk here";
	private static final String HERBIBOAR = "herbiboar";
	private static final int NO_MENU_ENTRY = -1;

	private static final Set<String> SECATEURS_PICK_OPTIONS = Set.of(
		PICK,
		PICK_FROM
	);

	private static final Set<String> SECATEURS_PICK_TARGETS = Set.of(
		"herb",
		"herbs",
		"limpwurt",
		"limpwurt root",
		"berry",
		"redberry bush",
		"cadavaberry bush",
		"dwellberry bush",
		"jangerberry bush",
		"whiteberry bush",
		"poison ivy",
		"poison ivy bush",
		"grapes"
	);

	private static final Set<String> SECATEURS_HARVEST_TARGETS = Set.of(
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
		"hammerstone hops",
		"asgarnian hops",
		"jute",
		"jute plant",
		"yanillian hops",
		"krandorian hops",
		"wildblood hops",
		"celastrus",
		"celastrus tree",
		"grapevine",
		"grapes",
		"coral nursery",
		"herbiboar"
	);

	@Inject
	private Client client;

	@Inject
	private SecaTearsConfig config;

	private int overheadTicksRemaining = 0;
	private String activeOverheadText;
	private Boolean hasMagicSecateurs;

	@Override
	protected void startUp()
	{
		hasMagicSecateurs = null;
		log.debug("Seca-Tears plugin started");
	}

	@Override
	protected void shutDown()
	{
		clearOverheadText();
		hasMagicSecateurs = null;
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
		log.debug("Moved '{}' below safety entry", movedEntry.getOption());
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (hasMagicSecateurs())
		{
			return;
		}

		if (!isSecateursAction(event.getMenuEntry()))
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

		if (!isSecateursAction(event.getMenuEntry()))
		{
			return;
		}

		showWarning();
		log.debug("Warned for '{}' on '{}' without Magic secateurs", event.getMenuOption(), event.getMenuTarget());
	}

	private void showWarning()
	{
		if (config.showChatMessage())
		{
			client.addChatMessage(
				ChatMessageType.GAMEMESSAGE,
				"",
				config.chatMessage(),
				null
			);
		}

		Player localPlayer = client.getLocalPlayer();
		if (config.showOverheadText() && localPlayer != null)
		{
			activeOverheadText = config.overheadText();
			localPlayer.setOverheadText(activeOverheadText);
			overheadTicksRemaining = config.overheadTextDuration();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (overheadTicksRemaining > 0)
		{
			overheadTicksRemaining--;
			if (overheadTicksRemaining == 0)
			{
				clearOverheadText();
			}
		}
	}

	private void clearOverheadText()
	{
		overheadTicksRemaining = 0;
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer != null && activeOverheadText != null
			&& activeOverheadText.equals(localPlayer.getOverheadText()))
		{
			localPlayer.setOverheadText(null);
		}
		activeOverheadText = null;
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
	static MenuEntry reorderMenuEntries(MenuEntry[] entries)
	{
		if (entries == null || entries.length < 2)
		{
			return null;
		}

		int actionIndex = findProtectedActionIndex(entries);
		if (actionIndex == NO_MENU_ENTRY)
		{
			return null;
		}

		String normalizedTarget = normalizeMenuText(entries[actionIndex].getTarget());
		int anchorIndex = findOptionIndex(requiredSafetyAnchor(normalizedTarget), entries);
		if (anchorIndex == NO_MENU_ENTRY || actionIndex <= anchorIndex)
		{
			return null;
		}

		MenuEntry movedEntry = entries[actionIndex];
		System.arraycopy(entries, anchorIndex, entries, anchorIndex + 1, actionIndex - anchorIndex);
		entries[anchorIndex] = movedEntry;
		return movedEntry;
	}

	private static int findProtectedActionIndex(MenuEntry[] entries)
	{
		int actionIndex = NO_MENU_ENTRY;
		for (int i = 0; i < entries.length; i++)
		{
			MenuEntry entry = entries[i];
			if (entry == null || !isSecateursAction(entry))
			{
				continue;
			}

			actionIndex = i;
		}

		return actionIndex;
	}

	private static int findOptionIndex(String option, MenuEntry[] entries)
	{
		if (option == null)
		{
			return NO_MENU_ENTRY;
		}

		for (int i = 0; i < entries.length; i++)
		{
			MenuEntry entry = entries[i];
			if (entry != null && option.equals(normalizeMenuText(entry.getOption())))
			{
				return i;
			}
		}

		return NO_MENU_ENTRY;
	}

	private static String requiredSafetyAnchor(String normalizedTarget)
	{
		if (normalizedTarget == null)
		{
			return null;
		}

		if (HERBIBOAR.equals(normalizedTarget))
		{
			return WALK_HERE;
		}

		return INSPECT;
	}

	static boolean isSecateursAction(MenuEntry entry)
	{
		if (entry == null)
		{
			return false;
		}

		return isSecateursAction(entry.getType(), entry.getOption(), entry.getTarget());
	}

	static boolean isSecateursAction(String option, String target)
	{
		if (option == null || target == null)
		{
			return false;
		}

		String normalizedOption = normalizeMenuText(option);
		String normalizedTarget = normalizeMenuText(target);
		return isSecateursActionNormalized(normalizedOption, normalizedTarget);
	}

	private static boolean isSecateursAction(MenuAction action, String option, String target)
	{
		if (option == null || target == null)
		{
			return false;
		}

		String normalizedTarget = normalizeMenuText(target);
		if (!isProtectedMenuActionNormalized(action, normalizedTarget))
		{
			return false;
		}

		return isSecateursActionNormalized(normalizeMenuText(option), normalizedTarget);
	}

	private static boolean isSecateursActionNormalized(String normalizedOption, String normalizedTarget)
	{
		if (SECATEURS_PICK_OPTIONS.contains(normalizedOption))
		{
			return SECATEURS_PICK_TARGETS.contains(normalizedTarget);
		}

		if (HARVEST.equals(normalizedOption))
		{
			return SECATEURS_HARVEST_TARGETS.contains(normalizedTarget);
		}

		return false;
	}

	static boolean isProtectedMenuAction(MenuAction action, String target)
	{
		return isProtectedMenuActionNormalized(action, normalizeMenuText(target));
	}

	private static boolean isProtectedMenuActionNormalized(MenuAction action, String normalizedTarget)
	{
		if (action == null)
		{
			return false;
		}

		switch (action)
		{
			case GAME_OBJECT_FIRST_OPTION:
			case GAME_OBJECT_SECOND_OPTION:
			case GAME_OBJECT_THIRD_OPTION:
			case GAME_OBJECT_FOURTH_OPTION:
			case GAME_OBJECT_FIFTH_OPTION:
				return true;
			case NPC_FIRST_OPTION:
			case NPC_SECOND_OPTION:
			case NPC_THIRD_OPTION:
			case NPC_FOURTH_OPTION:
			case NPC_FIFTH_OPTION:
				return HERBIBOAR.equals(normalizedTarget);
			default:
				return false;
		}
	}

	static String normalizeMenuText(String text)
	{
		if (text == null)
		{
			return "";
		}

		StringBuilder normalized = new StringBuilder(text.length());
		boolean inTag = false;
		boolean lastWasWhitespace = true;
		for (int i = 0; i < text.length(); i++)
		{
			char c = text.charAt(i);
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
				if (Character.isWhitespace(c))
				{
					if (!lastWasWhitespace)
					{
						normalized.append(' ');
						lastWasWhitespace = true;
					}
				}
				else
				{
					normalized.append(Character.toLowerCase(c));
					lastWasWhitespace = false;
				}
			}
		}
		int length = normalized.length();
		if (length > 0 && normalized.charAt(length - 1) == ' ')
		{
			normalized.setLength(length - 1);
		}
		return normalized.toString();
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
		return containsMagicSecateurs(client.getItemContainer(InventoryID.WORN))
			|| containsMagicSecateurs(client.getItemContainer(InventoryID.INV));
	}

	private static boolean containsMagicSecateurs(ItemContainer container)
	{
		if (container == null)
		{
			return false;
		}

		for (Item item : container.getItems())
		{
			if (item != null && item.getId() == ItemID.FAIRY_ENCHANTED_SECATEURS)
			{
				return true;
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

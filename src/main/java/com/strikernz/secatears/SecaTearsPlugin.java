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
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
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
	private static final String EXAMINE = "examine";
	private static final String HERBIBOAR = "herbiboar";
	private static final int NO_MENU_ENTRY = -1;

	private static final Set<String> SECATEURS_PICK_OPTIONS = Set.of(
		PICK,
		PICK_FROM
	);

	private static final Set<String> SECATEURS_PICK_TARGETS = Set.of(
		"herb",
		"herbs",
		"guam",
		"guam leaf",
		"marrentill",
		"tarromin",
		"harralander",
		"ranarr",
		"ranarr weed",
		"toadflax",
		"irit",
		"irit leaf",
		"avantoe",
		"kwuarm",
		"snapdragon",
		"cadantine",
		"lantadyme",
		"dwarf weed",
		"torstol",
		"huasca",
		"limpwurt",
		"limpwurt plant",
		"limpwurt root",
		"berry",
		"berries",
		"redberry bush",
		"cadavaberry bush",
		"dwellberry bush",
		"jangerberry bush",
		"whiteberry bush",
		"whiteberries",
		"white berries",
		"poison ivy",
		"poison ivy bush",
		"snape grass",
		"snape grass plant",
		"grapes"
	);

	private static final Set<String> SECATEURS_BUSH_PICK_TARGETS = Set.of(
		"berry",
		"redberry bush",
		"cadavaberry bush",
		"dwellberry bush",
		"jangerberry bush",
		"whiteberry bush",
		"whiteberries",
		"white berries",
		"poison ivy",
		"poison ivy bush"
	);

	private static final Set<Integer> SECATEURS_BUSH_OBJECT_IDS = Set.of(
		ObjectID.REDBERRY_BUSH_FULLYGROWN,
		ObjectID.REDBERRY_BUSH_BERRY_1,
		ObjectID.REDBERRY_BUSH_BERRY_2,
		ObjectID.REDBERRY_BUSH_BERRY_3,
		ObjectID.REDBERRY_BUSH_BERRY_4,
		ObjectID.CADAVABERRY_BUSH_FULLYGROWN,
		ObjectID.CADAVABERRY_BUSH_BERRY_1,
		ObjectID.CADAVABERRY_BUSH_BERRY_2,
		ObjectID.CADAVABERRY_BUSH_BERRY_3,
		ObjectID.CADAVABERRY_BUSH_BERRY_4,
		ObjectID.DWELLBERRY_BUSH_FULLYGROWN,
		ObjectID.DWELLBERRY_BUSH_BERRY_1,
		ObjectID.DWELLBERRY_BUSH_BERRY_2,
		ObjectID.DWELLBERRY_BUSH_BERRY_3,
		ObjectID.DWELLBERRY_BUSH_BERRY_4,
		ObjectID.JANGERBERRY_BUSH_FULLYGROWN,
		ObjectID.JANGERBERRY_BUSH_BERRY_1,
		ObjectID.JANGERBERRY_BUSH_BERRY_2,
		ObjectID.JANGERBERRY_BUSH_BERRY_3,
		ObjectID.JANGERBERRY_BUSH_BERRY_4,
		ObjectID.WHITEBERRY_BUSH_FULLYGROWN,
		ObjectID.WHITEBERRY_BUSH_BERRY_1,
		ObjectID.WHITEBERRY_BUSH_BERRY_2,
		ObjectID.WHITEBERRY_BUSH_BERRY_3,
		ObjectID.WHITEBERRY_BUSH_BERRY_4,
		ObjectID.POISONIVY_BUSH_FULLYGROWN,
		ObjectID.POISONIVY_BUSH_BERRY_1,
		ObjectID.POISONIVY_BUSH_BERRY_2,
		ObjectID.POISONIVY_BUSH_BERRY_3,
		ObjectID.POISONIVY_BUSH_BERRY_4
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
		"snape grass plant",
		"snape grass patch",
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
		if (reorderMenuEntries(entries))
		{
			event.setMenuEntries(entries);
		}
	}

	@Subscribe(priority = -1.0f)
	public void onPostMenuSort(PostMenuSort event)
	{
		if (hasMagicSecateurs())
		{
			return;
		}

		Menu menu = client.getMenu();
		MenuEntry[] entries = menu.getMenuEntries();
		if (reorderMenuEntries(entries))
		{
			menu.setMenuEntries(entries);
		}
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

	private boolean isSecateursAction(MenuEntry entry)
	{
		if (entry == null)
		{
			return false;
		}

		String normalizedTarget = normalizeMenuText(entry.getTarget());
		if (!isProtectedMenuActionNormalized(entry.getType(), normalizedTarget))
		{
			return false;
		}

		String normalizedOption = normalizeMenuText(entry.getOption());
		if (!isSecateursActionNormalized(normalizedOption, normalizedTarget))
		{
			return false;
		}

		return !requiresFarmingBushObjectId(normalizedOption, normalizedTarget)
			|| SECATEURS_BUSH_OBJECT_IDS.contains(resolveObjectId(entry));
	}

	private boolean reorderMenuEntries(MenuEntry[] entries)
	{
		if (entries == null || entries.length < 2)
		{
			return false;
		}

		boolean changed = false;
		for (int actionIndex = entries.length - 1; actionIndex >= 0; actionIndex--)
		{
			MenuEntry entry = entries[actionIndex];
			if (!isSecateursAction(entry))
			{
				continue;
			}

			int anchorIndex = findSafetyAnchorIndex(entries, actionIndex);
			if (anchorIndex == NO_MENU_ENTRY)
			{
				continue;
			}

			changed |= moveActionBehindAnchor(entries, actionIndex, anchorIndex);
		}

		return changed;
	}

	private static boolean moveActionBehindAnchor(MenuEntry[] entries, int actionIndex, int anchorIndex)
	{
		if (actionIndex == anchorIndex - 1)
		{
			return false;
		}

		MenuEntry movedEntry = entries[actionIndex];
		if (actionIndex > anchorIndex)
		{
			System.arraycopy(entries, anchorIndex, entries, anchorIndex + 1, actionIndex - anchorIndex);
			entries[anchorIndex] = movedEntry;
			return true;
		}

		System.arraycopy(entries, actionIndex + 1, entries, actionIndex, anchorIndex - actionIndex - 1);
		entries[anchorIndex - 1] = movedEntry;
		return true;
	}

	private static int findSafetyAnchorIndex(MenuEntry[] entries, int actionIndex)
	{
		String target = normalizeMenuText(entries[actionIndex].getTarget());
		int examineIndex = NO_MENU_ENTRY;
		for (int i = 0; i < entries.length; i++)
		{
			MenuEntry entry = entries[i];
			if (i == actionIndex || entry == null || !target.equals(normalizeMenuText(entry.getTarget())))
			{
				continue;
			}

			String option = normalizeMenuText(entry.getOption());
			if (INSPECT.equals(option))
			{
				return i;
			}

			if (EXAMINE.equals(option))
			{
				examineIndex = i;
			}
		}

		return examineIndex;
	}

	static boolean isSecateursActionNormalized(String normalizedOption, String normalizedTarget)
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

	private static boolean requiresFarmingBushObjectId(String normalizedOption, String normalizedTarget)
	{
		return SECATEURS_PICK_OPTIONS.contains(normalizedOption)
			&& SECATEURS_BUSH_PICK_TARGETS.contains(normalizedTarget);
	}

	private int resolveObjectId(MenuEntry entry)
	{
		if (entry == null || !isObjectMenuAction(entry.getType()))
		{
			return NO_MENU_ENTRY;
		}

		int objectId = entry.getIdentifier();
		ObjectComposition objectComposition = client.getObjectDefinition(objectId);
		if (objectComposition != null && objectComposition.getImpostorIds() != null)
		{
			objectComposition = objectComposition.getImpostor();
			if (objectComposition != null)
			{
				objectId = objectComposition.getId();
			}
		}

		return objectId;
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

	private static boolean isObjectMenuAction(MenuAction action)
	{
		switch (action)
		{
			case GAME_OBJECT_FIRST_OPTION:
			case GAME_OBJECT_SECOND_OPTION:
			case GAME_OBJECT_THIRD_OPTION:
			case GAME_OBJECT_FOURTH_OPTION:
			case GAME_OBJECT_FIFTH_OPTION:
				return true;
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

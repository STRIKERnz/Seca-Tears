package com.strikernz.secatears;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.ObjectID1;
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
	 * Harvest: allotments, hops, celastrus, grape vines, coral nurseries, and
	 * Herbiboars.
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

			if (isSecateursAction(entry))
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

	private static boolean isSecateursAction(MenuEntry entry)
	{
		if (entry == null)
		{
			return false;
		}

		String option = entry.getOption();
		if (option == null)
		{
			return false;
		}

		String optionLower = option.toLowerCase();
		if (optionLower.startsWith(PICKPOCKET))
		{
			return false;
		}

		MenuAction type = entry.getType();
		if (isGameObjectAction(type))
		{
			return isSecateursPatchAction(optionLower, entry.getIdentifier());
		}

		if (isNpcAction(type))
		{
			return optionLower.contains(HARVEST) && isHerbiboar(entry.getNpc());
		}

		return false;
	}

	private static boolean isSecateursPatchAction(String optionLower, int objectId)
	{
		if (optionLower.startsWith(PICK))
		{
			return isSecateursPickObject(objectId);
		}

		if (optionLower.contains(HARVEST))
		{
			return isSecateursHarvestObject(objectId);
		}

		return false;
	}

	private static boolean isGameObjectAction(MenuAction type)
	{
		return type == MenuAction.GAME_OBJECT_FIRST_OPTION
			|| type == MenuAction.GAME_OBJECT_SECOND_OPTION
			|| type == MenuAction.GAME_OBJECT_THIRD_OPTION
			|| type == MenuAction.GAME_OBJECT_FOURTH_OPTION
			|| type == MenuAction.GAME_OBJECT_FIFTH_OPTION;
	}

	private static boolean isNpcAction(MenuAction type)
	{
		return type == MenuAction.NPC_FIRST_OPTION
			|| type == MenuAction.NPC_SECOND_OPTION
			|| type == MenuAction.NPC_THIRD_OPTION
			|| type == MenuAction.NPC_FOURTH_OPTION
			|| type == MenuAction.NPC_FIFTH_OPTION;
	}

	private static boolean isSecateursPickObject(int objectId)
	{
		return isBushObject(objectId)
			|| isLimpwurtObject(objectId)
			|| isHerbObject(objectId)
			|| isGrapeVineObject(objectId);
	}

	private static boolean isSecateursHarvestObject(int objectId)
	{
		return isAllotmentObject(objectId)
			|| isHopsObject(objectId)
			|| isCelastrusObject(objectId)
			|| isGrapeVineObject(objectId)
			|| isCoralNurseryObject(objectId);
	}

	private static boolean isBushObject(int objectId)
	{
		return between(objectId, ObjectID.CADAVABERRY_BUSH_SEEDLING, ObjectID.WHITEBERRY_BUSH_FULLYGROWN_DEAD);
	}

	private static boolean isLimpwurtObject(int objectId)
	{
		return between(objectId, ObjectID.LIMPWURT_SEED, ObjectID.LIMPWURT_FULLYGROWN_DEAD);
	}

	private static boolean isHerbObject(int objectId)
	{
		return between(objectId, ObjectID.FARMING_HERB_PATCH_1, ObjectID.FARMING_HERB_PATCH_5)
			|| objectId == ObjectID.FARMING_HERB_PATCH_6
			|| between(objectId, ObjectID.HERB_GUAM_LEAF_1, ObjectID.HERB_GUAM_LEAF_FULLYGROWN)
			|| between(objectId, ObjectID.MYARM_REALPATCH_HERB1_ACTIVE, ObjectID.MYARM_REALPATCH_HERB5_ACTIVE)
			|| objectId == ObjectID1.FARMING_HERB_PATCH_7
			|| objectId == ObjectID1.FARMING_HERB_PATCH_8
			|| between(objectId, ObjectID1.HERB_MARRENTILL_SEED, ObjectID1.HERB_SNAPDRAGON_FULLYGROWN)
			|| between(objectId, ObjectID1.MYARM_REALPATCH_HERB1_HUASCA_ACTIVE, ObjectID1.MYARM_REALPATCH_HERB5_HUASCA_ACTIVE)
			|| between(objectId, ObjectID1.HERB_HUASCA_SEED, ObjectID1.HERB_HUASCA_FULLYGROWN)
			|| between(objectId, ObjectID1.SOTE_HERB_SEED, ObjectID1.SOTE_HERB_3)
			|| objectId == ObjectID1.SOTE_HERB_PATCH
			|| objectId == ObjectID1.WGS_RICH_SNAPDRAGON_PATCH
			|| objectId == ObjectID1.WGS_RICH_SNAPDRAGON_PATCH_WEEDED
			|| between(objectId, ObjectID1.WGS_RICH_SNAPDRAGON_HERB_SEED, ObjectID1.WGS_RICH_SNAPDRAGON_HERB_FULLYGROWN);
	}

	private static boolean isAllotmentObject(int objectId)
	{
		return between(objectId, ObjectID.FARMING_VEG_PATCH_1, ObjectID.FARMING_VEG_PATCH_11)
			|| between(objectId, ObjectID.CABBAGE_SEED, ObjectID.WATERMELON_7_DEAD)
			|| between(objectId, ObjectID1.SNAPEGRASS_SEEDLING, ObjectID1.SNAPEGRASS_6_DEAD)
			|| objectId == ObjectID1.FARMING_VEG_PATCH_12
			|| objectId == ObjectID1.FARMING_VEG_PATCH_13
			|| objectId == ObjectID1.FARMING_VEG_PATCH_14
			|| objectId == ObjectID1.FARMING_VEG_PATCH_15
			|| objectId == ObjectID1.FARMING_VEG_PATCH_16
			|| objectId == ObjectID1.FARMING_VEG_PATCH_17;
	}

	private static boolean isHopsObject(int objectId)
	{
		return between(objectId, ObjectID.ASGARNIAN_HOPS_SEED, ObjectID.YANILLIAN_HOPS_5_DEAD)
			|| objectId == ObjectID.FARMING_HOPS_PATCH_KELDAGRIM
			|| objectId == ObjectID1.FARMING_HOPS_PATCH_5;
	}

	private static boolean isCelastrusObject(int objectId)
	{
		return between(objectId, ObjectID1.CELASTRUS_PATCH_WEEDED, ObjectID1.CELASTRUS_TREE_STUMP)
			|| objectId == ObjectID1.FARMING_CELASTRUS_PATCH_1;
	}

	private static boolean isGrapeVineObject(int objectId)
	{
		return between(objectId, ObjectID.GRAPEVINE_GROWING_5, ObjectID.GRAPEVINE_CLICKZONE_DEAD)
			|| between(objectId, ObjectID.GRAPEVINE_PATCH_CLICKZONE_01, ObjectID.GRAPEVINE_PATCH_CLICKZONE_08)
			|| objectId == ObjectID.GRAPEVINE_PATCH_CLICKZONE_09
			|| between(objectId, ObjectID.GRAPEVINE_PATCH_CLICKZONE_10, ObjectID.GRAPEVINE_PATCH_CLICKZONE_12)
			|| between(objectId, ObjectID.GRAPEVINE_PATCH_01, ObjectID.GRAPEVINE_PATCH_04)
			|| between(objectId, ObjectID.GRAPEVINE_PATCH_05, ObjectID.GRAPEVINE_PATCH_12);
	}

	private static boolean isCoralNurseryObject(int objectId)
	{
		return between(objectId, ObjectID1.CORAL_PATCH_EMPTY, ObjectID1.FARMING_CORAL_PATCH_2);
	}

	private static boolean isHerbiboar(NPC npc)
	{
		if (npc == null)
		{
			return false;
		}

		int npcId = npc.getId();
		return between(npcId, NpcID.FOSSIL_HERBIBOAR_VISIBLE, NpcID.FOSSIL_HERBIBOAR_ANIM_VISIBLE)
			|| between(npcId, NpcID.FOSSIL_HERBIBOAR_1, NpcID.FOSSIL_HERBIBOAR_ANIMATE_9);
	}

	private static boolean between(int value, int min, int max)
	{
		return value >= min && value <= max;
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

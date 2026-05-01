package com.strikernz.secatears;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("seca-tears")
public interface SecaTearsConfig extends Config
{
	@ConfigItem(
		keyName = "showChatMessage",
		name = "Chat message warning",
		description = "Show a chat message when picking or harvesting without Magic secateurs"
	)
	default boolean showChatMessage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chatMessage",
		name = "Chat message text",
		description = "Message shown in chat when picking or harvesting without Magic secateurs"
	)
	default String chatMessage()
	{
		return "You are picking or harvesting without Magic secateurs equipped or in your inventory!";
	}

	@ConfigItem(
		keyName = "showOverheadText",
		name = "Overhead text warning",
		description = "Show overhead text when picking or harvesting without Magic secateurs"
	)
	default boolean showOverheadText()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overheadText",
		name = "Overhead text",
		description = "Text shown above your character when picking or harvesting without Magic secateurs"
	)
	default String overheadText()
	{
		return "No Magic secateurs!";
	}

	@Range(min = 1, max = 30)
	@ConfigItem(
		keyName = "overheadTextDuration",
		name = "Overhead text duration",
		description = "How many game ticks to show the overhead text (1 tick ≈ 0.6s, default 5 ≈ 3s)"
	)
	default int overheadTextDuration()
	{
		return 5;
	}
}

package com.inspect;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("inspect")
public interface InspectConfig extends Config
{
	String THIRD_PARTY_WARNING = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers";
	String WIKI_FEATURES = "wikiFeatures";

	@ConfigSection(
		position = 1,
		name = "Wiki-backed features",
		description = "Features available after OSRS Wiki lookups are enabled.",
		closedByDefault = true
	)
	String wikiFeatures = WIKI_FEATURES;

	@ConfigItem(
		position = 0,
		keyName = "enableWikiLookups",
		name = "Enable OSRS Wiki lookups",
		description = "Allows Inspect to fetch item and NPC information from the OSRS Wiki.",
		warning = THIRD_PARTY_WARNING
	)
	default boolean enableWikiLookups()
	{
		return false;
	}

	@ConfigItem(
		position = 2,
		keyName = "showPlayerEquipmentInspectOption",
		name = "Player equipment inspect",
		description = "Adds an Inspect menu option to players."
	)
	default boolean showPlayerEquipmentInspectOption()
	{
		return true;
	}

	@ConfigItem(
		position = 3,
		keyName = "showNpcInspectOption",
		name = "NPC Inspect",
		description = "Adds an Inspect menu option to NPCs. Requires OSRS Wiki lookups.",
		section = WIKI_FEATURES
	)
	default boolean showNpcInspectOption()
	{
		return true;
	}

	@ConfigItem(
		position = 4,
		keyName = "showItemInspectOption",
		name = "Item Inspect",
		description = "Adds an Inspect menu option to item widgets. Requires OSRS Wiki lookups.",
		section = WIKI_FEATURES
	)
	default boolean showItemInspectOption()
	{
		return true;
	}

	@ConfigItem(
		position = 5,
		keyName = "showInspectSearch",
		name = "Inspect search",
		description = "Adds sidebar search for item and NPC information. Requires OSRS Wiki lookups.",
		section = WIKI_FEATURES
	)
	default boolean showInspectSearch()
	{
		return true;
	}

	@ConfigItem(
		position = 6,
		keyName = "showEquipmentRecommendations",
		name = "Equipment recommendations",
		description = "Adds NPC equipment recommendations and lets you highlight recommended bank gear. Requires OSRS Wiki lookups.",
		section = WIKI_FEATURES
	)
	default boolean showEquipmentRecommendations()
	{
		return true;
	}

	@ConfigItem(
		position = 7,
		keyName = "npcInspectCacheTtlDays",
		name = "Inspect cache days",
		description = "How long to keep Inspect wiki data cached."
	)
	default int npcInspectCacheTtlDays()
	{
		return 7;
	}

	@ConfigItem(
		position = 8,
		keyName = "clearNpcInspectCacheOnStartup",
		name = "Clear NPC Inspect cache",
		description = "Clears cached NPC Inspect wiki data when the plugin starts."
	)
	default boolean clearNpcInspectCacheOnStartup()
	{
		return false;
	}
}

package com.inspect.item;

import java.util.Collections;
import java.util.Locale;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ItemInspectParserTest
{
	private final ItemInspectParser parser = new ItemInspectParser();

	@Test
	public void parsesItemAndBonusInfoboxes()
	{
		String wikitext = "{{Infobox Item\n"
			+ "|name = Abyssal whip\n"
			+ "|members = Yes\n"
			+ "|tradeable = Yes\n"
			+ "|equipable = Yes\n"
			+ "|value = 120001\n"
			+ "|weight = 0.453\n"
			+ "|examine = A weapon from the [[Abyss]].\n"
			+ "|id = 4151\n"
			+ "}}\n"
			+ "{{Infobox Bonuses\n"
			+ "|astab = 0\n"
			+ "|aslash = +82\n"
			+ "|acrush = 0\n"
			+ "|amagic = 0\n"
			+ "|arange = 0\n"
			+ "|dstab = 0\n"
			+ "|dslash = 0\n"
			+ "|dcrush = 0\n"
			+ "|dmagic = 0\n"
			+ "|drange = 0\n"
			+ "|str = +82\n"
			+ "|slot = weapon\n"
			+ "|speed = 4\n"
			+ "}}";

		ItemInspectInfo info = parser.parse(4151, "Whip", new ItemWikiLookup("Abyssal_whip", null, "https://wiki"), wikitext);

		assertEquals("Abyssal whip", info.getDisplayName());
		assertEquals("Yes", info.getMembers());
		assertEquals("120001 coins", info.getValue());
		assertEquals("0.453 kg", info.getWeight());
		assertEquals("A weapon from the Abyss.", info.getExamine());
		assertEquals("+82", info.getAttackSlash());
		assertEquals("+82", info.getStrength());
		assertEquals("weapon", info.getSlot());
		assertEquals("4 ticks (2.4 seconds)", info.getAttackSpeed());
	}

	@Test
	public void parsesWieldRequirementFromLeadText()
	{
		String wikitext = "{{Infobox Item\n"
			+ "|name = Abyssal whip\n"
			+ "|id = 4151\n"
			+ "}}\n"
			+ "The '''abyssal whip''' is a weapon which requires an [[Attack]] level of 70 to wield.\n"
			+ "{{Infobox Bonuses\n"
			+ "|slot = weapon\n"
			+ "}}";

		ItemInspectInfo info = parser.parse(4151, "Whip", new ItemWikiLookup("Abyssal_whip", null, "https://wiki"), wikitext);

		assertEquals("70", info.getRequirementAttack());
	}

	@Test
	public void parsesAlchAliases()
	{
		String wikitext = "{{Infobox Item\n"
			+ "|name = Rune platebody\n"
			+ "|id = 1127\n"
			+ "|high alch = 39000\n"
			+ "|low alchemy = 26000\n"
			+ "}}";

		ItemInspectInfo info = parser.parse(1127, "Rune platebody", new ItemWikiLookup("Rune_platebody", null, "https://wiki"), wikitext);

		assertEquals("39000 coins", info.getHighAlch());
		assertEquals("26000 coins", info.getLowAlch());
	}

	@Test
	public void parsesMultipleWearRequirementsFromLeadText()
	{
		String wikitext = "{{Infobox Item\n"
			+ "|name = Black d'hide body\n"
			+ "|id = 2503\n"
			+ "}}\n"
			+ "'''Black d'hide body''' requires at least level 70 [[Ranged]] and 40 [[Defence]] to be worn.\n"
			+ "{{Infobox Bonuses\n"
			+ "|slot = body\n"
			+ "}}";

		ItemInspectInfo info = parser.parse(2503, "Black d'hide body", new ItemWikiLookup("Black_d'hide_body", null, "https://wiki"), wikitext);

		assertEquals("70", info.getRequirementRanged());
		assertEquals("40", info.getRequirementDefence());
	}

	@Test
	public void ignoresCreationRequirements()
	{
		String wikitext = "{{Infobox Item\n"
			+ "|name = Slayer helmet\n"
			+ "|id = 11864\n"
			+ "}}\n"
			+ "The helmet can be created by players with level 55 [[Crafting]]. It requires 10 [[Defence]] to equip.\n"
			+ "{{Infobox Bonuses\n"
			+ "|slot = head\n"
			+ "}}";

		ItemInspectInfo info = parser.parse(11864, "Slayer helmet", new ItemWikiLookup("Slayer_helmet", null, "https://wiki"), wikitext);

		assertEquals("10", info.getRequirementDefence());
		assertNull(info.getRequirementSlayer());
	}

	@Test
	public void parsesQuestRequirementsAndSourceSummary()
	{
		String wikitext = "{{Infobox Item\n"
			+ "|name = Royal seed pod\n"
			+ "|id = 19564\n"
			+ "}}\n"
			+ "The '''royal seed pod''' is rewarded to the player by [[King Narnode Shareen]] after completing [[Monkey Madness II]]. "
			+ "It requires the Monkey Madness II quest to use.\n"
			+ "It is not dropped by monsters, sold by shops, or created by players.\n";

		ItemInspectInfo info = parser.parse(19564, "Royal seed pod", new ItemWikiLookup("Royal_seed_pod", null, "https://wiki"), wikitext);

		assertEquals("the Monkey Madness II quest", info.getQuestRequirements());
		assertEquals("Quests", info.getSourceSummary());
	}

	@Test
	public void parsesStructuredItemSourcesAndRequirements()
	{
		String wikitext = "{{Infobox Item\n"
			+ "|name = Test bow\n"
			+ "|id = 42\n"
			+ "}}\n"
			+ "The '''test bow''' requires [[Lost City]] quest to wield.\n"
			+ "It is sold by [[Lowe's Archery Emporium]].\n"
			+ "It is dropped by [[Abyssal demon]]s.\n"
			+ "It can be crafted with level 80 [[Fletching]] and a [[magic logs|magic log]].\n"
			+ "It can also be obtained as a reward from elite [[Treasure Trails]].\n";

		ItemInspectInfo info = parser.parse(42, "Test bow", new ItemWikiLookup("Test_bow", null, "https://wiki"), wikitext);

		assertEquals("Shops, Monsters, Skilling, Clues", info.getSourceSummary());
		assertEquals(4, info.getSourcePlan().size());
		assertEquals("Shops", info.getSourcePlan().get(0).getCategory());
		assertEquals("Monsters", info.getSourcePlan().get(1).getCategory());
		assertEquals("Skilling", info.getSourcePlan().get(2).getCategory());
		assertEquals("Clues", info.getSourcePlan().get(3).getCategory());
		assertEquals("Fletching", info.getSourcePlan().get(2).getRequirements().get(0).getSkillName());
		assertEquals(80, info.getSourcePlan().get(2).getRequirements().get(0).getLevel());
		assertFalse(info.getSourcePlan().get(3).getDetails().isEmpty());
	}

	@Test
	public void distinguishesDragonDaggerShopSourceFromWieldQuestAndPoisonedVariant()
	{
		String wikitext = "{{Infobox Item\n"
			+ "|version1 = Unpoisoned\n"
			+ "|version2 = Poison++\n"
			+ "|name1 = Dragon dagger\n"
			+ "|name2 = Dragon dagger(p++)\n"
			+ "|id1 = 1215\n"
			+ "|id2 = 5698\n"
			+ "}}\n"
			+ "The '''dragon dagger''' can be wielded by players with at least 60 [[Attack]] after completion of the [[Lost City]] quest.\n"
			+ "==Item sources==\n"
			+ "===Dragon dagger===\n"
			+ "{{Drop sources|Dragon dagger}}\n"
			+ "====Shop locations====\n"
			+ "{{Store locations list|Dragon dagger}}\n"
			+ "===Dragon dagger(p++)===\n"
			+ "{{Drop sources|Dragon dagger(p++)}}\n";

		ItemWikiLookup defaultLookup = new ItemWikiLookup("Dragon_dagger", null, "https://wiki");
		ItemInspectInfo defaultInfo = parser.parse(
			-1,
			"Dragon dagger",
			defaultLookup,
			wikitext,
			Collections.singletonList("Items_dropped_by_monster"));
		ItemInspectInfo poisonedInfo = parser.parse(
			5698,
			"Dragon dagger(p++)",
			new ItemWikiLookup("Dragon_dagger", "Poison++", "https://wiki"),
			wikitext,
			Collections.singletonList("Items_dropped_by_monster"));

		assertEquals("the Lost City quest", defaultInfo.getQuestRequirements());
		assertEquals("Shops, Monsters", defaultInfo.getSourceSummary());
		assertEquals("Shops", defaultInfo.getSourcePlan().get(0).getCategory());
		assertEquals("Sold by NPC shops; see the OSRS Wiki shop locations table for sellers and stock.",
			defaultInfo.getSourcePlan().get(0).getDetails().get(0));
		assertEquals("Monsters", defaultInfo.getSourcePlan().get(1).getCategory());

		assertEquals("the Lost City quest", poisonedInfo.getQuestRequirements());
		assertEquals("Monsters", poisonedInfo.getSourceSummary());
		assertEquals(1, poisonedInfo.getSourcePlan().size());
		assertEquals("Monsters", poisonedInfo.getSourcePlan().get(0).getCategory());
	}

	@Test
	public void keepsEquipQuestOutOfSourcesAndCleansTemplatePipes()
	{
		String wikitext = "{{Infobox Item\n"
			+ "|name = Rune platebody\n"
			+ "|id = 1127\n"
			+ "}}\n"
			+ "Players with level 99 [[Smithing]] can make one by using 5 [[runite bar]]s on an anvil while carrying a hammer.\n"
			+ "It requires 40 [[Defence]] and completion of the quest {{QuestReq|Dragon Slayer I| to equip}}.\n";

		ItemInspectInfo info = parser.parse(1127, "Rune platebody", new ItemWikiLookup("Rune_platebody", null, "https://wiki"), wikitext);

		assertEquals("Players with level 99 Smithing can make one by using 5 runite bars on an anvil while carrying a hammer.",
			info.getSourcePlan().get(0).getDetails().get(0));
		assertEquals(1, info.getSourcePlan().size());
		assertEquals("Skilling", info.getSourcePlan().get(0).getCategory());
		assertEquals("the quest Dragon Slayer I", info.getQuestRequirements());
	}

	@Test
	public void stripsMediaLinksFromStardustSourceDetails()
	{
		String wikitext = "{{External|rs}}\n"
			+ "{{Infobox Item\n"
			+ "|name = Stardust\n"
			+ "|id = 25527\n"
			+ "}}\n"
			+ "[[File:Stardust 175 detail.png|left|150px]]\n"
			+ "'''Stardust''' can be mined during the [[Shooting Stars]] activity. "
			+ "It is used as currency in [[Dusuri's Star Shop]] or to add charges to the [[celestial ring]].\n";

		ItemInspectInfo info = parser.parse(25527, "Stardust", new ItemWikiLookup("Stardust", null, "https://wiki"), wikitext);

		assertEquals("Skilling", info.getSourceSummary());
		assertEquals(1, info.getSourcePlan().size());
		assertEquals("Skilling", info.getSourcePlan().get(0).getCategory());
		assertEquals("Stardust can be mined during the Shooting Stars activity.", info.getSourcePlan().get(0).getDetails().get(0));
		assertFalse(info.getSourcePlan().get(0).getDetails().get(0).contains("left|150px"));
		assertFalse(info.getSourcePlan().get(0).getDetails().get(0).startsWith("rs "));
	}

	@Test
	public void stripsTopLevelTemplatesAndDropRateTextFromMonsterSourceDetails()
	{
		String wikitext = "{{otheruses|def=no|the corresponding chest|brimstone chest}}\n"
			+ "{{Infobox Item\n"
			+ "|name = Brimstone key\n"
			+ "|id = 23083\n"
			+ "}}\n"
			+ "[[File:Brimstone key detail.png|left|150px]]\n"
			+ "The '''brimstone key''' can be obtained from killing monsters while on a [[Slayer task]] given by [[Konar quo Maten]]. "
			+ "It is used to open the [[brimstone chest]]. "
			+ "The average value of a drop from the brimstone chest is {{Coins|{{Average drop value|Brimstone chest|raw=yes}}}}.\n"
			+ "The probability of receiving a key as a drop increases with the slain monster's [[Combat level]]. "
			+ "For monsters with combat level 100 or higher, the probability is between 1/100 and 1/50.\n"
			+ "A brimstone key is always dropped by a [[superior slayer monster]] assigned by Konar.\n"
			+ "{{Chart data|Chart data/brimstone key drop rate|width=40vw|height=40vh}}\n"
			+ "==Item sources==\n"
			+ "{{Drop sources|Brimstone key}}\n";

		ItemInspectInfo info = parser.parse(23083, "Brimstone key", new ItemWikiLookup("Brimstone_key", null, "https://wiki"), wikitext);

		assertEquals("Monsters", info.getSourceSummary());
		assertEquals(1, info.getSourcePlan().size());
		assertEquals("Monsters", info.getSourcePlan().get(0).getCategory());
		assertEquals(2, info.getSourcePlan().get(0).getDetails().size());
		assertEquals("The brimstone key can be obtained from killing monsters while on a Slayer task given by Konar quo Maten.",
			info.getSourcePlan().get(0).getDetails().get(0));
		assertEquals("A brimstone key is always dropped by a superior slayer monster assigned by Konar.",
			info.getSourcePlan().get(0).getDetails().get(1));
	}

	@Test
	public void classifiesDragonPlateskirtAsMonsterDropWithoutTreasureTrailMarkup()
	{
		String wikitext = "{{External|rs}}\n"
			+ "{{Infobox Item\n"
			+ "|name = Dragon plateskirt\n"
			+ "|examine = This looks pretty heavy.\n"
			+ "|id = 4585\n"
			+ "}}\n"
			+ "The '''dragon plateskirt''' is a rare piece of [[armour]] that requires a [[Defence]] level of 60 to be worn.\n"
			+ "==Combat stats==\n"
			+ "{{Infobox Bonuses|slot=legs}}\n"
			+ "==Products==\n"
			+ "{{Uses material list|Dragon plateskirt}}\n"
			+ "==Treasure Trails==\n"
			+ "{{EmoteClue|tier=master|item1=Dragon plateskirt}}\n"
			+ "==Item sources==\n"
			+ "{{Drop sources|Dragon plateskirt}}\n"
			+ "==Used in recommended equipment==\n"
			+ "{{Used in recommended equipment|Dragon plateskirt}}\n"
			+ "==Changes==\n";

		ItemInspectInfo info = parser.parse(
			4585,
			"Dragon plateskirt",
			new ItemWikiLookup("Dragon_plateskirt", null, "https://wiki"),
			wikitext,
			Collections.singletonList("Items_dropped_by_monster"));

		assertEquals("Monsters", info.getSourceSummary());
		assertEquals(1, info.getSourcePlan().size());
		assertEquals("Monsters", info.getSourcePlan().get(0).getCategory());
		assertEquals("Dropped by monsters; see the OSRS Wiki item sources table for specific monsters and rates.",
			info.getSourcePlan().get(0).getDetails().get(0));
		assertFalse(info.getSourcePlan().get(0).getDetails().get(0).contains("=="));
	}

	@Test
	public void ignoresChangesSectionWhenParsingEnsouledHeadSources()
	{
		String wikitext = "{{Infobox Item\n"
			+ "|name = Ensouled dragon head\n"
			+ "|id = 13510\n"
			+ "}}\n"
			+ "[[File:Ensouled dragon head detail.png|left|150px]]\n"
			+ "An '''ensouled dragon head''' is an item which can be dropped by chromatic [[dragon (race)|dragons]]. "
			+ "It is used to gain [[Prayer]] experience by using the level 90 [[Magic]] spell [[Master Reanimation]].\n"
			+ "==Changes==\n"
			+ "{{Subject changes\n"
			+ "|date = 28 April 2021\n"
			+ "|update = Arceuus Spellbook Beta and A Kingdom Divided Preparation\n"
			+ "|change = The examine text was slightly changed; previously, it was ''\"The creature's soul is still in here.\"''\n"
			+ "}}\n";

		ItemInspectInfo info = parser.parse(13510, "Ensouled dragon head",
			new ItemWikiLookup("Ensouled_dragon_head", null, "https://wiki"), wikitext);

		assertEquals("Monsters", info.getSourceSummary());
		assertEquals(1, info.getSourcePlan().size());
		assertEquals("Monsters", info.getSourcePlan().get(0).getCategory());
		assertEquals("An ensouled dragon head is an item which can be dropped by chromatic dragons.",
			info.getSourcePlan().get(0).getDetails().get(0));
	}

	@Test
	public void parsesRunePlatelegsRequirementsWithoutExperienceAsLevel()
	{
		String wikitext = "{{Infobox Item\n"
			+ "|name = Rune platelegs\n"
			+ "|id = 1079\n"
			+ "}}\n"
			+ "'''Rune platelegs''' are platelegs made of [[runite]]. They require level 40 [[Defence]] to wear.\n"
			+ "They can be created with the [[Smithing]] [[skill]] by using 3 [[runite bar]]s and a [[hammer]] on an [[anvil]]; "
			+ "this requires level 99 Smithing and yields 225 Smithing [[experience]].\n";

		ItemInspectInfo info = parser.parse(1079, "Rune platelegs", new ItemWikiLookup("Rune_platelegs", null, "https://wiki"), wikitext);

		assertEquals("40", info.getRequirementDefence());
		assertEquals("Smithing", info.getSourcePlan().get(0).getRequirements().get(0).getSkillName());
		assertEquals(99, info.getSourcePlan().get(0).getRequirements().get(0).getLevel());
		assertEquals(1, info.getSourcePlan().get(0).getRequirements().size());
	}

	@Test
	public void doesNotAttachSkillingRequirementsToMonsterSources()
	{
		String wikitext = "{{Infobox Item\n"
			+ "|name = Rune thrownaxe\n"
			+ "|id = 805\n"
			+ "}}\n"
			+ "'''Rune thrownaxes''' require level 40 [[Attack]] to wield.\n"
			+ "They are dropped by monsters and can be created with level 90 [[Smithing]].\n";

		ItemInspectInfo info = parser.parse(805, "Rune thrownaxe", new ItemWikiLookup("Rune_thrownaxe", null, "https://wiki"), wikitext);

		assertEquals("Monsters", info.getSourcePlan().get(0).getCategory());
		assertTrue(info.getSourcePlan().get(0).getRequirements().isEmpty());
		assertEquals("Skilling", info.getSourcePlan().get(1).getCategory());
		assertEquals("Smithing", info.getSourcePlan().get(1).getRequirements().get(0).getSkillName());
		assertEquals(90, info.getSourcePlan().get(1).getRequirements().get(0).getLevel());
	}

	@Test
	public void ignoresNegatedSourceClaims()
	{
		String wikitext = "{{Infobox Item\n"
			+ "|name = Unobtainable item\n"
			+ "|id = 1\n"
			+ "}}\n"
			+ "It is not dropped by monsters, sold by shops, or created by players.\n";

		ItemInspectInfo info = parser.parse(1, "Unobtainable item", new ItemWikiLookup("Unobtainable_item", null, "https://wiki"), wikitext);

		assertNull(info.getSourceSummary());
	}

	@Test
	public void selectsVersionByItemId()
	{
		String wikitext = "{{Infobox Item\n"
			+ "|name1 = Item one\n"
			+ "|name2 = Item two\n"
			+ "|members = Yes\n"
			+ "|id1 = 100\n"
			+ "|id2 = 200\n"
			+ "}}";

		ItemInspectInfo info = parser.parse(200, "Fallback", new ItemWikiLookup("Item", "Item_two", "https://wiki"), wikitext);

		assertEquals("Item two", info.getDisplayName());
		assertEquals("Yes", info.getMembers());
	}

	@Test
	public void selectsVersionByAnchorWhenSearchingWithoutItemId()
	{
		String wikitext = "{{Infobox Item\n"
			+ "|version1 = Item one\n"
			+ "|version2 = Item two\n"
			+ "|name1 = First variant\n"
			+ "|name2 = Second variant\n"
			+ "|id1 = 100\n"
			+ "|id2 = 200\n"
			+ "}}";

		ItemInspectInfo info = parser.parse(-1, "Fallback", new ItemWikiLookup("Item", "Item_two", "https://wiki"), wikitext);

		assertEquals(200, info.getItemId());
		assertEquals("Second variant", info.getDisplayName());
	}

	@Test
	public void selectsVersionFromCommaSeparatedItemIds()
	{
		String wikitext = "{{Infobox Item\n"
			+ "|name1 = First variant\n"
			+ "|name2 = Second variant\n"
			+ "|id1 = 100\n"
			+ "|id2 = 200, 201\n"
			+ "}}";

		ItemInspectInfo info = parser.parse(201, "Fallback", new ItemWikiLookup("Item", null, "https://wiki"), wikitext);

		assertEquals("Second variant", info.getDisplayName());
	}

	@Test
	public void searchWithoutItemIdSelectsFirstVersion()
	{
		String wikitext = "{{Infobox Item\n"
			+ "|name1 = Item one\n"
			+ "|name2 = Item two\n"
			+ "|id1 = 100\n"
			+ "|id2 = 200\n"
			+ "}}";

		ItemInspectInfo info = parser.parse(-1, "Fallback", new ItemWikiLookup("Item", null, "https://wiki"), wikitext);

		assertEquals(100, info.getItemId());
		assertEquals("Item one", info.getDisplayName());
	}

	@Test
	public void formatsTickDurationsWithDecimalPointRegardlessOfLocale()
	{
		Locale previousFormatLocale = Locale.getDefault(Locale.Category.FORMAT);
		try
		{
			Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY);
			String wikitext = "{{Infobox Item\n"
				+ "|name = Test weapon\n"
				+ "|id = 1\n"
				+ "}}\n"
				+ "{{Infobox Bonuses\n"
				+ "|speed = 4\n"
				+ "}}";

			ItemInspectInfo info = parser.parse(1, "Test weapon", new ItemWikiLookup("Test_weapon", null, "https://wiki"), wikitext);

			assertEquals("4 ticks (2.4 seconds)", info.getAttackSpeed());
		}
		finally
		{
			Locale.setDefault(Locale.Category.FORMAT, previousFormatLocale);
		}
	}

	@Test
	public void missingItemInfoboxReturnsNull()
	{
		assertNull(parser.parse(1, "Coins", new ItemWikiLookup("Coins", null, "https://wiki"), "No infobox"));
	}
}

package com.enhanced.item;

import java.util.Locale;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
			+ "The '''royal seed pod''' requires [[Monkey Madness II]] quest to use.\n"
			+ "It is a reward from [[King Narnode Shareen]]. It is not dropped by monsters, sold by shops, or created by players.\n";

		ItemInspectInfo info = parser.parse(19564, "Royal seed pod", new ItemWikiLookup("Royal_seed_pod", null, "https://wiki"), wikitext);

		assertEquals("Monkey Madness II quest", info.getQuestRequirements());
		assertEquals("Reward", info.getSourceSummary());
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

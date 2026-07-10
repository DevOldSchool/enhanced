package com.enhanced.npc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Locale;
import org.junit.Test;

public class NpcInspectParserTest
{
	private final NpcInspectParser parser = new NpcInspectParser();

	@Test
	public void parsesVersionByNpcId()
	{
		NpcCombatInfo info = parser.parse(3046, "Goblin", new NpcWikiLookup("Goblin", "Level_13", "https://oldschool.runescape.wiki/w/Goblin#Level_13"), goblinWikitext());

		assertEquals("Goblin", info.getDisplayName());
		assertEquals("13", info.getCombatLevel());
		assertEquals("2", info.getMaxHit());
		assertEquals("Crush", info.getAttackStyle());
		assertEquals("1", info.getMagic());
		assertEquals("4", info.getMagicDefence());
		assertEquals("No elemental weakness", info.getElementalWeakness());
		assertEquals("0% resistance", info.getPoisonResistance());
		assertEquals("Not immune", info.getCannonImmunity());
	}

	@Test
	public void parsesNpcIdFromCommaSeparatedVersionIds()
	{
		NpcCombatInfo info = parser.parse(3029, "Goblin", new NpcWikiLookup("Goblin", "Level_2", "https://oldschool.runescape.wiki/w/Goblin#Level_2"), goblinWikitext());

		assertEquals(3029, info.getNpcId());
		assertEquals("2", info.getCombatLevel());
		assertEquals("Stab", info.getAttackStyle());
	}

	@Test
	public void parsesUnsuffixedSingleVersionFields()
	{
		String wikitext = "{{Infobox Monster\n"
			+ "|name = Rat\n"
			+ "|combat = 1\n"
			+ "|attack style = [[Crush]]\n"
			+ "|id = 2854\n"
			+ "|mage = 1\n"
			+ "}}";

		NpcCombatInfo info = parser.parse(2854, "Rat", new NpcWikiLookup("Rat", null, "https://oldschool.runescape.wiki/w/Rat"), wikitext);

		assertEquals("Rat", info.getDisplayName());
		assertEquals("1", info.getCombatLevel());
		assertEquals("Crush", info.getAttackStyle());
		assertEquals("1", info.getMagic());
	}

	@Test
	public void returnsNullWhenNpcIdDoesNotMatchVersionedPage()
	{
		NpcCombatInfo info = parser.parse(999999, "Goblin", new NpcWikiLookup("Goblin", "Level_99", "https://oldschool.runescape.wiki/w/Goblin#Level_99"), goblinWikitext());

		assertNull(info);
	}

	@Test
	public void searchWithoutNpcIdSelectsFirstVersion()
	{
		NpcCombatInfo info = parser.parse(-1, "Goblin", new NpcWikiLookup("Goblin", null, "https://oldschool.runescape.wiki/w/Goblin"), goblinWikitext());

		assertEquals(3028, info.getNpcId());
		assertEquals("2", info.getCombatLevel());
	}

	@Test
	public void searchWithoutNpcIdSelectsVersionByAnchor()
	{
		NpcCombatInfo info = parser.parse(-1, "Goblin", new NpcWikiLookup("Goblin", "Level_13", "https://oldschool.runescape.wiki/w/Goblin#Level_13"), goblinWikitext());

		assertEquals(3046, info.getNpcId());
		assertEquals("13", info.getCombatLevel());
		assertEquals("Crush", info.getAttackStyle());
	}

	@Test
	public void normalizesNestedTemplatesAndPipedLinks()
	{
		String wikitext = "{{Infobox Monster\n"
			+ "|name = [[Giant rat|Giant rat]]\n"
			+ "|combat = 3\n"
			+ "|attack style = {{Nowrap|{{Small|[[Magic#Magic combat|Magic]]}}}}\n"
			+ "|id = 2855\n"
			+ "}}";

		NpcCombatInfo info = parser.parse(2855, "Rat", new NpcWikiLookup("Giant_rat", null, "https://wiki"), wikitext);

		assertEquals("Giant rat", info.getDisplayName());
		assertEquals("Magic", info.getAttackStyle());
	}

	@Test
	public void formatsRespawnSecondsIndependentlyOfDefaultLocale()
	{
		Locale originalLocale = Locale.getDefault();
		try
		{
			Locale.setDefault(Locale.GERMANY);
			String wikitext = "{{Infobox Monster\n"
				+ "|name = Rat\n"
				+ "|respawn = 1\n"
				+ "|id = 2854\n"
				+ "}}";

			NpcCombatInfo info = parser.parse(2854, "Rat", new NpcWikiLookup("Rat", null, "https://wiki"), wikitext);

			assertEquals("1 ticks (0.6 seconds)", info.getRespawnTime());
		}
		finally
		{
			Locale.setDefault(originalLocale);
		}
	}

	@Test
	public void returnsNullForUnbalancedInfobox()
	{
		String wikitext = "{{Infobox Monster\n|name = Rat\n|id = 2854\n";

		assertNull(parser.parse(2854, "Rat", new NpcWikiLookup("Rat", null, "https://wiki"), wikitext));
	}

	@Test
	public void parsesSlayerDetailsAndClassifiedDrops()
	{
		String wikitext = "{{Infobox Monster\n"
			+ "|name = Abyssal demon\n"
			+ "|combat = 124\n"
			+ "|id = 415\n"
			+ "|slaylvl = 85\n"
			+ "|cat = Demons\n"
			+ "|assignedby = [[Duradel]], [[Nieve]], [[Konar quo Maten]]\n"
			+ "|taskonly = No\n"
			+ "|superior = [[Greater abyssal demon]]\n"
			+ "}}\n"
			+ "== Drops ==\n"
			+ "{{DropsLine|name=Abyssal whip|rarity=1/512}}\n"
			+ "{{DropsLine|name=Adamant platebody|rarity=Rare}}\n"
			+ "{{DropsLine|name=Clue scroll (hard)|rarity=Rare}}\n"
			+ "{{DropsLine|name=Grimy ranarr weed|rarity=Uncommon}}\n"
			+ "{{DropsLine|name=Law rune|rarity=Common}}\n";

		NpcCombatInfo info = parser.parse(415, "Abyssal demon", new NpcWikiLookup("Abyssal_demon", null, "https://wiki"), wikitext);

		assertEquals("85", info.getSlayerLevel());
		assertEquals("Demons", info.getSlayerCategory());
		assertEquals("Duradel, Nieve, Konar quo Maten", info.getAssignedBy());
		assertEquals("No", info.getTaskOnly());
		assertEquals("Greater abyssal demon", info.getSuperiorVariant());
		assertEquals("Adamant platebody, Clue scroll (hard)", info.getRareDrops());
		assertEquals("Adamant platebody", info.getAlchableDrops());
		assertEquals("Clue scroll (hard)", info.getClueDrops());
		assertEquals("Grimy ranarr weed", info.getResourceDrops());
		assertEquals("Law rune", info.getSupplyDrops());
	}

	@Test
	public void keepsDropsAcrossSubheadingsButStopsAtNextTopLevelHeading()
	{
		String wikitext = "{{Infobox Monster\n"
			+ "|name = Test monster\n"
			+ "|id = 1\n"
			+ "}}\n"
			+ "== Drops ==\n"
			+ "=== Herbs ===\n"
			+ "{{DropsLine|name=Grimy ranarr weed|rarity=Uncommon}}\n"
			+ "=== Weapons and armour ===\n"
			+ "{{DropsLine|name=Rune platebody|rarity=Rare}}\n"
			+ "== Changes ==\n"
			+ "{{DropsLine|name=Dragon platebody|rarity=Rare}}\n";

		NpcCombatInfo info = parser.parse(1, "Test monster", new NpcWikiLookup("Test_monster", null, "https://wiki"), wikitext);

		assertEquals("Grimy ranarr weed, Rune platebody", info.getNotableDrops());
		assertEquals("Rune platebody", info.getRareDrops());
		assertEquals("Grimy ranarr weed", info.getResourceDrops());
	}

	@Test
	public void limitsDropSummariesToSixUniqueEntries()
	{
		String wikitext = "{{Infobox Monster|name=Test monster|id=1}}\n"
			+ "==Drops==\n"
			+ "{{DropsLine|name=First drop|rarity=Rare}}\n"
			+ "{{DropsLine|name=Second drop|rarity=Rare}}\n"
			+ "{{DropsLine|name=Third drop|rarity=Rare}}\n"
			+ "{{DropsLine|name=Fourth drop|rarity=Rare}}\n"
			+ "{{DropsLine|name=Fifth drop|rarity=Rare}}\n"
			+ "{{DropsLine|name=Sixth drop|rarity=Rare}}\n"
			+ "{{DropsLine|name=Seventh drop|rarity=Rare}}\n";

		NpcCombatInfo info = parser.parse(1, "Test monster", new NpcWikiLookup("Test_monster", null, "https://wiki"), wikitext);

		assertEquals("First drop, Second drop, Third drop, Fourth drop, Fifth drop, Sixth drop", info.getRareDrops());
	}

	private static String goblinWikitext()
	{
		return "{{Infobox Monster\n"
			+ "|version1 = Level 2\n"
			+ "|version2 = Level 13\n"
			+ "|name = Goblin\n"
			+ "|image1 = [[File:Goblin.png|150px]]\n"
			+ "|combat1 = 2\n"
			+ "|combat2 = 13\n"
			+ "|xpbonus = 0\n"
			+ "|max hit1 = 1\n"
			+ "|max hit2 = 2\n"
			+ "|aggressive = No\n"
			+ "|poisonous = No\n"
			+ "|attack style1 = [[Stab]]\n"
			+ "|attack style2 = [[Crush]]\n"
			+ "|attack speed1 = 4\n"
			+ "|attack speed2 = 4\n"
			+ "|respawn = 35\n"
			+ "|hitpoints1 = 5\n"
			+ "|hitpoints2 = 16\n"
			+ "|att1 = 1\n"
			+ "|att2 = 12\n"
			+ "|str1 = 1\n"
			+ "|str2 = 13\n"
			+ "|def1 = 1\n"
			+ "|def2 = 7\n"
			+ "|mage = 1\n"
			+ "|range = 1\n"
			+ "|attbns1 = -21\n"
			+ "|attbns2 = 0\n"
			+ "|strbns1 = -15\n"
			+ "|strbns2 = 0\n"
			+ "|amagic = 0\n"
			+ "|mbns = 0\n"
			+ "|arange = 0\n"
			+ "|rngbns = 0\n"
			+ "|dstab1 = -15\n"
			+ "|dstab2 = 4\n"
			+ "|dslash1 = -15\n"
			+ "|dslash2 = 6\n"
			+ "|dcrush1 = -15\n"
			+ "|dcrush2 = 8\n"
			+ "|dmagic1 = -15\n"
			+ "|dmagic2 = 4\n"
			+ "|dlight1 = -15\n"
			+ "|dlight2 = 4\n"
			+ "|dstandard1 = -15\n"
			+ "|dstandard2 = 4\n"
			+ "|dheavy1 = -15\n"
			+ "|dheavy2 = 4\n"
			+ "|poisonresistance = 0\n"
			+ "|venomresistance = 0\n"
			+ "|immunecannon = No\n"
			+ "|immunethrall = No\n"
			+ "|id1 = 3028,3029\n"
			+ "|id2 = 3046\n"
			+ "}}";
	}
}

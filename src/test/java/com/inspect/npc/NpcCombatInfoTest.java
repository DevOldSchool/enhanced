package com.inspect.npc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NpcCombatInfoTest
{
	@Test
	public void ttlBoundaryIsInclusive()
	{
		NpcCombatInfo info = NpcCombatInfo.builder()
			.fetchedAtEpochSecond(1000L)
			.build();
		long oneDay = 24L * 60L * 60L;

		assertFalse(info.isExpired(1000L + oneDay, 1));
		assertTrue(info.isExpired(1000L + oneDay + 1L, 1));
	}

	@Test
	public void nonPositiveTtlAlwaysExpires()
	{
		NpcCombatInfo info = NpcCombatInfo.builder()
			.fetchedAtEpochSecond(1000L)
			.build();

		assertTrue(info.isExpired(1000L, 0));
		assertTrue(info.isExpired(1000L, -1));
	}

	@Test
	public void cacheKeyNormalizesPageAndAnchorForFileNames()
	{
		NpcCombatInfo info = NpcCombatInfo.builder()
			.npcId(3028)
			.wikiPage("Goblin / Cave")
			.wikiAnchor("Level_2#North")
			.build();

		assertEquals("3028-goblin-cave-level-2-north", info.cacheKey());
	}
}

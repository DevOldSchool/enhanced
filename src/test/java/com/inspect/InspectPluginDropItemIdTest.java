package com.inspect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.inspect.npc.NpcItemRequirement;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class InspectPluginDropItemIdTest
{
	@Test
	public void resolvesKnownUnindexedDropItems()
	{
		assertEquals(ItemID.COINS, InspectPlugin.dropItemIdFallback("Coins"));
		assertEquals(ItemID.KEYHALF1, InspectPlugin.dropItemIdFallback("Tooth half of key"));
		assertEquals(ItemID.KEYHALF2, InspectPlugin.dropItemIdFallback("Loop half of key"));
		assertEquals(ItemID.VARLAMORE_KEY_HALF_1, InspectPlugin.dropItemIdFallback("Tooth half of key (moon key)"));
		assertEquals(ItemID.VARLAMORE_KEY_HALF_2, InspectPlugin.dropItemIdFallback("Loop half of key (moon key)"));
		assertEquals(ItemID.ATTAS_SEED, InspectPlugin.dropItemIdFallback("Attas seed"));
		assertEquals(ItemID.IASOR_SEED, InspectPlugin.dropItemIdFallback("Iasor seed"));
		assertEquals(ItemID.KRONOS_SEED, InspectPlugin.dropItemIdFallback("Kronos seed"));
		assertEquals(ItemID.WATERMELON_SEED, InspectPlugin.dropItemIdFallback("Watermelon seed"));
		assertEquals(ItemID.SNAPE_GRASS_SEED, InspectPlugin.dropItemIdFallback("Snape grass seed"));
		assertEquals(ItemID.WHITE_LILY_SEED, InspectPlugin.dropItemIdFallback("White lily seed"));
		assertEquals(ItemID.KONAR_KEY, InspectPlugin.dropItemIdFallback(" Brimstone key "));
		assertEquals(ItemID.SLAYER_ROOF_KEY, InspectPlugin.dropItemIdFallback("brittle key"));
		assertEquals(-1, InspectPlugin.dropItemIdFallback("Unknown key"));
	}

	@Test
	public void emptyNpcItemRequirementAlternativesAreNotDisplayed()
	{
		assertNull(InspectPlugin.npcItemRequirementWithAlternatives(null));
		assertNull(InspectPlugin.npcItemRequirementWithAlternatives(Collections.emptyList()));
	}

	@Test
	public void npcItemRequirementLabelUsesOnlyResolvedAlternatives()
	{
		NpcItemRequirement requirement = InspectPlugin.npcItemRequirementWithAlternatives(Arrays.asList("Rock hammer", "Granite maul"));

		assertEquals("Rock hammer or Granite maul", requirement.getLabel());
		assertEquals(Arrays.asList("Rock hammer", "Granite maul"), requirement.getAlternatives());
	}
}

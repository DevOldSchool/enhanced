package com.inspect;

import static org.junit.Assert.assertEquals;

import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class InspectPluginDropItemIdTest
{
	@Test
	public void resolvesKnownUnindexedDropItems()
	{
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
}

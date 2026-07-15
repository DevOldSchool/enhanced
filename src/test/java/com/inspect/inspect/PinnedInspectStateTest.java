package com.inspect.inspect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.inspect.item.ItemInspectInfo;
import com.inspect.npc.NpcCombatInfo;
import com.inspect.player.PlayerEquipmentItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class PinnedInspectStateTest
{
	@Test
	public void tracksAndClearsPinnedInspectionTypesIndependently()
	{
		NpcCombatInfo npc = NpcCombatInfo.builder().displayName("Gargoyle").build();
		ItemInspectInfo item = ItemInspectInfo.builder().itemId(4151).displayName("Abyssal whip").build();
		PlayerEquipmentItem weapon = new PlayerEquipmentItem("Weapon", 4151, "Abyssal whip", 2_000_000);

		PinnedInspectState state = PinnedInspectState.empty()
			.withNpc(npc)
			.withItem(item)
			.withPlayer("Player one", 100, Collections.singletonList(weapon));

		assertTrue(state.hasAny());
		assertEquals(npc, state.getNpc());
		assertEquals(item, state.getItem());
		assertEquals("Player one", state.getPlayerName());
		assertEquals(100, state.getPlayerCombatLevel());
		assertEquals(Collections.singletonList(weapon), state.getPlayerEquipment());

		PinnedInspectState withoutNpc = state.withoutNpc();
		assertNull(withoutNpc.getNpc());
		assertEquals(item, withoutNpc.getItem());
		assertEquals("Player one", withoutNpc.getPlayerName());

		PinnedInspectState withoutItem = state.withoutItem();
		assertEquals(npc, withoutItem.getNpc());
		assertNull(withoutItem.getItem());
		assertEquals("Player one", withoutItem.getPlayerName());

		PinnedInspectState withoutPlayer = state.withoutPlayer();
		assertEquals(npc, withoutPlayer.getNpc());
		assertEquals(item, withoutPlayer.getItem());
		assertNull(withoutPlayer.getPlayerName());
		assertEquals(0, withoutPlayer.getPlayerCombatLevel());
		assertTrue(withoutPlayer.getPlayerEquipment().isEmpty());
	}

	@Test
	public void copiesPinnedPlayerEquipment()
	{
		PlayerEquipmentItem weapon = new PlayerEquipmentItem("Weapon", 4151, "Abyssal whip", 2_000_000);
		List<PlayerEquipmentItem> equipment = new ArrayList<>();
		equipment.add(weapon);

		PinnedInspectState state = PinnedInspectState.empty().withPlayer("Player one", 100, equipment);
		equipment.clear();

		assertEquals(Collections.singletonList(weapon), state.getPlayerEquipment());
		assertNotSame(equipment, state.getPlayerEquipment());
	}

	@Test
	public void emptyStateHasNoPinnedInspections()
	{
		PinnedInspectState state = PinnedInspectState.empty();

		assertFalse(state.hasAny());
		assertNull(state.getNpc());
		assertNull(state.getItem());
		assertNull(state.getPlayerName());
		assertTrue(state.getPlayerEquipment().isEmpty());
	}
}

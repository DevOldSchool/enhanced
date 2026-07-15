package com.inspect.inspect;

import com.inspect.item.ItemInspectInfo;
import com.inspect.npc.NpcCombatInfo;
import com.inspect.player.PlayerEquipmentItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Value;

@Value
public class PinnedInspectState
{
	NpcCombatInfo npc;
	ItemInspectInfo item;
	String playerName;
	int playerCombatLevel;
	List<PlayerEquipmentItem> playerEquipment;

	public static PinnedInspectState empty()
	{
		return new PinnedInspectState(null, null, null, 0, Collections.emptyList());
	}

	public boolean hasAny()
	{
		return npc != null || item != null || playerName != null;
	}

	public PinnedInspectState withNpc(NpcCombatInfo pinnedNpc)
	{
		return new PinnedInspectState(pinnedNpc, item, playerName, playerCombatLevel, playerEquipment);
	}

	public PinnedInspectState withoutNpc()
	{
		return new PinnedInspectState(null, item, playerName, playerCombatLevel, playerEquipment);
	}

	public PinnedInspectState withItem(ItemInspectInfo pinnedItem)
	{
		return new PinnedInspectState(npc, pinnedItem, playerName, playerCombatLevel, playerEquipment);
	}

	public PinnedInspectState withoutItem()
	{
		return new PinnedInspectState(npc, null, playerName, playerCombatLevel, playerEquipment);
	}

	public PinnedInspectState withPlayer(String pinnedPlayerName, int combatLevel, List<PlayerEquipmentItem> equipment)
	{
		return new PinnedInspectState(npc, item, pinnedPlayerName, combatLevel, copyEquipment(equipment));
	}

	public PinnedInspectState withoutPlayer()
	{
		return new PinnedInspectState(npc, item, null, 0, Collections.emptyList());
	}

	private static List<PlayerEquipmentItem> copyEquipment(List<PlayerEquipmentItem> equipment)
	{
		return equipment == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(equipment));
	}
}

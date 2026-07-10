package com.inspect.npc;

import com.inspect.item.ItemInspectInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Value;

@Value
public class EquipmentRecommendation
{
	NpcCombatInfo npc;
	CombatStyleRecommendation style;
	List<RecommendedItem> items;

	public String getStyleName()
	{
		return style == null ? null : style.getDisplayName();
	}

	public String getDefenceLabel()
	{
		return style == null ? null : style.getDefenceLabel();
	}

	public boolean hasItems()
	{
		return items != null && !items.isEmpty();
	}

	public Set<Integer> itemIds()
	{
		Set<Integer> itemIds = new HashSet<>();
		if (items != null)
		{
			for (RecommendedItem item : items)
			{
				itemIds.add(item.getItemId());
			}
		}
		return itemIds;
	}

	public Map<Integer, Integer> bankItemRanks()
	{
		Map<Integer, Integer> ranks = new LinkedHashMap<>();
		if (items != null)
		{
			for (RecommendedItem item : items)
			{
				if (item.isInBank())
				{
					ranks.put(item.getItemId(), item.getRank());
				}
			}
		}
		return ranks;
	}

	public static EquipmentRecommendation preview(NpcCombatInfo npc)
	{
		return new EquipmentRecommendation(npc, CombatStyleRecommendation.forNpc(npc), Collections.emptyList());
	}

	static EquipmentRecommendation fromBank(NpcCombatInfo npc, Collection<ItemInspectInfo> bankItems, int limit)
	{
		List<CandidateItem> candidates = new ArrayList<>();
		for (ItemInspectInfo item : bankItems)
		{
			candidates.add(new CandidateItem(item, true, false));
		}
		return fromCandidates(npc, candidates, limit);
	}

	static EquipmentRecommendation fromCandidates(NpcCombatInfo npc, Collection<CandidateItem> candidates, int limit)
	{
		CombatStyleRecommendation style = CombatStyleRecommendation.forNpc(npc);
		if (style == null)
		{
			return new EquipmentRecommendation(npc, null, Collections.emptyList());
		}

		List<RecommendedItem> recommendations = new ArrayList<>();
		for (CandidateItem candidate : candidates)
		{
			ItemInspectInfo item = candidate.getInfo();
			if (item == null || !style.isRelevant(item))
			{
				continue;
			}

			recommendations.add(new RecommendedItem(
				item.getItemId(),
				item.getDisplayName(),
				item.getSlot(),
				style.score(item),
				candidate.isInBank(),
				candidate.isEquipped(),
				0
			));
		}

		recommendations.sort(Comparator
			.comparingDouble(RecommendedItem::getScore)
			.reversed()
			.thenComparing(RecommendedItem::getDisplayName, Comparator.nullsLast(String::compareToIgnoreCase)));

		int boundedLimit = Math.max(0, limit);
		if (recommendations.size() > boundedLimit)
		{
			recommendations = new ArrayList<>(recommendations.subList(0, boundedLimit));
		}

		for (int i = 0; i < recommendations.size(); i++)
		{
			RecommendedItem item = recommendations.get(i);
			recommendations.set(i, new RecommendedItem(
				item.getItemId(),
				item.getDisplayName(),
				item.getSlot(),
				item.getScore(),
				item.isInBank(),
				item.isEquipped(),
				i + 1
			));
		}

		return new EquipmentRecommendation(npc, style, Collections.unmodifiableList(recommendations));
	}

	@Value
	static class CandidateItem
	{
		ItemInspectInfo info;
		boolean inBank;
		boolean equipped;
	}

	@Value
	public static class RecommendedItem
	{
		int itemId;
		String displayName;
		String slot;
		double score;
		boolean inBank;
		boolean equipped;
		int rank;
	}
}

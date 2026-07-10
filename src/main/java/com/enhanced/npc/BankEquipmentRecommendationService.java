package com.enhanced.npc;

import com.enhanced.item.ItemInspectInfo;
import com.enhanced.item.ItemInspectService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class BankEquipmentRecommendationService
{
	private static final int RECOMMENDATION_LIMIT = 12;

	private final ItemLookupService itemLookupService;

	@Inject
	BankEquipmentRecommendationService(ItemInspectService itemInspectService)
	{
		this(itemInspectService::inspect);
	}

	BankEquipmentRecommendationService(ItemLookupService itemLookupService)
	{
		this.itemLookupService = itemLookupService;
	}

	public CompletableFuture<EquipmentRecommendation> recommend(NpcCombatInfo npc, Collection<BankItemCandidate> bankItems, int ttlDays)
	{
		if (npc == null || bankItems == null || bankItems.isEmpty())
		{
			return CompletableFuture.completedFuture(EquipmentRecommendation.preview(npc));
		}

		Map<Integer, BankItemCandidate> uniqueItems = new LinkedHashMap<>();
		for (BankItemCandidate item : bankItems)
		{
			BankItemCandidate existing = uniqueItems.get(item.getItemId());
			if (existing == null)
			{
				uniqueItems.put(item.getItemId(), item);
				continue;
			}

			uniqueItems.put(item.getItemId(), new BankItemCandidate(
				existing.getItemId(),
				existing.getItemName(),
				existing.isInBank() || item.isInBank(),
				existing.isEquipped() || item.isEquipped()
			));
		}

		List<ItemLookup> lookups = new ArrayList<>();
		for (BankItemCandidate item : uniqueItems.values())
		{
			lookups.add(new ItemLookup(item, itemLookupService.inspect(item.getItemId(), item.getItemName(), ttlDays)
				.exceptionally(throwable -> null)));
		}

		return CompletableFuture.allOf(lookups.stream().map(ItemLookup::getLookup).toArray(CompletableFuture[]::new))
			.thenApply(ignored ->
			{
				List<EquipmentRecommendation.CandidateItem> items = new ArrayList<>();
				for (ItemLookup lookup : lookups)
				{
					ItemInspectInfo info = lookup.getLookup().join();
					if (info != null)
					{
						items.add(new EquipmentRecommendation.CandidateItem(info, lookup.getCandidate().isInBank(), lookup.getCandidate().isEquipped()));
					}
				}
				return EquipmentRecommendation.fromCandidates(npc, items, RECOMMENDATION_LIMIT);
			});
	}

	@FunctionalInterface
	interface ItemLookupService
	{
		CompletableFuture<ItemInspectInfo> inspect(int itemId, String itemName, int ttlDays);
	}

	@lombok.Value
	private static class ItemLookup
	{
		BankItemCandidate candidate;
		CompletableFuture<ItemInspectInfo> lookup;
	}

	@lombok.Value
	public static class BankItemCandidate
	{
		int itemId;
		String itemName;
		boolean inBank;
		boolean equipped;
	}
}

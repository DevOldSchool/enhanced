package com.enhanced.npc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.enhanced.item.ItemInspectInfo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class BankEquipmentRecommendationServiceTest
{
	@Test
	public void emptyInputReturnsPreviewWithoutItemLookups() throws Exception
	{
		AtomicInteger calls = new AtomicInteger();
		BankEquipmentRecommendationService service = new BankEquipmentRecommendationService((itemId, itemName, ttlDays) ->
		{
			calls.incrementAndGet();
			return CompletableFuture.completedFuture(null);
		});

		EquipmentRecommendation nullItems = service.recommend(stabNpc(), null, 7).get(5, TimeUnit.SECONDS);
		EquipmentRecommendation emptyItems = service.recommend(stabNpc(), Collections.emptyList(), 7).get(5, TimeUnit.SECONDS);

		assertEquals("Stab melee", nullItems.getStyleName());
		assertFalse(nullItems.hasItems());
		assertEquals("Stab melee", emptyItems.getStyleName());
		assertEquals(0, calls.get());
	}

	@Test
	public void duplicateCandidatesMergeSourcesAndOnlyInspectOnce() throws Exception
	{
		AtomicInteger calls = new AtomicInteger();
		BankEquipmentRecommendationService service = new BankEquipmentRecommendationService((itemId, itemName, ttlDays) ->
		{
			calls.incrementAndGet();
			return CompletableFuture.completedFuture(item(itemId, itemName, 25));
		});

		EquipmentRecommendation recommendation = service.recommend(stabNpc(), Arrays.asList(
			new BankEquipmentRecommendationService.BankItemCandidate(100, "Sword", true, false),
			new BankEquipmentRecommendationService.BankItemCandidate(100, "Sword", false, true)
		), 7).get(5, TimeUnit.SECONDS);

		assertEquals(1, calls.get());
		assertEquals(1, recommendation.getItems().size());
		assertTrue(recommendation.getItems().get(0).isInBank());
		assertTrue(recommendation.getItems().get(0).isEquipped());
		assertEquals(Integer.valueOf(1), recommendation.bankItemRanks().get(100));
	}

	@Test
	public void failedLookupDoesNotDiscardSuccessfulCandidates() throws Exception
	{
		BankEquipmentRecommendationService service = new BankEquipmentRecommendationService((itemId, itemName, ttlDays) ->
		{
			if (itemId == 1)
			{
				CompletableFuture<ItemInspectInfo> failed = new CompletableFuture<>();
				failed.completeExceptionally(new IOException("offline"));
				return failed;
			}
			return CompletableFuture.completedFuture(item(itemId, itemName, 20));
		});

		EquipmentRecommendation recommendation = service.recommend(stabNpc(), Arrays.asList(
			new BankEquipmentRecommendationService.BankItemCandidate(1, "Unavailable", true, false),
			new BankEquipmentRecommendationService.BankItemCandidate(2, "Available", true, false)
		), 7).get(5, TimeUnit.SECONDS);

		assertEquals(1, recommendation.getItems().size());
		assertEquals(2, recommendation.getItems().get(0).getItemId());
	}

	@Test
	public void recommendationIsCappedAtTwelveWithConsecutiveRanks() throws Exception
	{
		AtomicInteger calls = new AtomicInteger();
		BankEquipmentRecommendationService service = new BankEquipmentRecommendationService((itemId, itemName, ttlDays) ->
		{
			calls.incrementAndGet();
			return CompletableFuture.completedFuture(item(itemId, itemName, itemId));
		});
		List<BankEquipmentRecommendationService.BankItemCandidate> candidates = new ArrayList<>();
		for (int itemId = 1; itemId <= 15; itemId++)
		{
			candidates.add(new BankEquipmentRecommendationService.BankItemCandidate(itemId, "Item " + itemId, true, false));
		}

		EquipmentRecommendation recommendation = service.recommend(stabNpc(), candidates, 7).get(5, TimeUnit.SECONDS);

		assertEquals(15, calls.get());
		assertEquals(12, recommendation.getItems().size());
		assertEquals(15, recommendation.getItems().get(0).getItemId());
		assertEquals(4, recommendation.getItems().get(11).getItemId());
		for (int index = 0; index < recommendation.getItems().size(); index++)
		{
			assertEquals(index + 1, recommendation.getItems().get(index).getRank());
		}
	}

	private static NpcCombatInfo stabNpc()
	{
		return NpcCombatInfo.builder()
			.stabDefence("1")
			.slashDefence("20")
			.crushDefence("30")
			.magicDefence("40")
			.lightRangedDefence("50")
			.standardRangedDefence("60")
			.heavyRangedDefence("70")
			.build();
	}

	private static ItemInspectInfo item(int itemId, String name, int attackStab)
	{
		return ItemInspectInfo.builder()
			.itemId(itemId)
			.displayName(name)
			.slot("Weapon")
			.attackStab(Integer.toString(attackStab))
			.build();
	}
}

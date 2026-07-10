package com.inspect.npc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.inspect.item.ItemInspectInfo;
import java.util.Arrays;
import org.junit.Test;

public class EquipmentRecommendationTest
{
	@Test
	public void recommendsWeakestNpcDefenceStyle()
	{
		NpcCombatInfo npc = NpcCombatInfo.builder()
			.stabDefence("80")
			.slashDefence("70")
			.crushDefence("5")
			.magicDefence("60")
			.lightRangedDefence("40")
			.standardRangedDefence("50")
			.heavyRangedDefence("55")
			.build();

		assertEquals("Crush melee", EquipmentRecommendation.preview(npc).getStyleName());
	}

	@Test
	public void ranksBankItemsForRecommendedStyle()
	{
		NpcCombatInfo npc = NpcCombatInfo.builder()
			.stabDefence("10")
			.slashDefence("70")
			.crushDefence("80")
			.magicDefence("60")
			.lightRangedDefence("40")
			.standardRangedDefence("50")
			.heavyRangedDefence("55")
			.build();

		EquipmentRecommendation recommendation = EquipmentRecommendation.fromBank(npc, Arrays.asList(
			item(1, "Weak stab weapon", "Weapon", "12", "5"),
			item(2, "Strong stab weapon", "Weapon", "20", "10"),
			ItemInspectInfo.builder().itemId(3).displayName("Ranged item").slot("Weapon").attackRanged("99").build()
		), 10);

		assertEquals("Stab melee", recommendation.getStyleName());
		assertEquals(2, recommendation.getItems().size());
		assertEquals("Strong stab weapon", recommendation.getItems().get(0).getDisplayName());
		assertTrue(recommendation.itemIds().contains(2));
	}

	@Test
	public void keepsEquippedSourceAndOnlyRanksBankItemsForOverlay()
	{
		NpcCombatInfo npc = NpcCombatInfo.builder()
			.stabDefence("10")
			.slashDefence("70")
			.crushDefence("80")
			.magicDefence("60")
			.lightRangedDefence("40")
			.standardRangedDefence("50")
			.heavyRangedDefence("55")
			.build();

		EquipmentRecommendation recommendation = EquipmentRecommendation.fromCandidates(npc, Arrays.asList(
			new EquipmentRecommendation.CandidateItem(item(1, "Equipped stab weapon", "Weapon", "30", "10"), false, true),
			new EquipmentRecommendation.CandidateItem(item(2, "Bank stab weapon", "Weapon", "20", "10"), true, false)
		), 10);

		assertEquals("Equipped stab weapon", recommendation.getItems().get(0).getDisplayName());
		assertTrue(recommendation.getItems().get(0).isEquipped());
		assertEquals(Integer.valueOf(2), recommendation.bankItemRanks().get(2));
		assertEquals(1, recommendation.bankItemRanks().size());
	}

	@Test
	public void ranksByWeightedScoreFiltersIrrelevantItemsAndAppliesLimit()
	{
		NpcCombatInfo npc = stabWeakNpc();

		EquipmentRecommendation recommendation = EquipmentRecommendation.fromBank(npc, Arrays.asList(
			item(1, "Accuracy weapon", "Weapon", "20", "0"),
			item(2, "Strength weapon", "Weapon", "1", "14"),
			item(3, "Balanced weapon", "Weapon", "12", "4"),
			ItemInspectInfo.builder().itemId(4).displayName("Ranged-only weapon").attackRanged("100").build()
		), 2);

		assertEquals(2, recommendation.getItems().size());
		assertEquals("Strength weapon", recommendation.getItems().get(0).getDisplayName());
		assertEquals(1, recommendation.getItems().get(0).getRank());
		assertEquals("Accuracy weapon", recommendation.getItems().get(1).getDisplayName());
		assertEquals(2, recommendation.getItems().get(1).getRank());
		assertFalse(recommendation.itemIds().contains(4));
	}

	@Test
	public void breaksScoreTiesByNameAndRanksConsecutively()
	{
		EquipmentRecommendation recommendation = EquipmentRecommendation.fromBank(stabWeakNpc(), Arrays.asList(
			item(1, "Zamorak spear", "Weapon", "10", "0"),
			item(2, "Abyssal dagger", "Weapon", "10", "0")
		), 10);

		assertEquals("Abyssal dagger", recommendation.getItems().get(0).getDisplayName());
		assertEquals(1, recommendation.getItems().get(0).getRank());
		assertEquals("Zamorak spear", recommendation.getItems().get(1).getDisplayName());
		assertEquals(2, recommendation.getItems().get(1).getRank());
	}

	@Test
	public void nonPositiveLimitReturnsNoItems()
	{
		ItemInspectInfo item = item(1, "Stab weapon", "Weapon", "10", "5");

		assertFalse(EquipmentRecommendation.fromBank(stabWeakNpc(), Arrays.asList(item), 0).hasItems());
		assertFalse(EquipmentRecommendation.fromBank(stabWeakNpc(), Arrays.asList(item), -1).hasItems());
	}

	@Test
	public void missingDefenceDataReturnsRecommendationWithoutStyleOrItems()
	{
		EquipmentRecommendation recommendation = EquipmentRecommendation.fromBank(
			NpcCombatInfo.builder().build(),
			Arrays.asList(item(1, "Stab weapon", "Weapon", "10", "5")),
			10);

		assertNull(recommendation.getStyleName());
		assertFalse(recommendation.hasItems());
	}

	private static NpcCombatInfo stabWeakNpc()
	{
		return NpcCombatInfo.builder()
			.stabDefence("10")
			.slashDefence("70")
			.crushDefence("80")
			.magicDefence("60")
			.lightRangedDefence("40")
			.standardRangedDefence("50")
			.heavyRangedDefence("55")
			.build();
	}

	private static ItemInspectInfo item(int itemId, String name, String slot, String attackStab, String strength)
	{
		return ItemInspectInfo.builder()
			.itemId(itemId)
			.displayName(name)
			.slot(slot)
			.attackStab(attackStab)
			.strength(strength)
			.build();
	}
}

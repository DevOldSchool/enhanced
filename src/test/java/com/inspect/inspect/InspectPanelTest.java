package com.inspect.inspect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.inspect.item.ItemInspectInfo;
import com.inspect.item.ItemRequirementSummary;
import com.inspect.item.ItemSource;
import com.inspect.item.ItemSourceRequirement;
import com.inspect.npc.EquipmentRecommendation;
import com.inspect.npc.NpcCombatInfo;
import com.inspect.npc.NpcItemRequirement;
import com.inspect.npc.NpcItemRequirementAlternativeStatus;
import com.inspect.npc.NpcItemRequirementStatus;
import com.inspect.player.PlayerEquipmentItem;
import com.inspect.player.PlayerInspectAnalysis;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.JTextArea;
import org.junit.Test;

public class InspectPanelTest
{
	@Test
	public void redactsEquipmentDetailsInPvpAreas() throws Exception
	{
		UiSnapshot snapshot = onEdt(() ->
		{
			// A null item manager makes the test fail if the blocked view requests a gear image.
			InspectPanel panel = new InspectPanel(null, null);
			PlayerEquipmentItem equipment = new PlayerEquipmentItem("Weapon", 4151, "Abyssal whip", 2_000_000);
			PlayerInspectAnalysis analysis = PlayerInspectAnalysis.message(
				"2,000,000 coins",
				"Abyssal whip is stronger than your weapon");

			panel.showPlayerEquipment(
				"Opponent",
				126,
				Collections.singletonList(equipment),
				analysis,
				true,
				Collections.emptyList(),
				Collections.emptyList());
			return UiSnapshot.capture(panel);
		});

		assertTrue(snapshot.text.contains("Player equipment inspect is disabled in PvP areas."));
		assertTrue(snapshot.text.contains("Disabled in PvP"));
		assertFalse(snapshot.text.contains("Abyssal whip"));
		assertFalse(snapshot.text.contains("2,000,000 coins"));
		assertFalse(snapshot.text.contains("Visible tags"));
		assertFalse(snapshot.text.contains("Melee"));
		assertFalse(snapshot.toolTips.contains("Abyssal whip"));
		assertFalse(snapshot.popupActions.contains("Inspect item"));
		assertEquals(0, snapshot.equipmentImageComponentCount);
	}

	@Test
	public void rendersVisibleEquipmentDetailsOutsidePvpAreas() throws Exception
	{
		UiSnapshot snapshot = onEdt(() ->
		{
			InspectPanel panel = new InspectPanel(null, null);
			PlayerEquipmentItem equipment = new PlayerEquipmentItem("Weapon", 0, "Abyssal whip", 2_000_000);

			panel.showPlayerEquipment(
				"Teammate",
				100,
				Collections.singletonList(equipment),
				PlayerInspectAnalysis.message("2,000,000 coins", null),
				false,
				Collections.emptyList(),
				Collections.emptyList());
			return UiSnapshot.capture(panel);
		});

		assertTrue(snapshot.text.contains("Visible tags"));
		assertTrue(snapshot.text.contains("Melee"));
		assertTrue(snapshot.text.contains("2,000,000 coins"));
		assertTrue(snapshot.toolTips.contains("Abyssal whip"));
		assertTrue(snapshot.equipmentImageComponentCount > 0);
	}

	@Test
	public void rendersNpcItemRequirementStatuses() throws Exception
	{
		UiSnapshot snapshot = onEdt(() ->
		{
			InspectPanel panel = new InspectPanel(null, null);
			NpcCombatInfo info = NpcCombatInfo.builder()
				.displayName("Gargoyle")
				.itemRequirements(Collections.singletonList(new NpcItemRequirement("Rock hammer or Granite hammer", Arrays.asList("Rock hammer", "Granite hammer"))))
				.build();

			panel.showInfo(info, EquipmentRecommendation.preview(info), null, Collections.singletonList(new NpcItemRequirementStatus(
				info.getItemRequirements().get(0),
				Arrays.asList(
					new NpcItemRequirementAlternativeStatus("Rock hammer", false, null),
					new NpcItemRequirementAlternativeStatus("Granite hammer", true, "Equipped")
				)
			)));
			return UiSnapshot.capture(panel);
		});

		assertTrue(snapshot.text.contains("Required items"));
		assertTrue(snapshot.text.contains("Any one of these"));
		assertTrue(snapshot.text.contains("Rock hammer"));
		assertTrue(snapshot.text.contains("Missing"));
		assertTrue(snapshot.text.contains("OR"));
		assertTrue(snapshot.text.contains("Granite hammer"));
		assertTrue(snapshot.text.contains("Equipped"));
		assertTrue(snapshot.text.contains("Can I kill this?"));
		assertFalse(snapshot.text.contains("Rock hammer or Granite hammer"));
	}

	@Test
	public void rendersAssignedByTagsAsWikiLinks() throws Exception
	{
		UiSnapshot snapshot = onEdt(() ->
		{
			InspectPanel panel = new InspectPanel(null, null);
			NpcCombatInfo info = NpcCombatInfo.builder()
				.displayName("Abyssal demon")
				.assignedBy("Vannaka, Chaeldar, Konar quo Maten")
				.build();

			panel.showInfo(info, EquipmentRecommendation.preview(info), null, Collections.emptyList());
			return UiSnapshot.capture(panel);
		});

		assertTrue(snapshot.text.contains("Assigned by"));
		assertTrue(snapshot.text.contains("Vannaka"));
		assertTrue(snapshot.text.contains("Chaeldar"));
		assertTrue(snapshot.text.contains("Konar Quo Maten"));
		assertTrue(snapshot.toolTips.contains("https://oldschool.runescape.wiki/w/Vannaka"));
		assertTrue(snapshot.toolTips.contains("https://oldschool.runescape.wiki/w/Chaeldar"));
		assertTrue(snapshot.toolTips.contains("https://oldschool.runescape.wiki/w/Konar_quo_Maten"));
	}

	@Test
	public void rendersNpcDropFilterButtons() throws Exception
	{
		UiSnapshot snapshot = onEdt(() ->
		{
			InspectPanel panel = new InspectPanel(null, null);
			NpcCombatInfo info = NpcCombatInfo.builder()
				.displayName("Abyssal demon")
				.valuableDrops("Abyssal whip, Brimstone key")
				.rareDrops("Abyssal whip")
				.slayerOnlyDrops("Brimstone key")
				.clueDrops("Clue scroll (hard)")
				.ironmanDrops("Grimy ranarr weed")
				.alchableDrops("Adamant platebody")
				.upgradeDrops("Abyssal head")
				.build();

			panel.showInfo(info, EquipmentRecommendation.preview(info), null, Collections.emptyList());
			return UiSnapshot.capture(panel);
		});

		assertTrue(snapshot.text.contains("Drop filters"));
		assertTrue(snapshot.text.contains("Valuable"));
		assertTrue(snapshot.text.contains("Rare"));
		assertTrue(snapshot.text.contains("Slayer-only"));
		assertTrue(snapshot.text.contains("Clue"));
		assertTrue(snapshot.text.contains("Ironman"));
		assertTrue(snapshot.text.contains("Alchable"));
		assertTrue(snapshot.text.contains("Upgrade"));
		assertTrue(snapshot.text.contains("Abyssal whip"));
		assertTrue(snapshot.text.contains("Brimstone key"));
		assertFalse(snapshot.text.contains("Abyssal whip, Brimstone key"));
	}

	@Test
	public void switchingNpcDropFiltersRerendersDropRows() throws Exception
	{
		UiSnapshot snapshot = onEdt(() ->
		{
			InspectPanel panel = new InspectPanel(null, null);
			NpcCombatInfo info = NpcCombatInfo.builder()
				.displayName("Abyssal demon")
				.valuableDrops("Abyssal whip, Brimstone key")
				.rareDrops("Abyssal whip")
				.ironmanDrops("Grimy ranarr weed")
				.build();

			panel.showInfo(info, EquipmentRecommendation.preview(info), null, Collections.emptyList());
			clickButton(panel, "Ironman");
			return UiSnapshot.capture(panel);
		});

		assertTrue(snapshot.text.contains("Drop filters"));
		assertTrue(snapshot.text.contains("Ironman"));
		assertTrue(snapshot.text.contains("Grimy ranarr weed"));
		assertFalse(snapshot.text.contains("Abyssal whip"));
		assertFalse(snapshot.text.contains("Brimstone key"));
	}

	@Test
	public void rendersNpcDropRowsFromPrecomputedItemIdsWithoutResolvingDefinitions() throws Exception
	{
		UiSnapshot snapshot = onEdt(() ->
		{
			InspectPanel panel = new InspectPanel(null, null);
			NpcCombatInfo info = NpcCombatInfo.builder()
				.displayName("Abyssal demon")
				.valuableDrops("Abyssal whip")
				.build();
			Map<String, Integer> dropItemIds = new LinkedHashMap<>();
			dropItemIds.put("abyssal whip", 4151);

			panel.showInfo(info, EquipmentRecommendation.preview(info), null, Collections.emptyList(), dropItemIds);
			return UiSnapshot.capture(panel);
		});

		assertTrue(snapshot.text.contains("Drop filters"));
		assertTrue(snapshot.text.contains("Abyssal whip"));
		assertTrue(snapshot.popupActions.contains("Inspect item"));
	}

	@Test
	public void unresolvedNpcDropRowsRenderWithoutInspectPopup() throws Exception
	{
		UiSnapshot snapshot = onEdt(() ->
		{
			InspectPanel panel = new InspectPanel(null, null);
			NpcCombatInfo info = NpcCombatInfo.builder()
				.displayName("Kurask")
				.valuableDrops("Leaf-bladed battleaxe")
				.build();

			panel.showInfo(info, EquipmentRecommendation.preview(info), null, Collections.emptyList(), Collections.emptyMap());
			return UiSnapshot.capture(panel);
		});

		assertTrue(snapshot.text.contains("Drop filters"));
		assertTrue(snapshot.text.contains("Leaf-bladed battleaxe"));
		assertFalse(snapshot.popupActions.contains("Inspect item"));
	}

	@Test
	public void rendersSavedNpcCompareTrayAndComparison() throws Exception
	{
		UiSnapshot snapshot = onEdt(() ->
		{
			InspectPanel panel = new InspectPanel(null, null);
			NpcCombatInfo pinned = NpcCombatInfo.builder()
				.displayName("Gargoyle")
				.combatLevel("111")
				.hitpoints("105")
				.maxHit("11")
				.attack("120")
				.build();
			NpcCombatInfo current = NpcCombatInfo.builder()
				.displayName("Abyssal demon")
				.combatLevel("124")
				.hitpoints("150")
				.maxHit("8")
				.attack("97")
				.build();

			panel.setPinnedInspects(PinnedInspectState.empty().withNpc(pinned));
			panel.showInfo(current, EquipmentRecommendation.preview(current), null, Collections.emptyList());
			return UiSnapshot.capture(panel);
		});

		assertTrue(snapshot.text.contains("Compare"));
		assertTrue(snapshot.text.contains("NPC: Gargoyle"));
		assertTrue(snapshot.text.contains("Compare NPC"));
		assertTrue(snapshot.text.contains("Compared to NPC"));
		assertTrue(snapshot.text.contains("Combat"));
		assertTrue(snapshot.text.contains("+13"));
		assertTrue(snapshot.text.contains("HP"));
		assertTrue(snapshot.text.contains("+45"));
		assertTrue(snapshot.text.contains("Max hit"));
		assertTrue(snapshot.text.contains("-3"));
	}

	@Test
	public void rendersSavedItemCompareTrayAndComparison() throws Exception
	{
		UiSnapshot snapshot = onEdt(() ->
		{
			InspectPanel panel = new InspectPanel(null, null);
			ItemInspectInfo pinned = ItemInspectInfo.builder()
				.itemId(100)
				.displayName("Rune scimitar")
				.attackSlash("45")
				.strength("44")
				.build();
			ItemInspectInfo current = ItemInspectInfo.builder()
				.itemId(101)
				.displayName("Dragon scimitar")
				.attackSlash("67")
				.strength("66")
				.build();

			panel.setPinnedInspects(PinnedInspectState.empty().withItem(pinned));
			panel.showItemInfo(current, null, null, null);
			return UiSnapshot.capture(panel);
		});

		assertTrue(snapshot.text.contains("Compare"));
		assertTrue(snapshot.text.contains("Item: Rune scimitar"));
		assertTrue(snapshot.text.contains("Compare item"));
		assertTrue(snapshot.text.contains("Compared to item"));
		assertTrue(snapshot.text.contains("Slash attack"));
		assertTrue(snapshot.text.contains("Strength"));
		assertTrue(snapshot.text.contains("+22"));
	}

	@Test
	public void rendersItemSourcesAsSingleDetailedSection() throws Exception
	{
		UiSnapshot snapshot = onEdt(() ->
		{
			InspectPanel panel = new InspectPanel(null, null);
			ItemInspectInfo item = ItemInspectInfo.builder()
				.itemId(1079)
				.displayName("Rune platelegs")
				.sourceSummary("Skilling, Quests")
				.sourcePlan(Arrays.asList(
					new ItemSource(
						"Skilling",
						Collections.singletonList("They can be created with level 99 Smithing and 3 runite bars."),
						Collections.singletonList(new ItemSourceRequirement("Smithing", 99, "Skilling"))),
					new ItemSource(
						"Quests",
						Collections.singletonList("It requires 40 Defence and completion of Dragon Slayer I to equip."),
						Collections.singletonList(new ItemSourceRequirement("Defence", 40, "Quests")))
				))
				.questRequirements("Dragon Slayer I")
				.build();

			panel.showItemInfo(item, null, null, null);
			return UiSnapshot.capture(panel);
		});

		assertEquals(1, countOccurrences(snapshot.text, "Sources"));
		assertTrue(snapshot.text.contains("Skilling"));
		assertTrue(snapshot.text.contains("They can be created with level 99 Smithing and 3 runite bars."));
		assertTrue(snapshot.text.contains("Quests"));
		assertTrue(snapshot.text.contains("It requires 40 Defence and completion of Dragon Slayer I to equip."));
		assertFalse(snapshot.text.contains("How to get"));
		assertFalse(snapshot.text.contains("Unlock notes"));
	}

	@Test
	public void rendersItemRequirementsAsSingleReadinessSection() throws Exception
	{
		UiSnapshot snapshot = onEdt(() ->
		{
			InspectPanel panel = new InspectPanel(null, null);
			ItemInspectInfo item = ItemInspectInfo.builder()
				.itemId(1127)
				.displayName("Rune platebody")
				.requirementDefence("40")
				.build();
			ItemRequirementSummary requirements = new ItemRequirementSummary(
				Collections.singletonList("Defence 40 (81)"),
				Collections.singletonList("Smithing 99 for skilling (71)")
			);

			panel.showItemInfo(item, null, requirements, null);
			return UiSnapshot.capture(panel);
		});

		assertEquals(1, countOccurrences(snapshot.text, "Requirements"));
		assertTrue(snapshot.text.contains("Met"));
		assertTrue(snapshot.text.contains("Defence 40 (81)"));
		assertTrue(snapshot.text.contains("Missing"));
		assertTrue(snapshot.text.contains("Smithing 99 for skilling (71)"));
		assertFalse(snapshot.text.contains("Requirement check"));
	}

	@Test
	public void rendersSavedPlayerCompareTrayAndComparison() throws Exception
	{
		UiSnapshot snapshot = onEdt(() ->
		{
			InspectPanel panel = new InspectPanel(null, null);
			PlayerEquipmentItem pinnedWeapon = new PlayerEquipmentItem("Weapon", 0, "Rune scimitar", 15_000);
			PlayerEquipmentItem currentWeapon = new PlayerEquipmentItem("Weapon", 0, "Dragon scimitar", 60_000);

			panel.setPinnedInspects(PinnedInspectState.empty().withPlayer("Pinned player", 90, Collections.singletonList(pinnedWeapon)));
			panel.showPlayerEquipment(
				"Current player",
				100,
				Collections.singletonList(currentWeapon),
				PlayerInspectAnalysis.message("60,000 coins", null),
				false,
				Collections.emptyList(),
				Collections.emptyList());
			return UiSnapshot.capture(panel);
		});

		assertTrue(snapshot.text.contains("Compare"));
		assertTrue(snapshot.text.contains("Player: Pinned player"));
		assertTrue(snapshot.text.contains("Compare player"));
		assertTrue(snapshot.text.contains("Compared to player"));
		assertTrue(snapshot.text.contains("Combat"));
		assertTrue(snapshot.text.contains("+10"));
		assertTrue(snapshot.text.contains("Gear value"));
		assertTrue(snapshot.text.contains("+45000"));
		assertTrue(snapshot.text.contains("Different"));
		assertTrue(snapshot.text.contains("Weapon"));
	}

	@Test
	public void clickingCompareRowsReopensSavedSelections() throws Exception
	{
		AtomicReference<NpcCombatInfo> openedNpc = new AtomicReference<>();
		AtomicReference<ItemInspectInfo> openedItem = new AtomicReference<>();
		AtomicReference<String> openedPlayer = new AtomicReference<>();
		AtomicInteger clearedNpc = new AtomicInteger();

		onEdt(() ->
		{
			InspectPanel panel = new InspectPanel(null, null);
			NpcCombatInfo pinnedNpc = NpcCombatInfo.builder()
				.displayName("Gargoyle")
				.combatLevel("111")
				.build();
			ItemInspectInfo pinnedItem = ItemInspectInfo.builder()
				.itemId(100)
				.displayName("Rune scimitar")
				.build();
			PlayerEquipmentItem pinnedWeapon = new PlayerEquipmentItem("Weapon", 0, "Rune scimitar", 15_000);
			NpcCombatInfo current = NpcCombatInfo.builder()
				.displayName("Abyssal demon")
				.combatLevel("124")
				.build();

			panel.setPinnedInspectHandler(new InspectPanel.PinnedInspectHandler()
			{
				@Override
				public void pinNpc(NpcCombatInfo info)
				{
				}

				@Override
				public void pinItem(ItemInspectInfo info)
				{
				}

				@Override
				public void pinPlayer(String playerName, int combatLevel, java.util.List<PlayerEquipmentItem> equipment)
				{
				}

				@Override
				public void openNpc(NpcCombatInfo info)
				{
					openedNpc.set(info);
				}

				@Override
				public void openItem(ItemInspectInfo info)
				{
					openedItem.set(info);
				}

				@Override
				public void openPlayer(String playerName, int combatLevel, java.util.List<PlayerEquipmentItem> equipment)
				{
					openedPlayer.set(playerName);
				}

				@Override
				public void clearNpc()
				{
					clearedNpc.incrementAndGet();
				}

				@Override
				public void clearItem()
				{
				}

				@Override
				public void clearPlayer()
				{
				}
			});
			panel.setPinnedInspects(PinnedInspectState.empty()
				.withNpc(pinnedNpc)
				.withItem(pinnedItem)
				.withPlayer("Pinned player", 90, Collections.singletonList(pinnedWeapon)));
			panel.showInfo(current, EquipmentRecommendation.preview(current), null, Collections.emptyList());

			clickButton(panel, "NPC: Gargoyle");
			clickButton(panel, "Item: Rune scimitar");
			clickButton(panel, "Player: Pinned player");
			clickButton(panel, "X");
			return null;
		});

		assertEquals("Gargoyle", openedNpc.get().getDisplayName());
		assertEquals("Rune scimitar", openedItem.get().getDisplayName());
		assertEquals("Pinned player", openedPlayer.get());
		assertEquals(1, clearedNpc.get());
	}

	private static <T> T onEdt(Callable<T> action) throws Exception
	{
		AtomicReference<T> result = new AtomicReference<>();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				result.set(action.call());
			}
			catch (Throwable throwable)
			{
				failure.set(throwable);
			}
		});

		if (failure.get() != null)
		{
			throw new AssertionError("Swing action failed", failure.get());
		}
		return result.get();
	}

	private static void clickButton(Component root, String text)
	{
		AbstractButton button = findButton(root, text);
		if (button == null)
		{
			throw new AssertionError("Button not found: " + text);
		}
		button.doClick();
	}

	private static AbstractButton findButton(Component root, String text)
	{
		Deque<Component> components = new ArrayDeque<>();
		components.add(root);
		while (!components.isEmpty())
		{
			Component component = components.removeFirst();
			if (component instanceof AbstractButton && text.equals(((AbstractButton) component).getText()))
			{
				return (AbstractButton) component;
			}
			if (component instanceof Container)
			{
				Collections.addAll(components, ((Container) component).getComponents());
			}
		}
		return null;
	}

	private static int countOccurrences(String value, String needle)
	{
		int count = 0;
		int index = 0;
		while ((index = value.indexOf(needle, index)) >= 0)
		{
			count++;
			index += needle.length();
		}
		return count;
	}

	private static final class UiSnapshot
	{
		private final String text;
		private final String toolTips;
		private final String popupActions;
		private final int equipmentImageComponentCount;

		private UiSnapshot(String text, String toolTips, String popupActions, int equipmentImageComponentCount)
		{
			this.text = text;
			this.toolTips = toolTips;
			this.popupActions = popupActions;
			this.equipmentImageComponentCount = equipmentImageComponentCount;
		}

		private static UiSnapshot capture(Component root)
		{
			StringBuilder text = new StringBuilder();
			StringBuilder toolTips = new StringBuilder();
			StringBuilder popupActions = new StringBuilder();
			int equipmentImageComponentCount = 0;
			Deque<Component> components = new ArrayDeque<>();
			components.add(root);

			while (!components.isEmpty())
			{
				Component component = components.removeFirst();
				if (component instanceof JLabel)
				{
					append(text, ((JLabel) component).getText());
				}
				else if (component instanceof AbstractButton)
				{
					append(text, ((AbstractButton) component).getText());
				}
				else if (component instanceof JTextArea)
				{
					append(text, ((JTextArea) component).getText());
				}

				if (component instanceof JComponent)
				{
					JComponent swingComponent = (JComponent) component;
					append(toolTips, swingComponent.getToolTipText());
					JPopupMenu popupMenu = swingComponent.getComponentPopupMenu();
					if (popupMenu != null)
					{
						components.addLast(popupMenu);
					}
				}

				if (component instanceof JPopupMenu)
				{
					for (Component child : ((JPopupMenu) component).getComponents())
					{
						if (child instanceof AbstractButton)
						{
							append(popupActions, ((AbstractButton) child).getText());
						}
					}
				}

				if ("EquipmentSlotComponent".equals(component.getClass().getSimpleName()))
				{
					equipmentImageComponentCount++;
				}

				if (component instanceof Container)
				{
					Collections.addAll(components, ((Container) component).getComponents());
				}
			}

			return new UiSnapshot(text.toString(), toolTips.toString(), popupActions.toString(), equipmentImageComponentCount);
		}

		private static void append(StringBuilder destination, String value)
		{
			if (value != null)
			{
				destination.append('\n').append(value);
			}
		}
	}
}

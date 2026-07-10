package com.inspect.inspect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.inspect.player.PlayerEquipmentItem;
import com.inspect.player.PlayerInspectAnalysis;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
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

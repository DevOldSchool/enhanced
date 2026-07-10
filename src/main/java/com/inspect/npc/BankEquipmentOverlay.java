package com.inspect.npc;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

@Singleton
public class BankEquipmentOverlay extends WidgetItemOverlay
{
	private static final Color TOP_OUTLINE = new Color(255, 214, 92, 235);
	private static final Color GOOD_OUTLINE = new Color(90, 200, 120, 230);
	private static final Font RANK_FONT = new Font("SansSerif", Font.BOLD, 10);

	private final ItemManager itemManager;
	private Map<Integer, Integer> highlightedItemRanks = Collections.emptyMap();

	@Inject
	BankEquipmentOverlay(ItemManager itemManager)
	{
		this.itemManager = itemManager;
		showOnBank();
	}

	public void setHighlightedItemRanks(Map<Integer, Integer> highlightedItemRanks)
	{
		this.highlightedItemRanks = highlightedItemRanks == null || highlightedItemRanks.isEmpty()
			? Collections.emptyMap()
			: Collections.unmodifiableMap(new LinkedHashMap<>(highlightedItemRanks));
	}

	public void clear()
	{
		highlightedItemRanks = Collections.emptyMap();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		int canonicalItemId = itemManager.canonicalize(itemId);
		Integer rank = highlightedItemRanks.get(canonicalItemId);
		if (rank == null)
		{
			return;
		}

		Rectangle bounds = widgetItem.getCanvasBounds();
		Stroke previous = graphics.getStroke();
		graphics.setColor(rank == 1 ? TOP_OUTLINE : GOOD_OUTLINE);
		graphics.setStroke(new BasicStroke(1f));
		graphics.draw(bounds);
		graphics.setStroke(previous);

		drawRank(graphics, bounds, rank);
	}

	private static void drawRank(Graphics2D graphics, Rectangle bounds, int rank)
	{
		String text = Integer.toString(rank);
		Font previousFont = graphics.getFont();
		graphics.setFont(RANK_FONT);
		int x = bounds.x + bounds.width - graphics.getFontMetrics().stringWidth(text) - 1;
		int y = bounds.y + graphics.getFontMetrics().getAscent();

		graphics.setColor(Color.BLACK);
		graphics.drawString(text, x + 1, y + 1);
		graphics.setColor(rank == 1 ? TOP_OUTLINE : GOOD_OUTLINE);
		graphics.drawString(text, x, y);
		graphics.setFont(previousFont);
	}
}

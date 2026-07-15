package com.inspect.inspect;

import com.inspect.item.ItemInspectInfo;
import com.inspect.item.ItemRequirementSummary;
import com.inspect.item.ItemPriceSummary;
import com.inspect.npc.CombatStyleRecommendation;
import com.inspect.npc.EquipmentRecommendation;
import com.inspect.npc.NpcCombatInfo;
import com.inspect.npc.NpcItemRequirementAlternativeStatus;
import com.inspect.npc.NpcItemRequirementStatus;
import com.inspect.player.PlayerEquipmentComparison;
import com.inspect.player.PlayerEquipmentItem;
import com.inspect.player.PlayerInspectAnalysis;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;
import javax.inject.Inject;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import lombok.Setter;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.LinkBrowser;

public class InspectPanel extends PluginPanel
{
	private static final String DASH = "--";
	private static final String ICON_PATH = "/com/inspect/icons/";
	private static final String STATUS_CHECK_ICON = "status-check.png";
	private static final String STATUS_CROSS_ICON = "status-cross.png";
	private static final String EQUIPMENT_SLOT_PATH = "/com/inspect/equipment-slots/";
	private static final int MESSAGE_HTML_WIDTH = 190;
	private static final DecimalFormat DELTA_FORMAT = new DecimalFormat("0.###");
	private final SpriteManager spriteManager;
	private final ItemManager itemManager;
	@Setter
    private SearchHandler searchHandler;
	@Setter
    private GearRecommendationHandler gearRecommendationHandler;
	@Setter
    private ItemInspectHandler itemInspectHandler;
	@Setter
    private RecentInspectHandler recentInspectHandler;
	@Setter
    private CacheManagementHandler cacheManagementHandler;
	@Setter
	private PinnedInspectHandler pinnedInspectHandler;
	private String activeTab = "Item";
	private String activeDropFilter = "Valuable";
	private PinnedInspectState pinnedInspects = PinnedInspectState.empty();
	private List<String> lastRecentNpcs = new ArrayList<>();
	private List<String> lastRecentPlayers = new ArrayList<>();
	private List<RecentInspectEntry> lastRecentItems = new ArrayList<>();
	private Runnable lastItemRenderer;
	private Runnable lastNpcRenderer;
	private Runnable lastPlayerRenderer;
	private String lastSearchText = "";
	private String lastSearchType = "Item";

	@Inject
	public InspectPanel(SpriteManager spriteManager, ItemManager itemManager)
	{
		this.spriteManager = spriteManager;
		this.itemManager = itemManager;
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(8, 8, 8, 8));
		showEmpty();
	}

    public void setRecentNpcs(List<String> recentNpcs)
	{
		lastRecentNpcs = recentNpcs == null ? new ArrayList<>() : new ArrayList<>(recentNpcs);
	}

	public void setRecentItems(List<RecentInspectEntry> recentItems)
	{
		lastRecentItems = recentItems == null ? new ArrayList<>() : new ArrayList<>(recentItems);
	}

	public void setPinnedInspects(PinnedInspectState pinnedInspects)
	{
		this.pinnedInspects = pinnedInspects == null ? PinnedInspectState.empty() : pinnedInspects;
	}

	public void refreshActiveView()
	{
		if ("NPC".equals(activeTab) && lastNpcRenderer != null)
		{
			lastNpcRenderer.run();
			return;
		}
		if ("Item".equals(activeTab) && lastItemRenderer != null)
		{
			lastItemRenderer.run();
			return;
		}
		if ("Player".equals(activeTab) && lastPlayerRenderer != null)
		{
			lastPlayerRenderer.run();
			return;
		}
		if ("Recent".equals(activeTab))
		{
			showRecentOnly();
		}
	}

    public void showEmpty()
	{
		activeTab = "Item";
		reset();
		addFullWidth(message("Search above, or right-click an NPC or item and choose Inspect."));
		addCacheManagement();
		refresh();
	}

	public void showSearchLoading(String type, String query)
	{
		activeTab = type;
		reset();
		addFullWidth(searchTitle(type + " Search"));
		addFullWidth(message("Searching " + query + "..."));
		refresh();
	}

	public void showSearchNotFound(String type, String query)
	{
		activeTab = type;
		reset();
		addFullWidth(searchTitle(type + " Search"));
		addFullWidth(message("No " + type.toLowerCase() + " info was found for " + query + "."));
		refresh();
	}

	public void showSearchError(String type, String query)
	{
		activeTab = type;
		reset();
		addFullWidth(searchTitle(type + " Search"));
		addErrorMessage(query + ": Unable to load info from the OSRS Wiki.", "Wiki search failed for " + type + ": " + query);
		refresh();
	}

	public void showSearchDisabled(String disabledMessage)
	{
		reset();
		addFullWidth(title("Inspect Search"));
		addFullWidth(message(disabledMessage));
		refresh();
	}

	public boolean isNpcActive()
	{
		return "NPC".equals(activeTab);
	}

	public void showLoading(String npcName)
	{
		activeTab = "NPC";
		reset();
		addFullWidth(title("NPC Inspect"));
		addFullWidth(message("Loading " + npcName + "..."));
		refresh();
	}

	public void showNotFound(String npcName)
	{
		reset();
		addFullWidth(title("NPC Inspect"));
		addFullWidth(message("No combat info was found for " + npcName + "."));
		refresh();
	}

	public void showError(String npcName, String message)
	{
		reset();
		addFullWidth(title("NPC Inspect"));
		addErrorMessage(npcName + ": " + message, message);
		refresh();
	}

	public void showItemLoading(String itemName)
	{
		activeTab = "Item";
		reset();
		addFullWidth(title("Item Inspect"));
		addFullWidth(message("Loading " + itemName + "..."));
		refresh();
	}

	public void showItemNotFound(String itemName)
	{
		reset();
		addFullWidth(title("Item Inspect"));
		addFullWidth(message("No item info was found for " + itemName + "."));
		refresh();
	}

	public void showItemError(String itemName, String message)
	{
		reset();
		addFullWidth(title("Item Inspect"));
		addErrorMessage(itemName + ": " + message, message);
		refresh();
	}

	public void showInfo(NpcCombatInfo info)
	{
		showInfo(info, EquipmentRecommendation.preview(info), null);
	}

	public void showInfo(NpcCombatInfo info, EquipmentRecommendation recommendation, String recommendationMessage)
	{
		showInfo(info, recommendation, recommendationMessage, Collections.emptyList());
	}

	public void showInfo(NpcCombatInfo info, EquipmentRecommendation recommendation, String recommendationMessage,
		List<NpcItemRequirementStatus> itemRequirementStatuses)
	{
		showInfo(info, recommendation, recommendationMessage, itemRequirementStatuses, Collections.emptyMap());
	}

	public void showInfo(NpcCombatInfo info, EquipmentRecommendation recommendation, String recommendationMessage,
		List<NpcItemRequirementStatus> itemRequirementStatuses, Map<String, Integer> dropItemIds)
	{
		List<NpcItemRequirementStatus> statuses = itemRequirementStatuses == null
			? Collections.emptyList()
			: new ArrayList<>(itemRequirementStatuses);
		Map<String, Integer> itemIds = dropItemIds == null
			? Collections.emptyMap()
			: new LinkedHashMap<>(dropItemIds);
		lastNpcRenderer = () -> renderNpcInfo(info, recommendation, recommendationMessage, statuses, itemIds);
		renderNpcInfo(info, recommendation, recommendationMessage, statuses, itemIds);
	}

	private void renderNpcInfo(NpcCombatInfo info, EquipmentRecommendation recommendation, String recommendationMessage,
		List<NpcItemRequirementStatus> itemRequirementStatuses, Map<String, Integer> dropItemIds)
	{
		activeTab = "NPC";
		reset();
		addFullWidth(title(info.valueOrDash(info.getDisplayName())));
		addPinNpcButton(info);
		addFullWidth(section("Combat info"));
		addFullWidth(rows(
			row("Combat level", info.getCombatLevel()),
			row("XP bonus", info.getXpBonus()),
			row("Max hit", info.getMaxHit()),
			row("Aggressive", info.getAggressive()),
			row("Poisonous", info.getPoisonous()),
			row("Attack style", info.getAttackStyle()),
			row("Attack speed", info.getAttackSpeed()),
			row("Respawn time", info.getRespawnTime())
		));

		addFullWidth(section("Combat stats"));
		addFullWidth(grid(new StatCell[]{
			iconCell("Hitpoints", "hitpoints.png", info.getHitpoints()),
			iconCell("Attack", "attack.png", info.getAttack()),
			iconCell("Strength", "strength.png", info.getStrength()),
			iconCell("Defence", "defence.png", info.getDefence()),
			iconCell("Magic", "magic.png", info.getMagic()),
			iconCell("Ranged", "ranged.png", info.getRanged())
		}, 6));

		addFullWidth(section("Aggressive stats"));
		addFullWidth(grid(new StatCell[]{
			iconCell("Attack bonus", "white-dagger.png", info.getAttackBonus()),
			iconCell("Strength bonus", "strength.png", info.getStrengthBonus()),
			iconCell("Magic attack", "magic.png", info.getMagicAttack()),
			iconCell("Magic strength", "magic-damage.png", info.getMagicStrength()),
			iconCell("Ranged attack", "ranged.png", info.getRangedAttack()),
			iconCell("Ranged strength", "ranged-strength.png", info.getRangedStrength())
		}, 6));

		addFullWidth(section("Melee defence"));
		addFullWidth(grid(new StatCell[]{
			iconCell("Stab defence", "white-dagger.png", info.getStabDefence()),
			iconCell("Slash defence", "white-scimitar.png", info.getSlashDefence()),
			iconCell("Crush defence", "white-warhammer.png", info.getCrushDefence())
		}, 3));

		addFullWidth(section("Magic defence"));
		addFullWidth(grid(new StatCell[]{
			iconCell("Magic defence", "magic-defence.png", info.getMagicDefence()),
			itemCell("Elemental weakness", ItemID.BLANKRUNE_HIGH, info.getElementalWeakness())
		}, 2));

		addFullWidth(section("Ranged defence"));
		addFullWidth(grid(new StatCell[]{
			iconCell("Light ranged defence", "steel-dart.png", info.getLightRangedDefence()),
			iconCell("Standard ranged defence", "steel-arrow-5.png", info.getStandardRangedDefence()),
			iconCell("Heavy ranged defence", "steel-bolts-5.png", info.getHeavyRangedDefence())
		}, 3));

		addFullWidth(section("Immunities"));
		addFullWidth(rows(
			row("Poison", info.getPoisonResistance()),
			row("Venom", info.getVenomResistance()),
			row("Cannons", info.getCannonImmunity()),
			row("Thralls", info.getThrallImmunity())
		));

		addNpcWeaknessSummary(info, recommendation);
		addPinnedNpcComparison(info);
		addSlayerHelper(info);
		addDropSummary(info, dropItemIds);
		addRequiredItems(itemRequirementStatuses);
		addKillChecklist(info);
		addEquipmentRecommendation(info, recommendation, recommendationMessage);

		if (info.getSourceUrl() != null)
		{
			addFullWidth(sourceButton(info.getSourceUrl()));
		}
		addRecentNpcInspects();
		refresh();
	}

	public void showItemInfo(ItemInspectInfo info)
	{
		showItemInfo(info, null, null, null);
	}

	public void showItemInfo(ItemInspectInfo info, ItemInspectInfo equippedInfo, ItemRequirementSummary requirementSummary, ItemPriceSummary priceSummary)
	{
		lastItemRenderer = () -> renderItemInfo(info, equippedInfo, requirementSummary, priceSummary);
		renderItemInfo(info, equippedInfo, requirementSummary, priceSummary);
	}

	private void renderItemInfo(ItemInspectInfo info, ItemInspectInfo equippedInfo, ItemRequirementSummary requirementSummary, ItemPriceSummary priceSummary)
	{
		activeTab = "Item";
		reset();
		addFullWidth(title(valueOrDash(info.getDisplayName())));
		addPinItemButton(info);
		addFullWidth(section("Item info"));
		addFullWidth(grid(new StatCell[]{
			itemCell("Item", info.getItemId(), "")
		}, 1));
		addFullWidth(rows(
			row("Members", info.getMembers()),
			row("Tradeable", info.getTradeable()),
			row("Equipable", info.getEquipable()),
			row("Stackable", info.getStackable()),
			row("Noteable", info.getNoteable()),
			row("Weight", info.getWeight()),
			row("Slot", info.getSlot()),
			row("Attack speed", info.getAttackSpeed()),
			row("Attack range", info.getAttackRange())
		));
		addPriceSummary(priceSummary);
		addItemTags(info);
		addItemSource(info);

		if (hasAny(info.getAttackStab(), info.getAttackSlash(), info.getAttackCrush(), info.getAttackMagic(), info.getAttackRanged()))
		{
			addFullWidth(section("Attack bonuses"));
			addFullWidth(grid(new StatCell[]{
				cell("Stab attack", SpriteID.Combaticons.SWORD_STAB, info.getAttackStab()),
				cell("Slash attack", SpriteID.Combaticons.SWORD_SLASH, info.getAttackSlash()),
				cell("Crush attack", SpriteID.Combaticons2.HAMMER_POUND, info.getAttackCrush()),
				cell("Magic attack", SpriteID.Staticons.MAGIC, info.getAttackMagic()),
				cell("Ranged attack", SpriteID.Staticons.RANGED, info.getAttackRanged())
			}, 5));
		}

		if (hasAny(info.getDefenceStab(), info.getDefenceSlash(), info.getDefenceCrush(), info.getDefenceMagic(), info.getDefenceRanged()))
		{
			addFullWidth(section("Defence bonuses"));
			addFullWidth(grid(new StatCell[]{
				cell("Stab defence", SpriteID.Combaticons.SWORD_STAB, info.getDefenceStab()),
				cell("Slash defence", SpriteID.Combaticons.SWORD_SLASH, info.getDefenceSlash()),
				cell("Crush defence", SpriteID.Combaticons2.HAMMER_POUND, info.getDefenceCrush()),
				cell("Magic defence", SpriteID.Staticons.MAGIC, info.getDefenceMagic()),
				cell("Ranged defence", SpriteID.Staticons.RANGED, info.getDefenceRanged())
			}, 5));
		}

		if (hasAny(info.getStrength(), info.getRangedStrength(), info.getMagicDamage(), info.getPrayer()))
		{
			addFullWidth(section("Other bonuses"));
			addFullWidth(grid(new StatCell[]{
				cell("Strength", SpriteID.Staticons.STRENGTH, info.getStrength()),
				cell("Ranged strength", SpriteID.Combaticons2.BOW_ACCURATE, info.getRangedStrength()),
				cell("Magic damage", SpriteID.Combaticons2.MAGIC_ACCURATE, info.getMagicDamage()),
				cell("Prayer", SpriteID.Staticons.PRAYER, info.getPrayer())
			}, 4));
		}

		if (hasAny(info.getRequirementAttack(), info.getRequirementStrength(), info.getRequirementDefence(), info.getRequirementRanged(),
			info.getRequirementMagic(), info.getRequirementPrayer(), info.getRequirementHitpoints(), info.getRequirementSlayer()))
		{
			addFullWidth(section("Requirements"));
			addFullWidth(rows(nonEmptyRows(
				"Attack", info.getRequirementAttack(),
				"Strength", info.getRequirementStrength(),
				"Defence", info.getRequirementDefence(),
				"Ranged", info.getRequirementRanged(),
				"Magic", info.getRequirementMagic(),
				"Prayer", info.getRequirementPrayer(),
				"Hitpoints", info.getRequirementHitpoints(),
				"Slayer", info.getRequirementSlayer()
			)));
		}
		addRequirementSummary(requirementSummary);
		if (info.getQuestRequirements() != null)
		{
			addFullWidth(section("Unlock notes"));
			addFullWidth(rows(row("Requires", info.getQuestRequirements())));
		}

		addComparison(info, equippedInfo);
		addPinnedItemComparison(info);

		if (info.getExamine() != null)
		{
			addFullWidth(section("Examine"));
			addFullWidth(message(info.getExamine()));
		}

		if (info.getSourceUrl() != null)
		{
			addFullWidth(sourceButton(info.getSourceUrl()));
		}
		addRecentItemInspects();
		refresh();
	}

	public void showPlayerEquipment(String playerName, int combatLevel, List<PlayerEquipmentItem> equipment,
		PlayerInspectAnalysis analysis, boolean pvpBlocked, List<String> recentPlayers, List<RecentInspectEntry> recentItems)
	{
		lastPlayerRenderer = () -> renderPlayerEquipment(playerName, combatLevel, equipment, analysis, pvpBlocked, recentPlayers, recentItems);
		renderPlayerEquipment(playerName, combatLevel, equipment, analysis, pvpBlocked, recentPlayers, recentItems);
	}

	private void renderPlayerEquipment(String playerName, int combatLevel, List<PlayerEquipmentItem> equipment,
		PlayerInspectAnalysis analysis, boolean pvpBlocked, List<String> recentPlayers, List<RecentInspectEntry> recentItems)
	{
		activeTab = "Player";
		storeRecentPlayersAndItems(recentPlayers, recentItems);
		reset();
		addFullWidth(title(playerName == null || playerName.isEmpty() ? "Player" : playerName));
		addPinPlayerButton(playerName, combatLevel, equipment);
		addFullWidth(playerSummary(combatLevel, analysis, pvpBlocked));

		if (pvpBlocked)
		{
			addFullWidth(blockedEquipmentPanel());
			addFullWidth(message("Player equipment inspect is disabled in PvP areas."));
		}
		else
		{
			addPlayerGearTags(equipment);
			addFullWidth(equipmentLayout(equipment, comparisonsBySlot(analysis)));
			addPinnedPlayerComparison(playerName, combatLevel, equipment);
			addPlayerComparison(analysis);
		}
		addRecentPlayerInspects();
		refresh();
	}

	private void addEquipmentRecommendation(NpcCombatInfo info, EquipmentRecommendation recommendation, String message)
	{
		if (recommendation == null || recommendation.getStyleName() == null)
		{
			return;
		}

		addFullWidth(section("Equipment recommendation"));
		List<JPanel> rows = new ArrayList<>();
		rows.add(row("Style", recommendation.getStyleName()));
		rows.add(row("Based on", recommendation.getDefenceLabel()));
		if (message != null && !message.isEmpty())
		{
			rows.add(row("Bank", message));
		}
		if (recommendation.hasItems())
		{
			int index = 1;
			for (EquipmentRecommendation.RecommendedItem item : recommendation.getItems())
			{
				rows.add(row(index + ". " + valueOrDash(item.getSlot()), item.getDisplayName() + sourceLabel(item) + scoreLabel(item)));
				index++;
			}
		}
		addFullWidth(rows(rows.toArray(new JPanel[0])));

		JButton button = new JButton("Find gear in bank");
		stylePanelButton(button, ColorScheme.BRAND_ORANGE);
		button.addActionListener(event ->
		{
			if (gearRecommendationHandler != null)
			{
				gearRecommendationHandler.findGear(info);
			}
		});
		addFullWidth(button);

		if (recommendation.hasItems())
		{
			JButton clearButton = new JButton("Clear selection");
			stylePanelButton(clearButton, ColorScheme.LIGHT_GRAY_COLOR);
			clearButton.addActionListener(event ->
			{
				if (gearRecommendationHandler != null)
				{
					gearRecommendationHandler.clearGear(info);
				}
			});
			addFullWidth(clearButton);
		}
	}

	private void addPinNpcButton(NpcCombatInfo info)
	{
		JButton button = panelButton("Compare NPC");
		button.setToolTipText("Use this NPC as the saved comparison for future NPC inspections.");
		button.addActionListener(event ->
		{
			if (pinnedInspectHandler != null)
			{
				pinnedInspectHandler.pinNpc(info);
			}
		});
		addFullWidth(button);
	}

	private void addPinItemButton(ItemInspectInfo info)
	{
		JButton button = panelButton("Compare item");
		button.setToolTipText("Use this item as the saved comparison for future item inspections.");
		button.addActionListener(event ->
		{
			if (pinnedInspectHandler != null)
			{
				pinnedInspectHandler.pinItem(info);
			}
		});
		addFullWidth(button);
	}

	private void addPinPlayerButton(String playerName, int combatLevel, List<PlayerEquipmentItem> equipment)
	{
		JButton button = panelButton("Compare player");
		button.setToolTipText("Use this player's visible gear as the saved comparison for future player inspections.");
		button.addActionListener(event ->
		{
			if (pinnedInspectHandler != null)
			{
				pinnedInspectHandler.pinPlayer(playerName, combatLevel, equipment);
			}
		});
		addFullWidth(button);
	}

	private void addPinnedNpcComparison(NpcCombatInfo info)
	{
		NpcCombatInfo pinned = pinnedInspects.getNpc();
		if (pinned == null || info == null || Objects.equals(pinned.getDisplayName(), info.getDisplayName()))
		{
			return;
		}

		List<JPanel> rows = new ArrayList<>();
		rows.add(row("Compare", pinned.valueOrDash(pinned.getDisplayName())));
		addDeltaRow(rows, "Combat", info.getCombatLevel(), pinned.getCombatLevel(), false);
		addDeltaRow(rows, "HP", info.getHitpoints(), pinned.getHitpoints(), false);
		addDeltaRow(rows, "Max hit", info.getMaxHit(), pinned.getMaxHit(), false);
		addDeltaRow(rows, "Attack", info.getAttack(), pinned.getAttack(), false);
		addDeltaRow(rows, "Strength", info.getStrength(), pinned.getStrength(), false);
		addDeltaRow(rows, "Defence", info.getDefence(), pinned.getDefence(), false);
		addDeltaRow(rows, "Magic", info.getMagic(), pinned.getMagic(), false);
		addDeltaRow(rows, "Ranged", info.getRanged(), pinned.getRanged(), false);
		addDeltaRow(rows, "Stab def", info.getStabDefence(), pinned.getStabDefence(), false);
		addDeltaRow(rows, "Slash def", info.getSlashDefence(), pinned.getSlashDefence(), false);
		addDeltaRow(rows, "Crush def", info.getCrushDefence(), pinned.getCrushDefence(), false);
		addDeltaRow(rows, "Magic def", info.getMagicDefence(), pinned.getMagicDefence(), false);
		if (rows.size() <= 1)
		{
			return;
		}

		addFullWidth(section("Compared to NPC"));
		addFullWidth(rows(rows.toArray(new JPanel[0])));
	}

	private void addNpcWeaknessSummary(NpcCombatInfo info, EquipmentRecommendation recommendation)
	{
		List<JPanel> rows = new ArrayList<>();
		if (recommendation != null && recommendation.getStyleName() != null)
		{
			rows.add(row("Lowest defence", recommendation.getStyleName()));
			rows.add(row("Based on", recommendation.getDefenceLabel()));
			rows.add(row("Why", weaknessBreakdown(info)));
		}
		if (info.getElementalWeakness() != null)
		{
			rows.add(row("Elemental", info.getElementalWeakness()));
		}
		if (rows.isEmpty())
		{
			return;
		}

		addFullWidth(section("Weakness summary"));
		addFullWidth(rows(rows.toArray(new JPanel[0])));
	}

	private void addSlayerHelper(NpcCombatInfo info)
	{
		if (!hasAny(
			info.getSlayerLevel(),
			info.getSlayerCategory(),
			info.getAssignedBy(),
			info.getTaskOnly(),
			info.getSuperiorVariant()))
		{
			return;
		}

		addFullWidth(section("Slayer"));
		JPanel[] slayerRows = nonEmptyRows(
			"Level", info.getSlayerLevel(),
			"Category", info.getSlayerCategory(),
			"Task-only", info.getTaskOnly(),
			"Superior", info.getSuperiorVariant()
		);
		if (slayerRows.length > 0)
		{
			addFullWidth(rows(slayerRows));
		}

		List<String> assignedBy = splitTags(info.getAssignedBy());
		if (!assignedBy.isEmpty())
		{
			addFullWidth(section("Assigned by"));
			addFullWidth(wikiChips(assignedBy));
		}
	}

	private void addDropSummary(NpcCombatInfo info, Map<String, Integer> dropItemIds)
	{
		List<DropFilterOption> filters = dropFilterOptions(info);
		if (filters.isEmpty())
		{
			return;
		}

		DropFilterOption selected = selectedDropFilter(filters);
		addFullWidth(section("Drop filters"));
		addFullWidth(dropFilterButtons(filters, selected));
		addFullWidth(dropItemsPanel(selected, dropItemIds));
	}

	private DropFilterOption selectedDropFilter(List<DropFilterOption> filters)
	{
		for (DropFilterOption filter : filters)
		{
			if (filter.label.equals(activeDropFilter))
			{
				return filter;
			}
		}

		activeDropFilter = filters.get(0).label;
		return filters.get(0);
	}

	private JPanel dropFilterButtons(List<DropFilterOption> filters, DropFilterOption selected)
	{
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(7, 6, 8, 6));

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.weightx = 1.0;
		constraints.insets = new Insets(2, 2, 2, 2);
		for (int i = 0; i < filters.size(); i++)
		{
			DropFilterOption filter = filters.get(i);
			constraints.gridx = i % 2;
			constraints.gridy = i / 2;
			panel.add(dropFilterButton(filter, selected), constraints);
		}

		int rows = Math.max(1, (int) Math.ceil(filters.size() / 2.0d));
		int height = 16 + rows * 25;
		panel.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, height));
		panel.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, height));
		return panel;
	}

	private JButton dropFilterButton(DropFilterOption filter, DropFilterOption selected)
	{
		boolean active = filter.label.equals(selected.label);
		JButton button = new JButton(filter.label);
		button.setHorizontalAlignment(SwingConstants.CENTER);
		button.setFocusPainted(false);
		button.setBackground(active ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.DARK_GRAY_COLOR);
		button.setForeground(active ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setBorder(new EmptyBorder(4, 6, 4, 6));
		button.setToolTipText(filter.drops);
		button.addActionListener(event ->
		{
			activeDropFilter = filter.label;
			if (lastNpcRenderer != null)
			{
				lastNpcRenderer.run();
			}
		});
		return button;
	}

	private static List<DropFilterOption> dropFilterOptions(NpcCombatInfo info)
	{
		List<DropFilterOption> filters = new ArrayList<>();
		addDropFilter(filters, "Valuable", info.getValuableDrops());
		addDropFilter(filters, "Rare", info.getRareDrops());
		addDropFilter(filters, "Slayer-only", info.getSlayerOnlyDrops());
		addDropFilter(filters, "Clue", info.getClueDrops());
		addDropFilter(filters, "Ironman", info.getIronmanDrops());
		addDropFilter(filters, "Alchable", info.getAlchableDrops());
		addDropFilter(filters, "Upgrade", info.getUpgradeDrops());
		return filters;
	}

	private static void addDropFilter(List<DropFilterOption> filters, String label, String drops)
	{
		if (drops != null && !drops.isEmpty())
		{
			filters.add(new DropFilterOption(label, drops));
		}
	}

	private JPanel dropItemsPanel(DropFilterOption filter, Map<String, Integer> dropItemIds)
	{
		List<String> drops = splitTags(filter.drops);
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(6, 4, 8, 4));

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		for (int i = 0; i < drops.size(); i++)
		{
			constraints.gridy = i;
			panel.add(dropItemRow(drops.get(i), dropItemIds), constraints);
		}

		int height = 14 + Math.max(1, drops.size()) * 36;
		panel.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, height));
		panel.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, height));
		return panel;
	}

	private JPanel dropItemRow(String itemName, Map<String, Integer> dropItemIds)
	{
		JPanel row = new JPanel(new GridBagLayout());
		row.setOpaque(false);
		row.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 32, 36));

		GridBagConstraints iconConstraints = new GridBagConstraints();
		iconConstraints.gridx = 0;
		iconConstraints.insets = new Insets(2, 2, 2, 8);
		iconConstraints.anchor = GridBagConstraints.CENTER;

		JLabel icon = new JLabel("", SwingConstants.CENTER);
		icon.setPreferredSize(new Dimension(32, 32));
		icon.setToolTipText(itemName);
		Integer itemId = dropItemIds == null ? null : dropItemIds.get(normalizeDropName(itemName));
		if (itemId != null && itemId > 0 && itemManager != null)
		{
			itemManager.getImage(itemId).addTo(icon);
		}
		row.add(icon, iconConstraints);

		GridBagConstraints nameConstraints = new GridBagConstraints();
		nameConstraints.gridx = 1;
		nameConstraints.insets = new Insets(2, 0, 2, 2);
		nameConstraints.anchor = GridBagConstraints.WEST;
		nameConstraints.weightx = 1;
		nameConstraints.fill = GridBagConstraints.HORIZONTAL;

		JLabel name = new JLabel("<html><body style='width:142px'>" + escape(valueOrDash(itemName)) + "</body></html>");
		name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setToolTipText(itemName);
		row.add(name, nameConstraints);

		if (itemId != null && itemId > 0)
		{
			JPopupMenu inspectMenu = itemInspectMenu(itemId, itemName);
			row.setComponentPopupMenu(inspectMenu);
			icon.setComponentPopupMenu(inspectMenu);
			name.setComponentPopupMenu(inspectMenu);
		}
		return row;
	}

	private void addRequiredItems(List<NpcItemRequirementStatus> itemRequirementStatuses)
	{
		if (itemRequirementStatuses == null || itemRequirementStatuses.isEmpty())
		{
			return;
		}

		addFullWidth(section("Required items"));
		addFullWidth(requiredItemsPanel(itemRequirementStatuses));
	}

	private JPanel requiredItemsPanel(List<NpcItemRequirementStatus> itemRequirementStatuses)
	{
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(6, 4, 8, 4));

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;

		int rowIndex = 0;
		int preferredHeight = 14;
		for (int groupIndex = 0; groupIndex < itemRequirementStatuses.size(); groupIndex++)
		{
			NpcItemRequirementStatus status = itemRequirementStatuses.get(groupIndex);
			if (groupIndex > 0)
			{
				JPanel andRow = conditionHeader("AND");
				preferredHeight += addRequiredItemPanelRow(panel, andRow, constraints, rowIndex++);
			}

			List<NpcItemRequirementAlternativeStatus> alternatives = status.getAlternatives();
			if (alternatives.size() > 1)
			{
				JPanel header = conditionHeader(status.isMet() ? "Any one of these" : "Need one of these");
				preferredHeight += addRequiredItemPanelRow(panel, header, constraints, rowIndex++);
			}

			for (int alternativeIndex = 0; alternativeIndex < alternatives.size(); alternativeIndex++)
			{
				if (alternativeIndex > 0)
				{
					JPanel orRow = conditionHeader("OR");
					preferredHeight += addRequiredItemPanelRow(panel, orRow, constraints, rowIndex++);
				}

				JPanel itemRow = requiredItemRow(alternatives.get(alternativeIndex));
				preferredHeight += addRequiredItemPanelRow(panel, itemRow, constraints, rowIndex++);
			}
		}

		panel.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, preferredHeight));
		panel.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, preferredHeight));
		return panel;
	}

	private static int addRequiredItemPanelRow(JPanel panel, JPanel row, GridBagConstraints constraints, int rowIndex)
	{
		constraints.gridy = rowIndex;
		panel.add(row, constraints);
		return row.getPreferredSize().height;
	}

	private static JPanel conditionHeader(String text)
	{
		JPanel row = new JPanel(new GridBagLayout());
		row.setOpaque(false);
		row.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 32, 18));

		JLabel label = new JLabel(text, SwingConstants.CENTER);
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setFont(FontManager.getRunescapeSmallFont());
		row.add(label);
		return row;
	}

	private static JPanel requiredItemRow(NpcItemRequirementAlternativeStatus alternative)
	{
		JPanel row = new JPanel(new GridBagLayout());
		row.setOpaque(false);
		row.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 32, 30));

		boolean met = alternative.isMet();
		Color statusColor = met ? new Color(80, 190, 100) : new Color(210, 80, 80);

		GridBagConstraints iconConstraints = new GridBagConstraints();
		iconConstraints.gridx = 0;
		iconConstraints.insets = new Insets(2, 2, 2, 6);
		iconConstraints.anchor = GridBagConstraints.CENTER;

		JLabel icon = new JLabel(loadIcon(met ? STATUS_CHECK_ICON : STATUS_CROSS_ICON), SwingConstants.CENTER);
		icon.setPreferredSize(new Dimension(18, 18));
		row.add(icon, iconConstraints);

		GridBagConstraints itemConstraints = new GridBagConstraints();
		itemConstraints.gridx = 1;
		itemConstraints.insets = new Insets(2, 0, 2, 6);
		itemConstraints.anchor = GridBagConstraints.WEST;
		itemConstraints.weightx = 0.7;
		itemConstraints.fill = GridBagConstraints.HORIZONTAL;

		JLabel item = new JLabel("<html><body style='width:108px'>" + escape(valueOrDash(alternative.getItemName())) + "</body></html>");
		item.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		item.setFont(FontManager.getRunescapeSmallFont());
		row.add(item, itemConstraints);

		GridBagConstraints statusConstraints = new GridBagConstraints();
		statusConstraints.gridx = 2;
		statusConstraints.insets = new Insets(2, 0, 2, 2);
		statusConstraints.anchor = GridBagConstraints.EAST;
		statusConstraints.weightx = 0.3;

		JLabel status = new JLabel("<html><body style='width:66px'>" + escape(alternative.statusText()) + "</body></html>");
		status.setForeground(statusColor);
		status.setFont(FontManager.getRunescapeSmallFont());
		row.add(status, statusConstraints);
		return row;
	}

	private void addKillChecklist(NpcCombatInfo info)
	{
		List<JPanel> rows = new ArrayList<>();
		rows.add(row("HP", info.getHitpoints()));
		rows.add(row("Max hit", info.getMaxHit()));
		rows.add(row("Aggressive", info.getAggressive()));
		rows.add(row("Poison", info.getPoisonous()));
		rows.add(row("Suggested style", CombatStyleRecommendation.forNpc(info) == null ? null : CombatStyleRecommendation.forNpc(info).getDisplayName()));
		addFullWidth(section("Can I kill this?"));
		addFullWidth(rows(rows.toArray(new JPanel[0])));
	}

	private static String sourceLabel(EquipmentRecommendation.RecommendedItem item)
	{
		if (item.isEquipped() && item.isInBank())
		{
			return " (equipped + bank)";
		}

		if (item.isEquipped())
		{
			return " (equipped)";
		}

		if (item.isInBank())
		{
			return " (bank)";
		}

		return "";
	}

	private static String scoreLabel(EquipmentRecommendation.RecommendedItem item)
	{
		return " (score " + DELTA_FORMAT.format(item.getScore()) + ")";
	}

	private static JButton panelButton(String text)
	{
		JButton button = new JButton(text);
		stylePanelButton(button, ColorScheme.BRAND_ORANGE);
		return button;
	}

	private static void stylePanelButton(JButton button, Color foreground)
	{
		button.setFocusPainted(false);
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		button.setForeground(foreground);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setBorder(new EmptyBorder(8, 4, 8, 4));
		button.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, 30));
		button.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, 30));
	}

	private void addComparison(ItemInspectInfo info, ItemInspectInfo equippedInfo)
	{
		if (equippedInfo == null)
		{
			return;
		}

		List<JPanel> comparisonRows = new ArrayList<>();
		comparisonRows.add(row("Equipped", equippedInfo.getDisplayName()));
		addDeltaRow(comparisonRows, "Stab attack", info.getAttackStab(), equippedInfo.getAttackStab(), false);
		addDeltaRow(comparisonRows, "Slash attack", info.getAttackSlash(), equippedInfo.getAttackSlash(), false);
		addDeltaRow(comparisonRows, "Crush attack", info.getAttackCrush(), equippedInfo.getAttackCrush(), false);
		addDeltaRow(comparisonRows, "Magic attack", info.getAttackMagic(), equippedInfo.getAttackMagic(), false);
		addDeltaRow(comparisonRows, "Ranged attack", info.getAttackRanged(), equippedInfo.getAttackRanged(), false);
		addDeltaRow(comparisonRows, "Stab defence", info.getDefenceStab(), equippedInfo.getDefenceStab(), false);
		addDeltaRow(comparisonRows, "Slash defence", info.getDefenceSlash(), equippedInfo.getDefenceSlash(), false);
		addDeltaRow(comparisonRows, "Crush defence", info.getDefenceCrush(), equippedInfo.getDefenceCrush(), false);
		addDeltaRow(comparisonRows, "Magic defence", info.getDefenceMagic(), equippedInfo.getDefenceMagic(), false);
		addDeltaRow(comparisonRows, "Ranged defence", info.getDefenceRanged(), equippedInfo.getDefenceRanged(), false);
		addDeltaRow(comparisonRows, "Strength", info.getStrength(), equippedInfo.getStrength(), false);
		addDeltaRow(comparisonRows, "Ranged strength", info.getRangedStrength(), equippedInfo.getRangedStrength(), false);
		addDeltaRow(comparisonRows, "Magic damage", info.getMagicDamage(), equippedInfo.getMagicDamage(), false);
		addDeltaRow(comparisonRows, "Prayer", info.getPrayer(), equippedInfo.getPrayer(), false);
		addDeltaRow(comparisonRows, "Weight", info.getWeight(), equippedInfo.getWeight(), true);
		addDeltaRow(comparisonRows, "Attack speed", info.getAttackSpeed(), equippedInfo.getAttackSpeed(), true);
		addDeltaRow(comparisonRows, "Attack range", info.getAttackRange(), equippedInfo.getAttackRange(), false);

		if (comparisonRows.size() <= 1)
		{
			return;
		}

		addFullWidth(section("Compared to equipped"));
		addFullWidth(rows(comparisonRows.toArray(new JPanel[0])));
	}

	private void addPinnedItemComparison(ItemInspectInfo info)
	{
		ItemInspectInfo pinned = pinnedInspects.getItem();
		if (pinned == null || pinned.getItemId() == info.getItemId())
		{
			return;
		}

		List<JPanel> comparisonRows = new ArrayList<>();
		comparisonRows.add(row("Compare", pinned.getDisplayName()));
		addDeltaRow(comparisonRows, "Stab attack", info.getAttackStab(), pinned.getAttackStab(), false);
		addDeltaRow(comparisonRows, "Slash attack", info.getAttackSlash(), pinned.getAttackSlash(), false);
		addDeltaRow(comparisonRows, "Crush attack", info.getAttackCrush(), pinned.getAttackCrush(), false);
		addDeltaRow(comparisonRows, "Magic attack", info.getAttackMagic(), pinned.getAttackMagic(), false);
		addDeltaRow(comparisonRows, "Ranged attack", info.getAttackRanged(), pinned.getAttackRanged(), false);
		addDeltaRow(comparisonRows, "Stab defence", info.getDefenceStab(), pinned.getDefenceStab(), false);
		addDeltaRow(comparisonRows, "Slash defence", info.getDefenceSlash(), pinned.getDefenceSlash(), false);
		addDeltaRow(comparisonRows, "Crush defence", info.getDefenceCrush(), pinned.getDefenceCrush(), false);
		addDeltaRow(comparisonRows, "Magic defence", info.getDefenceMagic(), pinned.getDefenceMagic(), false);
		addDeltaRow(comparisonRows, "Ranged defence", info.getDefenceRanged(), pinned.getDefenceRanged(), false);
		addDeltaRow(comparisonRows, "Strength", info.getStrength(), pinned.getStrength(), false);
		addDeltaRow(comparisonRows, "Ranged strength", info.getRangedStrength(), pinned.getRangedStrength(), false);
		addDeltaRow(comparisonRows, "Magic damage", info.getMagicDamage(), pinned.getMagicDamage(), false);
		addDeltaRow(comparisonRows, "Prayer", info.getPrayer(), pinned.getPrayer(), false);
		addDeltaRow(comparisonRows, "Weight", info.getWeight(), pinned.getWeight(), true);
		if (comparisonRows.size() <= 1)
		{
			return;
		}

		addFullWidth(section("Compared to item"));
		addFullWidth(rows(comparisonRows.toArray(new JPanel[0])));
	}

	private void addPriceSummary(ItemPriceSummary priceSummary)
	{
		if (priceSummary == null)
		{
			return;
		}

		addFullWidth(section("Prices"));
		List<JPanel> priceRows = new ArrayList<>();
		addPriceRow(priceRows, "GE", priceSummary.getGePrice(), ColorScheme.LIGHT_GRAY_COLOR);
		addPriceRow(priceRows, "High alch", priceSummary.getHighAlch(), ColorScheme.LIGHT_GRAY_COLOR);
		addPriceRow(priceRows, "Low alch", priceSummary.getLowAlch(), ColorScheme.LIGHT_GRAY_COLOR);
		addPriceRow(priceRows, highAlchProfitLabel(priceSummary), priceSummary.getHighAlchProfit(), highAlchProfitColor(priceSummary));
		addFullWidth(rows(priceRows.toArray(new JPanel[0])));
	}

	private static void addPriceRow(List<JPanel> rows, String label, String value, Color valueColor)
	{
		if (value == null || value.isEmpty())
		{
			return;
		}

		rows.add(row(label, value, valueColor));
	}

	private static String highAlchProfitLabel(ItemPriceSummary priceSummary)
	{
		Integer value = priceSummary.getHighAlchProfitValue();
		if (value == null)
		{
			return "HA profit";
		}
		if (value > 0)
		{
			return "HA profit";
		}
		if (value < 0)
		{
			return "HA loss";
		}
		return "HA even";
	}

	private static Color highAlchProfitColor(ItemPriceSummary priceSummary)
	{
		Integer value = priceSummary.getHighAlchProfitValue();
		if (value == null || value == 0)
		{
			return ColorScheme.LIGHT_GRAY_COLOR;
		}
		return value > 0 ? new Color(80, 190, 100) : new Color(210, 80, 80);
	}

	private void addItemTags(ItemInspectInfo info)
	{
		List<String> tags = new ArrayList<>();
		if (numericOrZero(info.getAttackStab()) > 0 || numericOrZero(info.getAttackSlash()) > 0 || numericOrZero(info.getAttackCrush()) > 0)
		{
			tags.add("Melee accuracy");
		}
		if (numericOrZero(info.getStrength()) > 0)
		{
			tags.add("Melee strength");
		}
		if (numericOrZero(info.getAttackRanged()) > 0 || numericOrZero(info.getRangedStrength()) > 0)
		{
			tags.add("Ranged");
		}
		if (numericOrZero(info.getAttackMagic()) > 0 || numericOrZero(info.getMagicDamage()) > 0)
		{
			tags.add("Magic");
		}
		if (numericOrZero(info.getPrayer()) > 0)
		{
			tags.add("Prayer");
		}
		if (defenceTotal(info) > 0)
		{
			tags.add("Tank");
		}
		if (tags.isEmpty() && "Yes".equalsIgnoreCase(info.getEquipable()))
		{
			tags.add("Cosmetic or utility");
		}
		if (tags.isEmpty())
		{
			return;
		}

		addFullWidth(section("Gear role"));
		addFullWidth(chips(tags));
	}

	private void addItemSource(ItemInspectInfo info)
	{
		if (info.getSourceSummary() == null)
		{
			return;
		}

		addFullWidth(section("Sources"));
		addFullWidth(chips(splitTags(info.getSourceSummary())));
	}

	private static List<String> splitTags(String value)
	{
		List<String> tags = new ArrayList<>();
		if (value == null)
		{
			return tags;
		}

		for (String tag : value.split(","))
		{
			String trimmed = tag.trim();
			if (!trimmed.isEmpty())
			{
				tags.add(trimmed);
			}
		}
		return tags;
	}

	private static String normalizeDropName(String itemName)
	{
		return itemName == null
			? ""
			: itemName.replace('\u00A0', ' ').trim().replaceAll("\\s+", " ").toLowerCase(Locale.ENGLISH);
	}

	private static List<String> splitNameTags(String value)
	{
		List<String> names = new ArrayList<>();
		for (String tag : splitTags(value))
		{
			names.add(formatNameTag(tag));
		}
		return names;
	}

	private static String formatNameTag(String value)
	{
		String normalized = value.trim().replace('_', ' ');
		if (normalized.isEmpty())
		{
			return normalized;
		}

		String[] words = normalized.split("\\s+");
		StringBuilder builder = new StringBuilder();
		for (String word : words)
		{
			if (builder.length() > 0)
			{
				builder.append(' ');
			}
			if (word.length() == 1)
			{
				builder.append(Character.toUpperCase(word.charAt(0)));
			}
			else
			{
				builder.append(Character.toUpperCase(word.charAt(0)));
				builder.append(word.substring(1).toLowerCase());
			}
		}
		return builder.toString();
	}

	private static JPanel chips(List<String> tags)
	{
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(7, 6, 8, 6));

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.weightx = 1.0;
		constraints.insets = new Insets(2, 2, 2, 2);
		for (int i = 0; i < tags.size(); i++)
		{
			constraints.gridx = i % 2;
			constraints.gridy = i / 2;
			panel.add(chip(tags.get(i)), constraints);
		}

		int rows = Math.max(1, (int) Math.ceil(tags.size() / 2.0d));
		int height = 16 + rows * 25;
		panel.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, height));
		panel.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, height));
		return panel;
	}

	private static JPanel wikiChips(List<String> tags)
	{
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(7, 6, 8, 6));

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.weightx = 1.0;
		constraints.insets = new Insets(2, 2, 2, 2);
		for (int i = 0; i < tags.size(); i++)
		{
			constraints.gridx = i % 2;
			constraints.gridy = i / 2;
			panel.add(wikiChip(tags.get(i)), constraints);
		}

		int rows = Math.max(1, (int) Math.ceil(tags.size() / 2.0d));
		int height = 16 + rows * 25;
		panel.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, height));
		panel.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, height));
		return panel;
	}

	private static JLabel chip(String text)
	{
		JLabel label = new JLabel(text, SwingConstants.CENTER);
		label.setOpaque(true);
		label.setBackground(ColorScheme.DARK_GRAY_COLOR);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setBorder(new EmptyBorder(4, 6, 4, 6));
		label.setToolTipText(text);
		return label;
	}

	private static JButton wikiChip(String text)
	{
		String url = wikiUrl(text);
		JButton button = new JButton(formatNameTag(text));
		button.setHorizontalAlignment(SwingConstants.CENTER);
		button.setOpaque(true);
		button.setBackground(ColorScheme.DARK_GRAY_COLOR);
		button.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setBorder(new EmptyBorder(4, 6, 4, 6));
		button.setToolTipText(url);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setFocusPainted(false);
		button.addActionListener(event -> LinkBrowser.browse(url));
		return button;
	}

	private static String wikiUrl(String pageName)
	{
		String page = pageName == null ? "" : pageName.trim().replace(' ', '_');
		return "https://oldschool.runescape.wiki/w/" + URLEncoder.encode(page, StandardCharsets.UTF_8).replace("+", "_");
	}

	private void addCacheManagement()
	{
		addFullWidth(section("Cache"));
		JButton itemButton = panelButton("Clear item cache");
		itemButton.addActionListener(event ->
		{
			if (cacheManagementHandler != null)
			{
				cacheManagementHandler.clearItemCache();
			}
		});
		addFullWidth(itemButton);

		JButton npcButton = panelButton("Clear NPC cache");
		npcButton.addActionListener(event ->
		{
			if (cacheManagementHandler != null)
			{
				cacheManagementHandler.clearNpcCache();
			}
		});
		addFullWidth(npcButton);

		JButton allButton = panelButton("Clear all Inspect cache");
		allButton.addActionListener(event ->
		{
			if (cacheManagementHandler != null)
			{
				cacheManagementHandler.clearAllCache();
			}
		});
		addFullWidth(allButton);
	}

	private void addPlayerGearTags(List<PlayerEquipmentItem> equipment)
	{
		if (equipment == null || equipment.isEmpty())
		{
			return;
		}

		List<String> tags = new ArrayList<>();
		for (PlayerEquipmentItem item : equipment)
		{
			String name = item.getItemName().toLowerCase();
			addTag(tags, name, "whip", "Melee");
			addTag(tags, name, "scimitar", "Melee");
			addTag(tags, name, "sword", "Melee");
			addTag(tags, name, "bow", "Ranged");
			addTag(tags, name, "crossbow", "Ranged");
			addTag(tags, name, "staff", "Magic");
			addTag(tags, name, "wand", "Magic");
			addTag(tags, name, "graceful", "Skilling");
			addTag(tags, name, "robe", "Magic");
		}
		if (tags.size() > 1 && !tags.contains("Mixed"))
		{
			tags.add("Mixed");
		}
		if (tags.isEmpty())
		{
			tags.add("Cosmetic");
		}

		addFullWidth(rows(row("Visible tags", String.join(", ", tags))));
	}

	private static void addTag(List<String> tags, String itemName, String needle, String tag)
	{
		if (itemName.contains(needle) && !tags.contains(tag))
		{
			tags.add(tag);
		}
	}

	private static String weaknessBreakdown(NpcCombatInfo info)
	{
		List<String> parts = new ArrayList<>();
		addWeaknessPart(parts, "Stab", info.getStabDefence());
		addWeaknessPart(parts, "Slash", info.getSlashDefence());
		addWeaknessPart(parts, "Crush", info.getCrushDefence());
		addWeaknessPart(parts, "Magic", info.getMagicDefence());
		addWeaknessPart(parts, "Ranged", lowestValue(info.getLightRangedDefence(), info.getStandardRangedDefence(), info.getHeavyRangedDefence()));
		return parts.isEmpty() ? null : String.join(", ", parts);
	}

	private static void addWeaknessPart(List<String> parts, String label, String value)
	{
		if (value != null && !value.isEmpty())
		{
			parts.add(label + " " + value);
		}
	}

	private static String lowestValue(String... values)
	{
		Double lowest = null;
		String lowestValue = null;
		for (String value : values)
		{
			Double numeric = numericValue(value);
			if (numeric != null && (lowest == null || numeric < lowest))
			{
				lowest = numeric;
				lowestValue = value;
			}
		}
		return lowestValue;
	}

	private static double defenceTotal(ItemInspectInfo info)
	{
		return numericOrZero(info.getDefenceStab())
			+ numericOrZero(info.getDefenceSlash())
			+ numericOrZero(info.getDefenceCrush())
			+ numericOrZero(info.getDefenceMagic())
			+ numericOrZero(info.getDefenceRanged());
	}

	private static double numericOrZero(String value)
	{
		Double numeric = numericValue(value);
		return numeric == null ? 0 : numeric;
	}

	private void addRequirementSummary(ItemRequirementSummary summary)
	{
		if (summary == null || (summary.getMetRequirements().isEmpty() && summary.getMissingRequirements().isEmpty()))
		{
			return;
		}

		addFullWidth(section("Requirement check"));
		List<JPanel> rows = new ArrayList<>();
		if (!summary.getMetRequirements().isEmpty())
		{
			rows.add(row("Met", String.join(", ", summary.getMetRequirements())));
		}
		if (!summary.getMissingRequirements().isEmpty())
		{
			rows.add(row("Missing", String.join(", ", summary.getMissingRequirements())));
		}
		addFullWidth(rows(rows.toArray(new JPanel[0])));
	}

	private static JPanel playerSummary(int combatLevel, PlayerInspectAnalysis analysis, boolean pvpBlocked)
	{
		List<JPanel> rows = new ArrayList<>();
		rows.add(row("Combat level", combatLevel > 0 ? Integer.toString(combatLevel) : null));
		rows.add(row("Gear value", pvpBlocked ? "Disabled in PvP" : analysis == null ? null : analysis.getVisibleValue()));
		return rows(rows.toArray(new JPanel[0]));
	}

	private void addPlayerComparison(PlayerInspectAnalysis analysis)
	{
		if (analysis == null)
		{
			return;
		}

		if (analysis.getComparisons() == null || analysis.getComparisons().isEmpty())
		{
			if (analysis.getComparisonMessage() != null && !analysis.getComparisonMessage().isEmpty())
			{
				addFullWidth(section("Compared to you"));
				addFullWidth(message(analysis.getComparisonMessage()));
			}
			return;
		}

		addFullWidth(section("Compared to you"));
		addFullWidth(playerComparisonTotal(analysis.getComparisons()));
	}

	private void addPinnedPlayerComparison(String playerName, int combatLevel, List<PlayerEquipmentItem> equipment)
	{
		if (pinnedInspects.getPlayerName() == null || equipment == null)
		{
			return;
		}

		List<JPanel> rows = new ArrayList<>();
		rows.add(row("Compare", pinnedInspects.getPlayerName()));
		if (combatLevel > 0 && pinnedInspects.getPlayerCombatLevel() > 0)
		{
			rows.add(deltaRow("Combat", combatLevel - pinnedInspects.getPlayerCombatLevel(), false));
		}
		rows.add(deltaRow("Gear value", totalVisibleValue(equipment) - totalVisibleValue(pinnedInspects.getPlayerEquipment()), false));
		rows.add(row("Same slots", Integer.toString(matchingEquipmentSlots(equipment, pinnedInspects.getPlayerEquipment()))));
		rows.add(row("Different", differentEquipmentSlots(equipment, pinnedInspects.getPlayerEquipment())));
		addFullWidth(section("Compared to player"));
		addFullWidth(rows(rows.toArray(new JPanel[0])));
	}

	private static Map<String, PlayerEquipmentComparison> comparisonsBySlot(PlayerInspectAnalysis analysis)
	{
		Map<String, PlayerEquipmentComparison> bySlot = new HashMap<>();
		if (analysis == null || analysis.getComparisons() == null)
		{
			return bySlot;
		}

		for (PlayerEquipmentComparison comparison : analysis.getComparisons())
		{
			bySlot.put(comparison.getSlot(), comparison);
		}
		return bySlot;
	}

	private static int totalVisibleValue(List<PlayerEquipmentItem> equipment)
	{
		int total = 0;
		if (equipment == null)
		{
			return total;
		}

		for (PlayerEquipmentItem item : equipment)
		{
			if (item != null)
			{
				total += Math.max(0, item.getPrice());
			}
		}
		return total;
	}

	private static int matchingEquipmentSlots(List<PlayerEquipmentItem> current, List<PlayerEquipmentItem> pinned)
	{
		Map<String, PlayerEquipmentItem> currentBySlot = equipmentBySlot(current);
		Map<String, PlayerEquipmentItem> pinnedBySlot = equipmentBySlot(pinned);
		int matching = 0;
		for (Map.Entry<String, PlayerEquipmentItem> entry : currentBySlot.entrySet())
		{
			PlayerEquipmentItem pinnedItem = pinnedBySlot.get(entry.getKey());
			if (pinnedItem != null && Objects.equals(entry.getValue().getItemName(), pinnedItem.getItemName()))
			{
				matching++;
			}
		}
		return matching;
	}

	private static String differentEquipmentSlots(List<PlayerEquipmentItem> current, List<PlayerEquipmentItem> pinned)
	{
		Map<String, PlayerEquipmentItem> currentBySlot = equipmentBySlot(current);
		Map<String, PlayerEquipmentItem> pinnedBySlot = equipmentBySlot(pinned);
		Set<String> slots = new TreeSet<>();
		slots.addAll(currentBySlot.keySet());
		slots.addAll(pinnedBySlot.keySet());

		List<String> different = new ArrayList<>();
		for (String slot : slots)
		{
			PlayerEquipmentItem currentItem = currentBySlot.get(slot);
			PlayerEquipmentItem pinnedItem = pinnedBySlot.get(slot);
			String currentName = currentItem == null ? null : currentItem.getItemName();
			String pinnedName = pinnedItem == null ? null : pinnedItem.getItemName();
			if (!Objects.equals(currentName, pinnedName))
			{
				different.add(slot);
			}
		}
		return different.isEmpty() ? "None" : String.join(", ", different);
	}

	private static Map<String, PlayerEquipmentItem> equipmentBySlot(List<PlayerEquipmentItem> equipment)
	{
		Map<String, PlayerEquipmentItem> bySlot = new HashMap<>();
		if (equipment == null)
		{
			return bySlot;
		}

		for (PlayerEquipmentItem item : equipment)
		{
			if (item != null && item.getSlot() != null)
			{
				bySlot.put(item.getSlot(), item);
			}
		}
		return bySlot;
	}

	private void addRecentNpcInspects()
	{
		if (lastRecentNpcs.isEmpty())
		{
			return;
		}

		addFullWidth(section("Recent NPCs"));
		addFullWidth(recentList(lastRecentNpcs, null, null));
	}

	private void addRecentItemInspects()
	{
		if (lastRecentItems.isEmpty())
		{
			return;
		}

		addFullWidth(section("Recent items"));
		addFullWidth(recentList(null, null, lastRecentItems));
	}

	private void addRecentPlayerInspects()
	{
		if (lastRecentPlayers.isEmpty())
		{
			return;
		}

		addFullWidth(section("Recent players"));
		addFullWidth(recentList(null, lastRecentPlayers, null));
	}

	private void addRecentInspects()
	{
		boolean hasNpcs = !lastRecentNpcs.isEmpty();
		boolean hasPlayers = !lastRecentPlayers.isEmpty();
		boolean hasItems = !lastRecentItems.isEmpty();
		if (!hasNpcs && !hasPlayers && !hasItems)
		{
			return;
		}

		addFullWidth(section("Recent"));
		addFullWidth(recentList(lastRecentNpcs, lastRecentPlayers, lastRecentItems));
	}

	private JPanel recentList(List<String> recentNpcs, List<String> recentPlayers, List<RecentInspectEntry> recentItems)
	{
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(6, 4, 8, 4));

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.weightx = 1.0;
		constraints.insets = new Insets(0, 0, 3, 0);
		int row = 0;

		if (recentNpcs != null && !recentNpcs.isEmpty())
		{
			constraints.gridy = row++;
			panel.add(recentLabel("NPCs"), constraints);
			for (String npcName : recentNpcs)
			{
				constraints.gridy = row++;
				panel.add(recentButton(npcName, () ->
				{
					if (recentInspectHandler != null)
					{
						recentInspectHandler.inspectNpc(npcName);
					}
				}), constraints);
			}
		}

		boolean hasPlayers = recentPlayers != null && !recentPlayers.isEmpty();
		boolean hasItems = recentItems != null && !recentItems.isEmpty();
		if (hasPlayers)
		{
			constraints.gridy = row++;
			panel.add(recentLabel("Players"), constraints);
			for (String playerName : recentPlayers)
			{
				constraints.gridy = row++;
				panel.add(recentButton(playerName, () ->
				{
					if (recentInspectHandler != null)
					{
						recentInspectHandler.inspectPlayer(playerName);
					}
				}), constraints);
			}
		}

		if (hasItems)
		{
			constraints.gridy = row++;
			panel.add(recentLabel("Items"), constraints);
			for (RecentInspectEntry item : recentItems)
			{
				constraints.gridy = row++;
				panel.add(recentButton(item.getLabel(), () ->
				{
					if (recentInspectHandler != null)
					{
						recentInspectHandler.inspectItem(item.getItemId(), item.getLabel());
					}
				}), constraints);
			}
		}

		int height = 12 + row * 25;
		panel.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, height));
		panel.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, height));
		return panel;
	}

	private static JLabel recentLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 32, 20));
		return label;
	}

	private static JButton recentButton(String text, Runnable action)
	{
		JButton button = new JButton(text);
		button.setHorizontalAlignment(SwingConstants.LEFT);
		button.setFocusPainted(false);
		button.setBackground(ColorScheme.DARK_GRAY_COLOR);
		button.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setBorder(new EmptyBorder(4, 6, 4, 6));
		button.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 32, 22));
		button.setToolTipText(text);
		button.addActionListener(event -> action.run());
		return button;
	}

	private void addPinnedTray()
	{
		if (pinnedInspects == null || !pinnedInspects.hasAny())
		{
			return;
		}

		addFullWidth(section("Compare"));
		addFullWidth(pinnedTray());
	}

	private JPanel pinnedTray()
	{
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(6, 4, 8, 4));

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.weightx = 1.0;
		constraints.insets = new Insets(0, 0, 3, 0);

		int row = 0;
		if (pinnedInspects.getNpc() != null)
		{
			constraints.gridy = row++;
			panel.add(pinnedRow("NPC", pinnedInspects.getNpc().getDisplayName(), () ->
			{
				if (pinnedInspectHandler != null)
				{
					pinnedInspectHandler.openNpc(pinnedInspects.getNpc());
				}
			}, () ->
			{
				if (pinnedInspectHandler != null)
				{
					pinnedInspectHandler.clearNpc();
				}
			}), constraints);
		}
		if (pinnedInspects.getItem() != null)
		{
			constraints.gridy = row++;
			panel.add(pinnedRow("Item", pinnedInspects.getItem().getDisplayName(), () ->
			{
				if (pinnedInspectHandler != null)
				{
					pinnedInspectHandler.openItem(pinnedInspects.getItem());
				}
			}, () ->
			{
				if (pinnedInspectHandler != null)
				{
					pinnedInspectHandler.clearItem();
				}
			}), constraints);
		}
		if (pinnedInspects.getPlayerName() != null)
		{
			constraints.gridy = row++;
			panel.add(pinnedRow("Player", pinnedInspects.getPlayerName(), () ->
			{
				if (pinnedInspectHandler != null)
				{
					pinnedInspectHandler.openPlayer(
						pinnedInspects.getPlayerName(),
						pinnedInspects.getPlayerCombatLevel(),
						pinnedInspects.getPlayerEquipment());
				}
			}, () ->
			{
				if (pinnedInspectHandler != null)
				{
					pinnedInspectHandler.clearPlayer();
				}
			}), constraints);
		}

		int height = 12 + row * 25;
		panel.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, height));
		panel.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, height));
		return panel;
	}

	private static JPanel pinnedRow(String type, String label, Runnable open, Runnable clear)
	{
		JPanel row = new JPanel(new GridBagLayout());
		row.setOpaque(false);
		row.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 32, 22));

		JButton text = comparisonButton(type + ": " + valueOrDash(label), open);
		text.setToolTipText("Open " + type.toLowerCase(Locale.ENGLISH) + " comparison");

		GridBagConstraints textConstraints = new GridBagConstraints();
		textConstraints.gridx = 0;
		textConstraints.fill = GridBagConstraints.HORIZONTAL;
		textConstraints.weightx = 1.0;
		textConstraints.insets = new Insets(0, 2, 0, 4);
		row.add(text, textConstraints);

		JButton clearButton = new JButton("X");
		clearButton.setFocusPainted(false);
		clearButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
		clearButton.setForeground(new Color(210, 80, 80));
		clearButton.setFont(FontManager.getRunescapeSmallFont());
		clearButton.setBorder(new EmptyBorder(2, 6, 2, 6));
		clearButton.setToolTipText("Clear " + type.toLowerCase(Locale.ENGLISH) + " comparison");
		clearButton.addActionListener(event -> clear.run());

		GridBagConstraints clearConstraints = new GridBagConstraints();
		clearConstraints.gridx = 1;
		clearConstraints.insets = new Insets(0, 0, 0, 2);
		row.add(clearButton, clearConstraints);
		return row;
	}

	private static JButton comparisonButton(String text, Runnable action)
	{
		JButton button = new JButton(text);
		button.setHorizontalAlignment(SwingConstants.LEFT);
		button.setFocusPainted(false);
		button.setBackground(ColorScheme.DARK_GRAY_COLOR);
		button.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setBorder(new EmptyBorder(3, 6, 3, 6));
		button.addActionListener(event -> action.run());
		return button;
	}

	private static JPanel playerComparisonTotal(List<PlayerEquipmentComparison> comparisons)
	{
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(6, 4, 8, 4));
		panel.setOpaque(false);
		panel.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, 38));
		panel.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, 38));

		addComparisonDelta(panel, "Atk", totalDelta(comparisons, "attack"), 0);
		addComparisonDelta(panel, "Def", totalDelta(comparisons, "defence"), 1);
		addComparisonDelta(panel, "Dmg", totalDelta(comparisons, "strength"), 2);
		addComparisonDelta(panel, "Pray", totalDelta(comparisons, "prayer"), 3);
		return panel;
	}

	private static void addComparisonDelta(JPanel panel, String label, String delta, int gridx)
	{
		JLabel component = new JLabel(label + " " + delta, SwingConstants.CENTER);
		component.setForeground(deltaColor(numericValue(delta), false));
		component.setFont(FontManager.getRunescapeSmallFont());

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = gridx;
		constraints.gridy = 0;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.weightx = 1.0;
		constraints.insets = new Insets(2, 1, 0, 1);
		panel.add(component, constraints);
	}

	private static String totalDelta(List<PlayerEquipmentComparison> comparisons, String type)
	{
		double total = 0;
		for (PlayerEquipmentComparison comparison : comparisons)
		{
			switch (type)
			{
				case "attack":
					total += numericValue(comparison.getAttackDelta());
					break;
				case "defence":
					total += numericValue(comparison.getDefenceDelta());
					break;
				case "strength":
					total += numericValue(comparison.getStrengthDelta());
					break;
				case "prayer":
					total += numericValue(comparison.getPrayerDelta());
					break;
				default:
					break;
			}
		}
		return formatDelta(total);
	}

	private static void addDeltaRow(List<JPanel> rows, String label, String inspectedValue, String equippedValue, boolean lowerIsBetter)
	{
		Double inspected = numericValue(inspectedValue);
		Double equipped = numericValue(equippedValue);
		if (inspected == null || equipped == null)
		{
			return;
		}

		double delta = inspected - equipped;
		rows.add(deltaRow(label, delta, lowerIsBetter));
	}

	private void reset()
	{
		removeAll();
		addSearchControls();
		addTabControls();
		addPinnedTray();
	}

	private void addFullWidth(Component component)
	{
		add(component);
	}

	private void refresh()
	{
		revalidate();
		repaint();
	}

	private void addSearchControls()
	{
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(6, 4, 6, 4));
		panel.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, 66));
		panel.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, 66));

		JComboBox<String> type = new JComboBox<>(new String[]{"Item", "NPC"});
		type.setSelectedItem(lastSearchType);
		type.setFont(FontManager.getRunescapeSmallFont());
		type.setFocusable(false);

		IconTextField query = new IconTextField();
		query.setIcon(IconTextField.Icon.SEARCH);
		if (!lastSearchText.isEmpty())
		{
			query.setText(lastSearchText);
		}
		query.setBackground(ColorScheme.DARK_GRAY_COLOR);
		query.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		query.addClearListener(() ->
		{
			lastSearchText = "";
			showEmpty();
		});

		JButton search = new JButton("Search");
		search.setFont(FontManager.getRunescapeSmallFont());
		search.setFocusPainted(false);
		search.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
		search.setForeground(ColorScheme.BRAND_ORANGE);

		Runnable submit = () ->
		{
			lastSearchType = String.valueOf(type.getSelectedItem());
			lastSearchText = query.getText().trim();
			if (!lastSearchText.isEmpty() && searchHandler != null)
			{
				searchHandler.search(lastSearchType, lastSearchText);
			}
		};
		query.addActionListener(event -> submit.run());
		search.addActionListener(event -> submit.run());

		GridBagConstraints typeConstraints = new GridBagConstraints();
		typeConstraints.gridx = 0;
		typeConstraints.gridy = 0;
		typeConstraints.insets = new Insets(0, 0, 4, 4);
		typeConstraints.fill = GridBagConstraints.HORIZONTAL;
		typeConstraints.weightx = 0.28;
		panel.add(type, typeConstraints);

		GridBagConstraints queryConstraints = new GridBagConstraints();
		queryConstraints.gridx = 1;
		queryConstraints.gridy = 0;
		queryConstraints.insets = new Insets(0, 0, 4, 0);
		queryConstraints.fill = GridBagConstraints.HORIZONTAL;
		queryConstraints.weightx = 0.72;
		panel.add(query, queryConstraints);

		GridBagConstraints searchConstraints = new GridBagConstraints();
		searchConstraints.gridx = 0;
		searchConstraints.gridy = 1;
		searchConstraints.gridwidth = 2;
		searchConstraints.fill = GridBagConstraints.HORIZONTAL;
		searchConstraints.weightx = 1.0;
		panel.add(search, searchConstraints);

		addFullWidth(panel);
	}

	private void addTabControls()
	{
		JPanel panel = new JPanel(new GridLayout(1, 4, 2, 0));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, 26));
		panel.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, 26));
		panel.add(tabButton("Item"));
		panel.add(tabButton("NPC"));
		panel.add(tabButton("Player"));
		panel.add(tabButton("Recent"));
		addFullWidth(panel);
	}

	private JButton tabButton(String tab)
	{
		JButton button = new JButton(tab);
		button.setFocusPainted(false);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setForeground(activeTab.equals(tab) ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
		button.setBackground(activeTab.equals(tab) ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR);
		button.setBorder(new EmptyBorder(4, 2, 4, 2));
		button.addActionListener(event -> showTab(tab));
		return button;
	}

	private void showTab(String tab)
	{
		switch (tab)
		{
			case "Item":
				showStoredTab(tab, lastItemRenderer);
				return;
			case "NPC":
				showStoredTab(tab, lastNpcRenderer);
				return;
			case "Player":
				showStoredTab(tab, lastPlayerRenderer);
				return;
			case "Recent":
				showRecentOnly();
				return;
			default:
				return;
		}
	}

	private void showStoredTab(String tab, Runnable renderer)
	{
		if (renderer != null)
		{
			renderer.run();
			return;
		}

		activeTab = tab;
		reset();
		addFullWidth(message("No " + tab.toLowerCase() + " inspection yet."));
		refresh();
	}

	private void showRecentOnly()
	{
		activeTab = "Recent";
		reset();
		addRecentInspects();
		if (lastRecentNpcs.isEmpty() && lastRecentPlayers.isEmpty() && lastRecentItems.isEmpty())
		{
			addFullWidth(message("No recent inspections yet."));
		}
		refresh();
	}

	private void storeRecentPlayersAndItems(List<String> recentPlayers, List<RecentInspectEntry> recentItems)
	{
		lastRecentPlayers = recentPlayers == null ? new ArrayList<>() : new ArrayList<>(recentPlayers);
		lastRecentItems = recentItems == null ? new ArrayList<>() : new ArrayList<>(recentItems);
	}

	private static JLabel title(String text)
	{
		JLabel label = new JLabel(text, SwingConstants.CENTER);
		label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setBorder(new EmptyBorder(0, 0, 8, 0));
		label.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, 30));
		label.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, 30));
		return label;
	}

	private static JLabel searchTitle(String text)
	{
		JLabel label = new JLabel(text, SwingConstants.CENTER);
		label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setBorder(new EmptyBorder(3, 0, 8, 0));
		label.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, 30));
		label.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, 30));
		return label;
	}

	private static JLabel message(String text)
	{
		JLabel label = new JLabel("<html><body style='width:" + MESSAGE_HTML_WIDTH + "px'>" + escape(text) + "</body></html>");
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setBorder(new EmptyBorder(8, 4, 8, 4));
		label.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, label.getPreferredSize().height));
		label.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, label.getPreferredSize().height));
		return label;
	}

	private void addErrorMessage(String userMessage, String detail)
	{
		addFullWidth(message(userMessage));
		JButton details = panelButton("Show error details");
		details.addActionListener(event ->
		{
			addFullWidth(message(detail == null ? userMessage : detail));
			refresh();
			details.setEnabled(false);
		});
		addFullWidth(details);
	}

	private static JButton sourceButton(String sourceUrl)
	{
		JButton button = new JButton("Open OSRS Wiki page");
		button.setToolTipText(sourceUrl);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setFocusPainted(false);
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		button.setForeground(ColorScheme.BRAND_ORANGE);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setBorder(new EmptyBorder(8, 4, 8, 4));
		button.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, 30));
		button.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, 30));
		button.addActionListener(event -> LinkBrowser.browse(sourceUrl));
		return button;
	}

	private static JLabel section(String text)
	{
		JLabel label = new JLabel(text, SwingConstants.CENTER);
		label.setOpaque(true);
		label.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, 24));
		label.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, 24));
		label.setBorder(new EmptyBorder(4, 4, 4, 4));
		return label;
	}

	private static JPanel rows(JPanel... rows)
	{
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(6, 4, 8, 4));
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		for (int i = 0; i < rows.length; i++)
		{
			constraints.gridy = i;
			panel.add(rows[i], constraints);
		}
		int rowHeight = Arrays.stream(rows).mapToInt(row -> row.getPreferredSize().height).sum();
		panel.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, rowHeight + 14));
		panel.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, rowHeight + 14));
		return panel;
	}

	private static JPanel row(String label, String value)
	{
		return row(label, value, ColorScheme.LIGHT_GRAY_COLOR);
	}

	private static JPanel row(String label, String value, Color valueColor)
	{
		JPanel row = new JPanel(new GridBagLayout());
		row.setOpaque(false);
		String safeValue = valueOrDash(value);
		int valueHeight = safeValue.length() > 28 ? 36 : 22;
		row.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 32, valueHeight));

		GridBagConstraints labelConstraints = new GridBagConstraints();
		labelConstraints.gridx = 0;
		labelConstraints.anchor = GridBagConstraints.EAST;
		labelConstraints.insets = new Insets(2, 2, 2, 8);
		labelConstraints.weightx = 0.35;

		JLabel labelComponent = new JLabel(label);
		labelComponent.setForeground(ColorScheme.BRAND_ORANGE);
		labelComponent.setFont(FontManager.getRunescapeSmallFont());
		row.add(labelComponent, labelConstraints);

		GridBagConstraints valueConstraints = new GridBagConstraints();
		valueConstraints.gridx = 1;
		valueConstraints.anchor = GridBagConstraints.WEST;
		valueConstraints.insets = new Insets(2, 0, 2, 2);
		valueConstraints.weightx = 0.65;

		JLabel valueComponent = new JLabel("<html><body style='width:118px'>" + safeValue + "</body></html>");
		valueComponent.setForeground(valueColor);
		valueComponent.setFont(FontManager.getRunescapeSmallFont());
		row.add(valueComponent, valueConstraints);

		return row;
	}

	private JPanel equipmentLayout(List<PlayerEquipmentItem> equipment, Map<String, PlayerEquipmentComparison> comparisonsBySlot)
	{
		Map<String, PlayerEquipmentItem> bySlot = new HashMap<>();
		if (equipment != null)
		{
			for (PlayerEquipmentItem item : equipment)
			{
				bySlot.put(item.getSlot(), item);
			}
		}

		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(8, 26, 8, 26));
		panel.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, 210));
		panel.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, 210));

		addEquipmentSlot(panel, "Head", "head_slot.png", bySlot.get("Head"), comparisonsBySlot.get("Head"), 1, 0);
		addEquipmentSlot(panel, "Cape", "cape_slot.png", bySlot.get("Cape"), comparisonsBySlot.get("Cape"), 0, 1);
		addEquipmentSlot(panel, "Amulet", "neck_slot.png", bySlot.get("Amulet"), comparisonsBySlot.get("Amulet"), 1, 1);
		addEquipmentSlot(panel, "Ammo", "ammo_slot.png", bySlot.get("Ammo"), comparisonsBySlot.get("Ammo"), 2, 1);
		addEquipmentSlot(panel, "Weapon", "weapon_slot.png", bySlot.get("Weapon"), comparisonsBySlot.get("Weapon"), 0, 2);
		addEquipmentSlot(panel, "Body", "body_slot.png", bySlot.get("Body"), comparisonsBySlot.get("Body"), 1, 2);
		addEquipmentSlot(panel, "Shield", "shield_slot.png", bySlot.get("Shield"), comparisonsBySlot.get("Shield"), 2, 2);
		addEquipmentSlot(panel, "Legs", "legs_slot.png", bySlot.get("Legs"), comparisonsBySlot.get("Legs"), 1, 3);
		addEquipmentSlot(panel, "Hands", "hands_slot.png", bySlot.get("Hands"), comparisonsBySlot.get("Hands"), 0, 4);
		addEquipmentSlot(panel, "Feet", "feet_slot.png", bySlot.get("Feet"), comparisonsBySlot.get("Feet"), 1, 4);
		addEquipmentSlot(panel, "Ring", "ring_slot.png", bySlot.get("Ring"), comparisonsBySlot.get("Ring"), 2, 4);
		return panel;
	}

	private void addEquipmentSlot(JPanel panel, String slotName, String placeholderIcon, PlayerEquipmentItem item,
		PlayerEquipmentComparison comparison, int gridx, int gridy)
	{
		String tooltip = equipmentTooltip(slotName, item, comparison);
		ImageIcon background = loadEquipmentSlotIcon(item == null ? placeholderIcon : "blank_slot.png");
		AsyncBufferedImage itemImage = item != null && item.getItemId() > 0 ? itemManager.getImage(item.getItemId()) : null;
		JComponent slot = new EquipmentSlotComponent(background.getImage(), itemImage, tooltip);
		if (item != null && item.getItemId() > 0)
		{
			slot.setComponentPopupMenu(itemInspectMenu(item));
		}

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = gridx;
		constraints.gridy = gridy;
		constraints.insets = new Insets(3, 4, 3, 4);
		panel.add(slot, constraints);
	}

	private static String equipmentTooltip(String slotName, PlayerEquipmentItem item, PlayerEquipmentComparison comparison)
	{
		if (item == null)
		{
			return slotName + ": empty";
		}

		if (comparison == null)
		{
			return item.getItemName();
		}

		return "<html><body>"
			+ escape(item.getItemName())
			+ "<br>You: " + escape(comparison.getLocalItemName())
			+ "<br>Atk " + comparison.getAttackDelta()
			+ " | Def " + comparison.getDefenceDelta()
			+ " | Dmg " + comparison.getStrengthDelta()
			+ " | Pray " + comparison.getPrayerDelta()
			+ "</body></html>";
	}

	private JPopupMenu itemInspectMenu(PlayerEquipmentItem item)
	{
		return itemInspectMenu(item.getItemId(), item.getItemName());
	}

	private JPopupMenu itemInspectMenu(int itemId, String itemName)
	{
		JPopupMenu popupMenu = new JPopupMenu();
		JMenuItem inspect = new JMenuItem("Inspect item");
		inspect.addActionListener(event ->
		{
			if (itemInspectHandler != null)
			{
				itemInspectHandler.inspectItem(itemId, itemName);
			}
		});
		popupMenu.add(inspect);
		return popupMenu;
	}

	private static JPanel blockedEquipmentPanel()
	{
		JPanel panel = new BlockedEquipmentPanel();
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, 160));
		panel.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, 160));
		return panel;
	}

	private static JPanel deltaRow(String label, double delta, boolean lowerIsBetter)
	{
		JPanel row = new JPanel(new GridBagLayout());
		row.setOpaque(false);
		row.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 32, 22));

		GridBagConstraints labelConstraints = new GridBagConstraints();
		labelConstraints.gridx = 0;
		labelConstraints.anchor = GridBagConstraints.EAST;
		labelConstraints.insets = new Insets(2, 2, 2, 8);
		labelConstraints.weightx = 0.35;

		JLabel labelComponent = new JLabel(label);
		labelComponent.setForeground(ColorScheme.BRAND_ORANGE);
		labelComponent.setFont(FontManager.getRunescapeSmallFont());
		row.add(labelComponent, labelConstraints);

		GridBagConstraints valueConstraints = new GridBagConstraints();
		valueConstraints.gridx = 1;
		valueConstraints.anchor = GridBagConstraints.WEST;
		valueConstraints.insets = new Insets(2, 0, 2, 2);
		valueConstraints.weightx = 0.65;

		JLabel valueComponent = new JLabel(formatDelta(delta));
		valueComponent.setForeground(deltaColor(delta, lowerIsBetter));
		valueComponent.setFont(FontManager.getRunescapeSmallFont());
		row.add(valueComponent, valueConstraints);

		return row;
	}

	private static JPanel[] nonEmptyRows(String... labelValues)
	{
		List<JPanel> rows = new ArrayList<>();
		for (int i = 0; i < labelValues.length - 1; i += 2)
		{
			String value = labelValues[i + 1];
			if (value != null && !value.isEmpty())
			{
				rows.add(row(labelValues[i], value));
			}
		}
		return rows.toArray(new JPanel[0]);
	}

	private JPanel grid(StatCell[] cells, int columns)
	{
		JPanel grid = new JPanel(new GridLayout(0, columns, 1, 1));
		grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		grid.setBorder(new EmptyBorder(6, 4, 8, 4));
		int rows = (int) Math.ceil((double) cells.length / columns);
		grid.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, rows * 42 + 14));
		grid.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, rows * 42 + 14));

		for (StatCell cell : cells)
		{
			JPanel panel = new JPanel();
			panel.setLayout(new GridBagLayout());
			panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			panel.setBorder(new EmptyBorder(2, 1, 2, 1));

			JLabel icon = new JLabel("", SwingConstants.CENTER);
			icon.setToolTipText(cell.label);
			icon.setPreferredSize(new Dimension(28, 24));
			icon.setForeground(ColorScheme.BRAND_ORANGE);
			if (cell.itemId > 0 && itemManager != null)
			{
				itemManager.getImage(cell.itemId).addTo(icon);
			}
			else if (cell.iconPath != null)
			{
				icon.setIcon(loadIcon(cell.iconPath));
			}
			else if (spriteManager != null)
			{
				spriteManager.addSpriteTo(icon, cell.spriteId, 0);
			}
			else
			{
				icon.setText("?");
			}

			JLabel value = new JLabel(valueOrDash(cell.value), SwingConstants.CENTER);
			value.setToolTipText(cell.label);
			value.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			value.setFont(FontManager.getRunescapeSmallFont());

			GridBagConstraints labelConstraints = new GridBagConstraints();
			labelConstraints.gridx = 0;
			labelConstraints.gridy = 0;
			labelConstraints.weightx = 1.0;
			labelConstraints.fill = GridBagConstraints.HORIZONTAL;
			panel.add(icon, labelConstraints);

			GridBagConstraints valueConstraints = new GridBagConstraints();
			valueConstraints.gridx = 0;
			valueConstraints.gridy = 1;
			valueConstraints.weightx = 1.0;
			valueConstraints.fill = GridBagConstraints.HORIZONTAL;
			panel.add(value, valueConstraints);
			grid.add(panel);
		}

		return grid;
	}

	private static StatCell cell(String label, int spriteId, String value)
	{
		return new StatCell(label, spriteId, -1, null, value);
	}

	private static StatCell itemCell(String label, int itemId, String value)
	{
		return new StatCell(label, -1, itemId, null, value);
	}

	private static StatCell iconCell(String label, String iconPath, String value)
	{
		return new StatCell(label, -1, -1, iconPath, value);
	}

	private static ImageIcon loadIcon(String iconPath)
	{
		return new ImageIcon(Objects.requireNonNull(InspectPanel.class.getResource(ICON_PATH + iconPath)));
	}

	private static ImageIcon loadEquipmentSlotIcon(String iconPath)
	{
		return new ImageIcon(Objects.requireNonNull(InspectPanel.class.getResource(EQUIPMENT_SLOT_PATH + iconPath)));
	}

	private static String valueOrDash(String value)
	{
		return value == null || value.isEmpty() ? DASH : escape(value);
	}

	private static boolean hasAny(String... values)
	{
		for (String value : values)
		{
			if (value != null && !value.isEmpty())
			{
				return true;
			}
		}
		return false;
	}

	private static Double numericValue(String value)
	{
		if (value == null || value.isEmpty())
		{
			return null;
		}

		StringBuilder number = new StringBuilder();
		boolean started = false;
		boolean hasDecimal = false;
		for (int i = 0; i < value.length(); i++)
		{
			char c = value.charAt(i);
			if (!started && (c == '+' || c == '-' || Character.isDigit(c)))
			{
				number.append(c);
				started = true;
				continue;
			}

			if (started && Character.isDigit(c))
			{
				number.append(c);
				continue;
			}

			if (started && c == '.' && !hasDecimal)
			{
				number.append(c);
				hasDecimal = true;
				continue;
			}

			if (started)
			{
				break;
			}
		}

		if (number.length() == 0 || "+".contentEquals(number) || "-".contentEquals(number))
		{
			return null;
		}

		try
		{
			return Double.parseDouble(number.toString());
		}
		catch (NumberFormatException ex)
		{
			return null;
		}
	}

	private static String formatDelta(double delta)
	{
		if (delta == 0)
		{
			return "0";
		}

		return (delta > 0 ? "+" : "") + DELTA_FORMAT.format(delta);
	}

	private static Color deltaColor(double delta, boolean lowerIsBetter)
	{
		if (delta == 0)
		{
			return ColorScheme.LIGHT_GRAY_COLOR;
		}

		boolean favorable = lowerIsBetter ? delta < 0 : delta > 0;
		return favorable ? new Color(80, 190, 100) : new Color(210, 80, 80);
	}

	private static String escape(String value)
	{
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static final class StatCell
	{
		private final String label;
		private final int spriteId;
		private final int itemId;
		private final String iconPath;
		private final String value;

		private StatCell(String label, int spriteId, int itemId, String iconPath, String value)
		{
			this.label = label;
			this.spriteId = spriteId;
			this.itemId = itemId;
			this.iconPath = iconPath;
			this.value = value;
		}
	}

	private static final class DropFilterOption
	{
		private final String label;
		private final String drops;

		private DropFilterOption(String label, String drops)
		{
			this.label = label;
			this.drops = drops;
		}
	}

	private static final class BlockedEquipmentPanel extends JPanel
	{
		@Override
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D graphics2d = (Graphics2D) graphics.create();
			graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			int diameter = Math.min(getWidth(), getHeight()) - 56;
			int x = (getWidth() - diameter) / 2;
			int y = (getHeight() - diameter) / 2;
			Stroke previousStroke = graphics2d.getStroke();
			graphics2d.setColor(new Color(210, 60, 60));
			graphics2d.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			graphics2d.drawOval(x, y, diameter, diameter);
			graphics2d.drawLine(x + diameter - 10, y + 10, x + 10, y + diameter - 10);
			graphics2d.setStroke(previousStroke);
			graphics2d.dispose();
		}
	}

	private static final class EquipmentSlotComponent extends JComponent
	{
		private static final int SIZE = 36;
		private static final int ITEM_MAX_SIZE = 32;
		private final Image background;
		private final Image itemImage;

		private EquipmentSlotComponent(Image background, AsyncBufferedImage itemImage, String tooltip)
		{
			this.background = background;
			this.itemImage = itemImage;
			setToolTipText(tooltip);
			Dimension size = new Dimension(SIZE, SIZE);
			setPreferredSize(size);
			setMinimumSize(size);
			setMaximumSize(size);
			if (itemImage != null)
			{
				itemImage.onLoaded(this::repaint);
			}
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			graphics.drawImage(background, 0, 0, SIZE, SIZE, this);
			if (itemImage != null)
			{
				int width = itemImage.getWidth(this);
				int height = itemImage.getHeight(this);
				if (width <= 0 || height <= 0)
				{
					return;
				}

				double scale = Math.min(1.0d, Math.min((double) ITEM_MAX_SIZE / width, (double) ITEM_MAX_SIZE / height));
				int drawWidth = (int) Math.round(width * scale);
				int drawHeight = (int) Math.round(height * scale);
				int x = (SIZE - drawWidth) / 2;
				int y = (SIZE - drawHeight) / 2;
				graphics.drawImage(itemImage, x, y, drawWidth, drawHeight, this);
			}
		}
	}

	public interface SearchHandler
	{
		void search(String type, String query);
	}

	public interface GearRecommendationHandler
	{
		void findGear(NpcCombatInfo info);

		void clearGear(NpcCombatInfo info);
	}

	public interface ItemInspectHandler
	{
		void inspectItem(int itemId, String itemName);
	}

	public interface RecentInspectHandler
	{
		void inspectNpc(String npcName);

		void inspectPlayer(String playerName);

		void inspectItem(int itemId, String itemName);
	}

	public interface PinnedInspectHandler
	{
		void pinNpc(NpcCombatInfo info);

		void pinItem(ItemInspectInfo info);

		void pinPlayer(String playerName, int combatLevel, List<PlayerEquipmentItem> equipment);

		void openNpc(NpcCombatInfo info);

		void openItem(ItemInspectInfo info);

		void openPlayer(String playerName, int combatLevel, List<PlayerEquipmentItem> equipment);

		void clearNpc();

		void clearItem();

		void clearPlayer();
	}

	public interface CacheManagementHandler
	{
		void clearItemCache();

		void clearNpcCache();

		void clearAllCache();
	}

}

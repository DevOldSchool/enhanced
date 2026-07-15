package com.inspect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.inspect.item.ItemInspectInfo;
import com.inspect.item.ItemRequirementSummary;
import com.inspect.item.ItemSource;
import com.inspect.item.ItemSourceRequirement;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Skill;
import org.junit.Test;

public class InspectPluginItemRequirementTest
{
	@Test
	public void itemReadinessDoesNotIncludeSourceRequirements()
	{
		ItemInspectInfo dragonScimitar = ItemInspectInfo.builder()
			.itemId(4587)
			.displayName("Dragon scimitar")
			.requirementAttack("60")
			.sourcePlan(Collections.singletonList(new ItemSource(
				"Shops",
				Collections.singletonList("It can be bought from Daga's Scimitar Smithy after using level 64 Magic."),
				Collections.singletonList(new ItemSourceRequirement("Magic", 64, "Shops")))))
			.build();
		Map<Skill, Integer> skillLevels = new EnumMap<>(Skill.class);
		skillLevels.put(Skill.ATTACK, 70);
		skillLevels.put(Skill.MAGIC, 1);

		ItemRequirementSummary summary = InspectPlugin.itemRequirementSummary(dragonScimitar, skillLevels);

		assertEquals(Collections.singletonList("Attack 60 (70)"), summary.getMetRequirements());
		assertFalse(summary.getMissingRequirements().contains("Magic 64 for shops (1)"));
		assertEquals(Collections.emptyList(), summary.getMissingRequirements());
	}
}

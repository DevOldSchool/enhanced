package com.enhanced.player;

import java.util.Collections;
import java.util.List;
import lombok.Value;

@Value
public class PlayerInspectAnalysis
{
	String visibleValue;
	String comparisonMessage;
	List<PlayerEquipmentComparison> comparisons;

	public static PlayerInspectAnalysis loading(String visibleValue)
	{
		return new PlayerInspectAnalysis(visibleValue, "Loading visible gear comparison...", Collections.emptyList());
	}

	public static PlayerInspectAnalysis message(String visibleValue, String message)
	{
		return new PlayerInspectAnalysis(visibleValue, message, Collections.emptyList());
	}
}

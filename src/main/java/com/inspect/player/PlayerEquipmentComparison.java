package com.inspect.player;

import lombok.Value;

@Value
public class PlayerEquipmentComparison
{
	String slot;
	String inspectedItemName;
	String localItemName;
	String attackDelta;
	String defenceDelta;
	String strengthDelta;
	String prayerDelta;
}

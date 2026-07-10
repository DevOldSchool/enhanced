package com.inspect.player;

import lombok.Value;

@Value
public class PlayerEquipmentItem
{
	String slot;
	int itemId;
	String itemName;
	int price;
}

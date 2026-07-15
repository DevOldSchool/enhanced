package com.inspect.npc;

import lombok.Value;

@Value
public class NpcItemRequirementAlternativeStatus
{
	String itemName;
	boolean met;
	String matchedItemName;

	public String statusText()
	{
		if (!met)
		{
			return "Missing";
		}

		return matchedItemName == null || matchedItemName.isEmpty() ? "Have" : matchedItemName;
	}
}

package com.enhanced.item;

import java.util.Collections;
import java.util.List;
import lombok.Value;

@Value
public class ItemRequirementSummary
{
	List<String> metRequirements;
	List<String> missingRequirements;

	public static ItemRequirementSummary empty()
	{
		return new ItemRequirementSummary(Collections.emptyList(), Collections.emptyList());
	}
}

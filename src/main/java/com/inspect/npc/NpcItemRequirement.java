package com.inspect.npc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Value;

@Value
public class NpcItemRequirement
{
	String label;
	List<String> alternatives;

	public NpcItemRequirement(String label, List<String> alternatives)
	{
		this.label = label;
		this.alternatives = alternatives == null
			? Collections.emptyList()
			: Collections.unmodifiableList(new ArrayList<>(alternatives));
	}

	public boolean hasAlternatives()
	{
		return !alternatives.isEmpty();
	}
}

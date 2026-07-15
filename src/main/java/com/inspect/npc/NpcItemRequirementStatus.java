package com.inspect.npc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Value;

@Value
public class NpcItemRequirementStatus
{
	NpcItemRequirement requirement;
	List<NpcItemRequirementAlternativeStatus> alternatives;

	public NpcItemRequirementStatus(NpcItemRequirement requirement, List<NpcItemRequirementAlternativeStatus> alternatives)
	{
		this.requirement = requirement;
		this.alternatives = alternatives == null
			? Collections.emptyList()
			: Collections.unmodifiableList(new ArrayList<>(alternatives));
	}

	public String label()
	{
		return requirement == null ? "Required item" : requirement.getLabel();
	}

	public boolean isMet()
	{
		for (NpcItemRequirementAlternativeStatus alternative : alternatives)
		{
			if (alternative.isMet())
			{
				return true;
			}
		}
		return false;
	}

	public String statusText()
	{
		if (isMet())
		{
			return "Met";
		}

		if (requirement == null || !requirement.hasAlternatives())
		{
			return "Missing";
		}

		return "Missing: " + String.join(" or ", requirement.getAlternatives());
	}
}

package com.inspect.item;

import java.util.List;
import lombok.Value;

@Value
public class ItemSource
{
	String category;
	List<String> details;
	List<ItemSourceRequirement> requirements;
}

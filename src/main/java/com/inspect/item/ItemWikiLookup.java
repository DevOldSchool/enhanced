package com.inspect.item;

import lombok.Value;

@Value
class ItemWikiLookup
{
	String page;
	String anchor;
	String sourceUrl;
}

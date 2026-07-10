package com.inspect.item;

import lombok.Value;

@Value
public class ItemPriceSummary
{
	String gePrice;
	String highAlch;
	String lowAlch;
	String highAlchProfit;
	Integer highAlchProfitValue;
}

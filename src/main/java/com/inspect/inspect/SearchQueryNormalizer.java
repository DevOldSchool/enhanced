package com.inspect.inspect;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class SearchQueryNormalizer
{
	private static final Map<String, String> ITEM_ALIASES = new LinkedHashMap<>();
	private static final Map<String, String> NPC_ALIASES = new LinkedHashMap<>();
	private static final Map<String, String> TOKEN_ALIASES = new LinkedHashMap<>();

	static
	{
		ITEM_ALIASES.put("b gloves", "Barrows gloves");
		ITEM_ALIASES.put("barrows glove", "Barrows gloves");
		ITEM_ALIASES.put("bp", "Toxic blowpipe");
		ITEM_ALIASES.put("blowpipe", "Toxic blowpipe");
		ITEM_ALIASES.put("bowfa", "Bow of faerdhinen");
		ITEM_ALIASES.put("bofa", "Bow of faerdhinen");
		ITEM_ALIASES.put("bcp", "Bandos chestplate");
		ITEM_ALIASES.put("d boots", "Dragon boots");
		ITEM_ALIASES.put("dds", "Dragon dagger");
		ITEM_ALIASES.put("ddp", "Dragon dagger");
		ITEM_ALIASES.put("d scim", "Dragon scimitar");
		ITEM_ALIASES.put("d scimmy", "Dragon scimitar");
		ITEM_ALIASES.put("dwh", "Dragon warhammer");
		ITEM_ALIASES.put("bgs", "Bandos godsword");
		ITEM_ALIASES.put("sgs", "Saradomin godsword");
		ITEM_ALIASES.put("ags", "Armadyl godsword");
		ITEM_ALIASES.put("zgs", "Zamorak godsword");
		ITEM_ALIASES.put("fury", "Amulet of fury");
		ITEM_ALIASES.put("glory", "Amulet of glory");
		ITEM_ALIASES.put("occult", "Occult necklace");
		ITEM_ALIASES.put("pegs", "Pegasian boots");
		ITEM_ALIASES.put("prims", "Primordial boots");
		ITEM_ALIASES.put("eternals", "Eternal boots");
		ITEM_ALIASES.put("tassets", "Bandos tassets");
		ITEM_ALIASES.put("tbow", "Twisted bow");
		ITEM_ALIASES.put("trident", "Trident of the seas");
		ITEM_ALIASES.put("whip", "Abyssal whip");

		NPC_ALIASES.put("abby demon", "Abyssal demon");
		NPC_ALIASES.put("abby demons", "Abyssal demon");
		NPC_ALIASES.put("dust dev", "Dust devil");
		NPC_ALIASES.put("dust devs", "Dust devil");
		NPC_ALIASES.put("garg", "Gargoyle");
		NPC_ALIASES.put("gargs", "Gargoyle");
		NPC_ALIASES.put("kbd", "King Black Dragon");
		NPC_ALIASES.put("kq", "Kalphite Queen");
		NPC_ALIASES.put("nech", "Nechryael");
		NPC_ALIASES.put("nechs", "Nechryael");

		TOKEN_ALIASES.put("addy", "adamant");
		TOKEN_ALIASES.put("addam", "adamant");
		TOKEN_ALIASES.put("ahrim", "Ahrim's");
		TOKEN_ALIASES.put("anc", "ancient");
		TOKEN_ALIASES.put("arma", "armadyl");
		TOKEN_ALIASES.put("blk", "black");
		TOKEN_ALIASES.put("cbow", "crossbow");
		TOKEN_ALIASES.put("dh", "Dharok's");
		TOKEN_ALIASES.put("d", "dragon");
		TOKEN_ALIASES.put("drag", "dragon");
		TOKEN_ALIASES.put("guthan", "Guthan's");
		TOKEN_ALIASES.put("karil", "Karil's");
		TOKEN_ALIASES.put("mith", "mithril");
		TOKEN_ALIASES.put("obby", "obsidian");
		TOKEN_ALIASES.put("scim", "scimitar");
		TOKEN_ALIASES.put("scimmy", "scimitar");
		TOKEN_ALIASES.put("torag", "Torag's");
		TOKEN_ALIASES.put("verac", "Verac's");
	}

	private SearchQueryNormalizer()
	{
	}

	public static String normalize(String type, String query)
	{
		String cleaned = clean(query);
		if (cleaned.isEmpty())
		{
			return "";
		}

		Map<String, String> aliases = "NPC".equals(type) ? NPC_ALIASES : ITEM_ALIASES;
		String exact = aliases.get(cleaned.toLowerCase(Locale.ENGLISH));
		if (exact != null)
		{
			return exact;
		}

		return expandTokens(cleaned);
	}

	private static String clean(String query)
	{
		if (query == null)
		{
			return "";
		}

		return query
			.replace('\u00A0', ' ')
			.replace('_', ' ')
			.replace('-', ' ')
			.replace('\u2018', '\'')
			.replace('\u2019', '\'')
			.trim()
			.replaceAll("\\s+", " ");
	}

	private static String expandTokens(String query)
	{
		String[] tokens = query.split("\\s+");
		for (int i = 0; i < tokens.length; i++)
		{
			tokens[i] = expandToken(tokens[i]);
		}
		return String.join(" ", tokens);
	}

	private static String expandToken(String token)
	{
		StringBuilder suffix = new StringBuilder();
		String word = token;
		while (!word.isEmpty() && !Character.isLetterOrDigit(word.charAt(word.length() - 1)))
		{
			suffix.insert(0, word.charAt(word.length() - 1));
			word = word.substring(0, word.length() - 1);
		}

		String expanded = TOKEN_ALIASES.get(word.toLowerCase(Locale.ENGLISH));
		return (expanded == null ? word : expanded) + suffix;
	}
}

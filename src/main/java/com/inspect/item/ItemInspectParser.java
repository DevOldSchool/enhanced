package com.inspect.item;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ItemInspectParser
{
	private static final Pattern WIKI_LINK = Pattern.compile("\\[\\[([^]|]+\\|)?([^]]+)]]");
	private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
	private static final Pattern TEMPLATE = Pattern.compile("\\{\\{([^{}|]+\\|)?([^{}|]+)}}");
	private static final Pattern REQUIREMENT_PAIR = Pattern.compile("(\\d+)\\s+(Attack|Strength|Defence|Ranged|Magic|Prayer|Hitpoints|Slayer)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern REVERSED_REQUIREMENT_PAIR = Pattern.compile("(Attack|Strength|Defence|Ranged|Magic|Prayer|Hitpoints|Slayer)\\s+level\\s+of\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern QUEST_REQUIREMENT = Pattern.compile("(?:completion of|completed?|requires)\\s+([^.;]+?)(?:\\s+(?:to|and|before)|[.;])", Pattern.CASE_INSENSITIVE);
	private static final Pattern SOURCE_NEGATION = Pattern.compile("\\b(?:not|never|neither|cannot|can't|isn't|aren't|wasn't|weren't|without)\\b");
	private static final Pattern NOT_ONLY = Pattern.compile("\\bnot\\s+only\\b");

	ItemInspectInfo parse(int itemId, String fallbackName, ItemWikiLookup lookup, String wikitext)
	{
		String itemInfobox = extractInfobox(wikitext, "{{Infobox Item");
		if (itemInfobox == null)
		{
			return null;
		}

		Map<String, String> itemFields = parseFields(itemInfobox);
		String suffix = selectVersionSuffix(itemId, lookup.getAnchor(), itemFields);
		if (suffix == null)
		{
			return null;
		}
		int resolvedItemId = itemId >= 0 ? itemId : parseFirstId(value(itemFields, "id", suffix, null));

		Map<String, String> bonusFields = new LinkedHashMap<>();
		String bonusesInfobox = extractInfobox(wikitext, "{{Infobox Bonuses");
		if (bonusesInfobox != null)
		{
			bonusFields.putAll(parseFields(bonusesInfobox));
		}
		Map<String, String> requirements = parseRequirements(wikitext);
		String questRequirements = parseQuestRequirements(wikitext);

		return ItemInspectInfo.builder()
			.itemId(resolvedItemId)
			.wikiPage(lookup.getPage())
			.wikiAnchor(lookup.getAnchor())
			.displayName(value(itemFields, "name", suffix, fallbackName))
			.examine(value(itemFields, "examine", suffix, null))
			.members(value(itemFields, "members", suffix, null))
			.tradeable(value(itemFields, "tradeable", suffix, null))
			.equipable(value(itemFields, "equipable", suffix, null))
			.stackable(value(itemFields, "stackable", suffix, null))
			.noteable(value(itemFields, "noteable", suffix, null))
			.value(formatCoins(value(itemFields, "value", suffix, null)))
			.weight(formatWeight(value(itemFields, "weight", suffix, null)))
			.highAlch(formatCoins(valueAny(itemFields, suffix, "highalch", "high alch", "highalc", "high alchemy")))
			.lowAlch(formatCoins(valueAny(itemFields, suffix, "lowalch", "low alch", "lowalc", "low alchemy")))
			.attackStab(value(bonusFields, "astab", suffix, null))
			.attackSlash(value(bonusFields, "aslash", suffix, null))
			.attackCrush(value(bonusFields, "acrush", suffix, null))
			.attackMagic(value(bonusFields, "amagic", suffix, null))
			.attackRanged(value(bonusFields, "arange", suffix, null))
			.defenceStab(value(bonusFields, "dstab", suffix, null))
			.defenceSlash(value(bonusFields, "dslash", suffix, null))
			.defenceCrush(value(bonusFields, "dcrush", suffix, null))
			.defenceMagic(value(bonusFields, "dmagic", suffix, null))
			.defenceRanged(value(bonusFields, "drange", suffix, null))
			.strength(value(bonusFields, "str", suffix, null))
			.rangedStrength(value(bonusFields, "rstr", suffix, null))
			.magicDamage(value(bonusFields, "mdmg", suffix, null))
			.prayer(value(bonusFields, "prayer", suffix, null))
			.slot(value(bonusFields, "slot", suffix, null))
			.attackSpeed(formatTicks(value(bonusFields, "speed", suffix, null)))
			.attackRange(value(bonusFields, "attackrange", suffix, null))
			.requirementAttack(requirements.get("attack"))
			.requirementStrength(requirements.get("strength"))
			.requirementDefence(requirements.get("defence"))
			.requirementRanged(requirements.get("ranged"))
			.requirementMagic(requirements.get("magic"))
			.requirementPrayer(requirements.get("prayer"))
			.requirementHitpoints(requirements.get("hitpoints"))
			.requirementSlayer(requirements.get("slayer"))
			.questRequirements(questRequirements)
			.sourceSummary(parseSourceSummary(wikitext))
			.fetchedAtEpochSecond(Instant.now().getEpochSecond())
			.sourceUrl(lookup.getSourceUrl())
			.build();
	}

	static String extractInfobox(String wikitext, String infoboxStart)
	{
		int start = wikitext.indexOf(infoboxStart);
		if (start < 0)
		{
			return null;
		}

		int depth = 0;
		for (int i = start; i < wikitext.length() - 1; i++)
		{
			String token = wikitext.substring(i, i + 2);
			if ("{{".equals(token))
			{
				depth++;
				i++;
			}
			else if ("}}".equals(token))
			{
				depth--;
				i++;
				if (depth == 0)
				{
					return wikitext.substring(start, i + 1);
				}
			}
		}

		return null;
	}

	static Map<String, String> parseFields(String infobox)
	{
		Map<String, String> fields = new LinkedHashMap<>();
		for (String part : splitTopLevel(infobox))
		{
			part = part.trim();
			if (part.endsWith("}}"))
			{
				part = part.substring(0, part.length() - 2).trim();
			}

			int equals = part.indexOf('=');
			if (equals <= 0)
			{
				continue;
			}

			String key = part.substring(0, equals).trim().toLowerCase();
			String value = normalizeValue(part.substring(equals + 1).trim());
			if (!key.isEmpty())
			{
				fields.put(key, value);
			}
		}
		return fields;
	}

	private static String selectVersionSuffix(int itemId, String anchor, Map<String, String> fields)
	{
		if (itemId >= 0)
		{
			String id = Integer.toString(itemId);
			for (Map.Entry<String, String> entry : fields.entrySet())
			{
				String key = entry.getKey();
				if (!key.matches("id\\d*"))
				{
					continue;
				}

				for (String candidate : entry.getValue().split(","))
				{
					if (id.equals(candidate.trim()))
					{
						return key.substring(2);
					}
				}
			}
		}

		if (anchor != null)
		{
			String normalizedAnchor = anchor.replace('_', ' ').trim();
			for (Map.Entry<String, String> entry : fields.entrySet())
			{
				if (entry.getKey().startsWith("version") && normalizedAnchor.equalsIgnoreCase(entry.getValue()))
				{
					return entry.getKey().substring("version".length());
				}
			}
		}

		if (itemId < 0)
		{
			if (fields.containsKey("id"))
			{
				return "";
			}
			return fields.containsKey("id1") || fields.containsKey("version1") ? "1" : "";
		}

		return fields.containsKey("id") || !fields.containsKey("version1") ? "" : null;
	}

	private static int parseFirstId(String value)
	{
		if (value == null)
		{
			return -1;
		}

		for (String candidate : value.split(","))
		{
			String trimmed = candidate.trim();
			if (trimmed.matches("\\d+"))
			{
				return Integer.parseInt(trimmed);
			}
		}
		return -1;
	}

	private static String value(Map<String, String> fields, String key, String suffix, String fallback)
	{
		String suffixed = fields.get(key + suffix);
		if (suffixed != null && !suffixed.isEmpty())
		{
			return suffixed;
		}

		String unsuffixed = fields.get(key);
		if (unsuffixed != null && !unsuffixed.isEmpty())
		{
			return unsuffixed;
		}

		return fallback;
	}

	private static String valueAny(Map<String, String> fields, String suffix, String... keys)
	{
		for (String key : keys)
		{
			String value = value(fields, key, suffix, null);
			if (value != null && !value.isEmpty())
			{
				return value;
			}
		}
		return null;
	}

	private static String normalizeValue(String value)
	{
		String normalized = value.replace("&nbsp;", " ").replace("&#160;", " ");
		Matcher linkMatcher = WIKI_LINK.matcher(normalized);
		StringBuilder linkBuffer = new StringBuilder();
		while (linkMatcher.find())
		{
			linkMatcher.appendReplacement(linkBuffer, Matcher.quoteReplacement(linkMatcher.group(2)));
		}
		linkMatcher.appendTail(linkBuffer);

		Matcher templateMatcher = TEMPLATE.matcher(linkBuffer.toString());
		StringBuilder templateBuffer = new StringBuilder();
		while (templateMatcher.find())
		{
			templateMatcher.appendReplacement(templateBuffer, Matcher.quoteReplacement(templateMatcher.group(2)));
		}
		templateMatcher.appendTail(templateBuffer);

		return HTML_TAG.matcher(templateBuffer.toString())
			.replaceAll("")
			.replace("'''", "")
			.replace("''", "")
			.trim();
	}

	private static String formatCoins(String value)
	{
		if (value == null || value.contains("coin") || !value.matches("\\d+"))
		{
			return value;
		}

		return value + " coins";
	}

	private static String formatWeight(String value)
	{
		if (value == null || value.contains("kg") || !value.matches("-?\\d+(\\.\\d+)?"))
		{
			return value;
		}

		return value + " kg";
	}

	private static String formatTicks(String value)
	{
		if (value == null || !value.matches("\\d+"))
		{
			return value;
		}

		int ticks = Integer.parseInt(value);
		double seconds = ticks * 0.6d;
		String secondsText = seconds == Math.rint(seconds) ? Integer.toString((int) seconds) : String.format(Locale.ROOT, "%.1f", seconds);
		return ticks + " ticks (" + secondsText + " seconds)";
	}

	private static Map<String, String> parseRequirements(String wikitext)
	{
		Map<String, String> requirements = new LinkedHashMap<>();
		String articleLead = wikitext;
		int firstHeading = articleLead.indexOf("\n==");
		if (firstHeading > 0)
		{
			articleLead = articleLead.substring(0, firstHeading);
		}

		String normalized = normalizeValue(articleLead)
			.replace('\n', ' ')
			.replaceAll("\\s+", " ");
		for (String sentence : normalized.split("(?<=[.!?])\\s+"))
		{
			String lower = sentence.toLowerCase();
			if (!(lower.contains("requires") || lower.contains("requiring")))
			{
				continue;
			}

			if (!(lower.contains("to wield")
				|| lower.contains("to wear")
				|| lower.contains("to be worn")
				|| lower.contains("to equip")
				|| lower.contains("to use")))
			{
				continue;
			}

			addRequirementMatches(requirements, REQUIREMENT_PAIR.matcher(sentence), false);
			addRequirementMatches(requirements, REVERSED_REQUIREMENT_PAIR.matcher(sentence), true);
		}
		return requirements;
	}

	private static void addRequirementMatches(Map<String, String> requirements, Matcher matcher, boolean reversed)
	{
		while (matcher.find())
		{
			String skill = reversed ? matcher.group(1) : matcher.group(2);
			String level = reversed ? matcher.group(2) : matcher.group(1);
			requirements.put(skill.toLowerCase(), level);
		}
	}

	private static String parseQuestRequirements(String wikitext)
	{
		String lead = wikitext;
		int firstHeading = lead.indexOf("\n==");
		if (firstHeading > 0)
		{
			lead = lead.substring(0, firstHeading);
		}

		String normalized = normalizeValue(lead)
			.replace('\n', ' ')
			.replaceAll("\\s+", " ");
		Matcher matcher = QUEST_REQUIREMENT.matcher(normalized);
		while (matcher.find())
		{
			String candidate = matcher.group(1).trim();
			String lower = candidate.toLowerCase();
			if ((lower.contains("quest") || lower.contains("diary") || lower.contains("miniquest"))
				&& candidate.length() <= 120)
			{
				return candidate;
			}
		}
		return null;
	}

	private static String parseSourceSummary(String wikitext)
	{
		String lower = wikitext.toLowerCase(Locale.ROOT);
		List<String> sources = new java.util.ArrayList<>();
		addSourceIfPresent(sources, lower, "dropped by", "Dropped");
		addSourceIfPresent(sources, lower, "sold by", "Sold");
		addSourceIfPresent(sources, lower, "shop", "Shop");
		addSourceIfPresent(sources, lower, "created", "Created");
		addSourceIfPresent(sources, lower, "made by", "Created");
		addSourceIfPresent(sources, lower, "reward", "Reward");
		addSourceIfPresent(sources, lower, "treasure trails", "Clue reward");
		return sources.isEmpty() ? null : String.join(", ", sources);
	}

	private static void addSourceIfPresent(List<String> sources, String lowerWikitext, String needle, String label)
	{
		if (containsNonNegatedSource(lowerWikitext, needle) && !sources.contains(label))
		{
			sources.add(label);
		}
	}

	private static boolean containsNonNegatedSource(String lowerWikitext, String needle)
	{
		int searchFrom = 0;
		int sourceIndex;
		while ((sourceIndex = lowerWikitext.indexOf(needle, searchFrom)) >= 0)
		{
			if (!isNegatedSource(lowerWikitext, sourceIndex))
			{
				return true;
			}
			searchFrom = sourceIndex + needle.length();
		}
		return false;
	}

	private static boolean isNegatedSource(String lowerWikitext, int sourceIndex)
	{
		int clauseStart = 0;
		for (char boundary : new char[]{'.', '!', '?', ';', '\n'})
		{
			clauseStart = Math.max(clauseStart, lowerWikitext.lastIndexOf(boundary, sourceIndex - 1) + 1);
		}

		int contrast = lowerWikitext.lastIndexOf(" but ", sourceIndex);
		if (contrast >= clauseStart)
		{
			clauseStart = contrast + " but ".length();
		}
		contrast = lowerWikitext.lastIndexOf(" however ", sourceIndex);
		if (contrast >= clauseStart)
		{
			clauseStart = contrast + " however ".length();
		}

		String prefix = NOT_ONLY.matcher(lowerWikitext.substring(clauseStart, sourceIndex)).replaceAll("");
		return SOURCE_NEGATION.matcher(prefix).find();
	}

	private static Iterable<String> splitTopLevel(String infobox)
	{
		Map<Integer, String> parts = new LinkedHashMap<>();
		int depth = 0;
		int linkDepth = 0;
		int start = 0;
		int index = 0;
		for (int i = 0; i < infobox.length(); i++)
		{
			if (i < infobox.length() - 1 && "[[".equals(infobox.substring(i, i + 2)))
			{
				linkDepth++;
				i++;
				continue;
			}
			if (i < infobox.length() - 1 && "]]".equals(infobox.substring(i, i + 2)))
			{
				linkDepth = Math.max(0, linkDepth - 1);
				i++;
				continue;
			}
			if (i < infobox.length() - 1 && "{{".equals(infobox.substring(i, i + 2)))
			{
				depth++;
				i++;
				continue;
			}
			if (i < infobox.length() - 1 && "}}".equals(infobox.substring(i, i + 2)))
			{
				depth = Math.max(0, depth - 1);
				i++;
				continue;
			}
			if (infobox.charAt(i) == '|' && depth == 1 && linkDepth == 0)
			{
				parts.put(index++, infobox.substring(start, i));
				start = i + 1;
			}
		}
		parts.put(index, infobox.substring(start));
		return parts.values();
	}
}

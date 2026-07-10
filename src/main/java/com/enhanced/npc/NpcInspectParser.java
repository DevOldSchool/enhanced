package com.enhanced.npc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class NpcInspectParser
{
	private static final Pattern WIKI_LINK = Pattern.compile("\\[\\[([^]|]+\\|)?([^]]+)]]");
	private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
	private static final Pattern TEMPLATE = Pattern.compile("\\{\\{([^{}|]+\\|)?([^{}|]+)}}");
	private static final Pattern DROP_LINE = Pattern.compile("\\|name\\s*=\\s*([^\\n|}]+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern DROPS_HEADING = Pattern.compile("(?im)^==[ \\t]*Drops[ \\t]*==[ \\t]*$");
	private static final Pattern LEVEL_TWO_HEADING = Pattern.compile("(?m)^==(?!=)[^\\r\\n]*?(?<![=])==[ \\t]*$");

	NpcCombatInfo parse(int npcId, String fallbackName, NpcWikiLookup lookup, String wikitext)
	{
		String infobox = extractInfobox(wikitext);
		if (infobox == null)
		{
			return null;
		}

		Map<String, String> fields = parseFields(infobox);
		String suffix = selectVersionSuffix(npcId, lookup.getAnchor(), fields);
		if (suffix == null)
		{
			return null;
		}
		int resolvedNpcId = npcId >= 0 ? npcId : parseFirstId(value(fields, "id", suffix, null));

		return NpcCombatInfo.builder()
			.npcId(resolvedNpcId)
			.wikiPage(lookup.getPage())
			.wikiAnchor(lookup.getAnchor())
			.displayName(value(fields, "name", suffix, fallbackName))
			.combatLevel(value(fields, "combat", suffix, null))
			.xpBonus(value(fields, "xpbonus", suffix, null))
			.maxHit(value(fields, "max hit", suffix, null))
			.aggressive(value(fields, "aggressive", suffix, null))
			.poisonous(value(fields, "poisonous", suffix, null))
			.attackStyle(value(fields, "attack style", suffix, null))
			.attackSpeed(value(fields, "attack speed", suffix, null))
			.respawnTime(formatTicks(value(fields, "respawn", suffix, null)))
			.hitpoints(value(fields, "hitpoints", suffix, null))
			.attack(value(fields, "att", suffix, null))
			.strength(value(fields, "str", suffix, null))
			.defence(value(fields, "def", suffix, null))
			.magic(value(fields, "mage", suffix, null))
			.ranged(value(fields, "range", suffix, null))
			.attackBonus(value(fields, "attbns", suffix, null))
			.strengthBonus(value(fields, "strbns", suffix, null))
			.magicAttack(value(fields, "amagic", suffix, null))
			.magicStrength(value(fields, "mbns", suffix, null))
			.rangedAttack(value(fields, "arange", suffix, null))
			.rangedStrength(value(fields, "rngbns", suffix, null))
			.stabDefence(value(fields, "dstab", suffix, null))
			.slashDefence(value(fields, "dslash", suffix, null))
			.crushDefence(value(fields, "dcrush", suffix, null))
			.magicDefence(value(fields, "dmagic", suffix, null))
			.elementalWeaknessType(value(fields, "elementalweaknesstype", suffix, null))
			.elementalWeakness(value(fields, "elementalweaknesspercent", suffix, "No elemental weakness"))
			.lightRangedDefence(value(fields, "dlight", suffix, null))
			.standardRangedDefence(value(fields, "dstandard", suffix, null))
			.heavyRangedDefence(value(fields, "dheavy", suffix, null))
			.poisonResistance(formatResistance(value(fields, "poisonresistance", suffix, null)))
			.venomResistance(formatResistance(value(fields, "venomresistance", suffix, null)))
			.cannonImmunity(formatImmunity(value(fields, "immunecannon", suffix, null)))
			.thrallImmunity(formatImmunity(value(fields, "immunethrall", suffix, null)))
			.slayerLevel(value(fields, "slaylvl", suffix, null))
			.slayerCategory(firstNonEmpty(
				value(fields, "cat", suffix, null),
				value(fields, "slaycat", suffix, null),
				value(fields, "assignedby", suffix, null)
			))
			.assignedBy(value(fields, "assignedby", suffix, null))
			.taskOnly(firstNonEmpty(value(fields, "taskonly", suffix, null), value(fields, "task only", suffix, null)))
			.superiorVariant(firstNonEmpty(
				value(fields, "superior", suffix, null),
				value(fields, "superior monster", suffix, null)
			))
			.notableDrops(dropSummary(wikitext, "uncommon", "rare", "very rare"))
			.rareDrops(dropSummary(wikitext, "rare", "very rare"))
			.uniqueDrops(classifiedDrops(wikitext, DropCategory.UNIQUE))
			.alchableDrops(classifiedDrops(wikitext, DropCategory.ALCHABLE))
			.clueDrops(classifiedDrops(wikitext, DropCategory.CLUE))
			.resourceDrops(classifiedDrops(wikitext, DropCategory.RESOURCE))
			.supplyDrops(classifiedDrops(wikitext, DropCategory.SUPPLY))
			.fetchedAtEpochSecond(Instant.now().getEpochSecond())
			.sourceUrl(lookup.getSourceUrl())
			.build();
	}

	static String extractInfobox(String wikitext)
	{
		int start = wikitext.indexOf("{{Infobox Monster");
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
		String infoboxBody = infobox.endsWith("}}") ? infobox.substring(0, infobox.length() - 2) : infobox;
		for (String part : splitTopLevel(infoboxBody))
		{
			part = part.trim();
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

	private static String selectVersionSuffix(int npcId, String anchor, Map<String, String> fields)
	{
		if (npcId < 0)
		{
			String anchorSuffix = selectVersionSuffixByAnchor(anchor, fields);
			if (anchorSuffix != null)
			{
				return anchorSuffix;
			}

			if (fields.containsKey("id") || !fields.containsKey("version1"))
			{
				return "";
			}
			return "1";
		}

		String id = Integer.toString(npcId);
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

		String anchorSuffix = selectVersionSuffixByAnchor(anchor, fields);
		if (anchorSuffix != null)
		{
			return anchorSuffix;
		}

		return fields.containsKey("id") || !fields.containsKey("version1") ? "" : null;
	}

	private static String selectVersionSuffixByAnchor(String anchor, Map<String, String> fields)
	{
		if (anchor == null)
		{
			return null;
		}

		String normalizedAnchor = anchor.replace('_', ' ').trim();
		for (Map.Entry<String, String> entry : fields.entrySet())
		{
			if (entry.getKey().startsWith("version") && normalizedAnchor.equalsIgnoreCase(entry.getValue()))
			{
				return entry.getKey().substring("version".length());
			}
		}
		return null;
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

		return HTML_TAG.matcher(normalizeTemplates(linkBuffer.toString()))
			.replaceAll("")
			.replace("'''", "")
			.replace("''", "")
			.trim();
	}

	private static String normalizeTemplates(String value)
	{
		String normalized = value;
		while (true)
		{
			Matcher matcher = TEMPLATE.matcher(normalized);
			if (!matcher.find())
			{
				return normalized;
			}

			StringBuilder buffer = new StringBuilder();
			do
			{
				matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(2)));
			}
			while (matcher.find());
			matcher.appendTail(buffer);
			normalized = buffer.toString();
		}
	}

	private static String formatTicks(String value)
	{
		if (value == null || !value.matches("\\d+"))
		{
			return value;
		}

		int ticks = Integer.parseInt(value);
		double seconds = ticks * 0.6d;
		String secondsText = seconds == Math.rint(seconds) ? Integer.toString((int) seconds) : String.format(Locale.ENGLISH, "%.1f", seconds);
		return ticks + " ticks (" + secondsText + " seconds)";
	}

	private static String formatResistance(String value)
	{
		if (value == null || value.contains("%"))
		{
			return value;
		}

		return value + "% resistance";
	}

	private static String formatImmunity(String value)
	{
		if (value == null)
		{
			return null;
		}

		if ("yes".equalsIgnoreCase(value))
		{
			return "Immune";
		}

		if ("no".equalsIgnoreCase(value))
		{
			return "Not immune";
		}

		return value;
	}

	private static String firstNonEmpty(String... values)
	{
		for (String value : values)
		{
			if (value != null && !value.isEmpty())
			{
				return value;
			}
		}
		return null;
	}

	private static String dropSummary(String wikitext, String... rarityTokens)
	{
		String drops = dropSection(wikitext);
		if (drops == null)
		{
			return null;
		}

		List<String> names = new ArrayList<>();
		Matcher matcher = DROP_LINE.matcher(drops);
		while (matcher.find() && names.size() < 6)
		{
			int lineStart = Math.max(0, drops.lastIndexOf('\n', matcher.start()));
			int lineEnd = drops.indexOf('\n', matcher.end());
			String line = drops.substring(lineStart, lineEnd < 0 ? drops.length() : lineEnd).toLowerCase();
			if (!containsAny(line, rarityTokens))
			{
				continue;
			}

			String name = normalizeValue(matcher.group(1));
			if (!name.isEmpty() && !names.contains(name))
			{
				names.add(name);
			}
		}
		return names.isEmpty() ? null : String.join(", ", names);
	}

	private static boolean containsAny(String value, String... tokens)
	{
		for (String token : tokens)
		{
			if (value.contains(token))
			{
				return true;
			}
		}
		return false;
	}

	private static String classifiedDrops(String wikitext, DropCategory category)
	{
		List<String> drops = dropNames(wikitext);
		List<String> matching = new ArrayList<>();
		for (String drop : drops)
		{
			if (category.matches(drop) && !matching.contains(drop))
			{
				matching.add(drop);
				if (matching.size() >= 6)
				{
					break;
				}
			}
		}
		return matching.isEmpty() ? null : String.join(", ", matching);
	}

	private static List<String> dropNames(String wikitext)
	{
		String drops = dropSection(wikitext);
		if (drops == null)
		{
			return java.util.Collections.emptyList();
		}

		List<String> names = new ArrayList<>();
		Matcher matcher = DROP_LINE.matcher(drops);
		while (matcher.find() && names.size() < 30)
		{
			String name = normalizeValue(matcher.group(1));
			if (!name.isEmpty() && !names.contains(name))
			{
				names.add(name);
			}
		}
		return names;
	}

	private static String dropSection(String wikitext)
	{
		Matcher dropsHeading = DROPS_HEADING.matcher(wikitext);
		if (!dropsHeading.find())
		{
			return null;
		}

		int start = dropsHeading.end();
		Matcher nextHeading = LEVEL_TWO_HEADING.matcher(wikitext);
		int end = nextHeading.find(start) ? nextHeading.start() : wikitext.length();
		return wikitext.substring(start, end);
	}

	private enum DropCategory
	{
		UNIQUE
			{
				@Override
				boolean matches(String drop)
				{
					String lower = drop.toLowerCase();
					return lower.contains("clue") || lower.contains("key") || lower.contains("shard") || lower.contains("sigil")
						|| lower.contains("pet") || lower.contains("visage") || lower.contains("head") || lower.contains("unique");
				}
			},
		ALCHABLE
			{
				@Override
				boolean matches(String drop)
				{
					String lower = drop.toLowerCase();
					return lower.contains("rune ") || lower.contains("dragon ") || lower.contains("adamant ")
						|| lower.contains("mithril ") || lower.contains("battleaxe") || lower.contains("platebody")
						|| lower.contains("kiteshield") || lower.contains("longsword");
				}
			},
		CLUE
			{
				@Override
				boolean matches(String drop)
				{
					return drop.toLowerCase().contains("clue");
				}
			},
		RESOURCE
			{
				@Override
				boolean matches(String drop)
				{
					String lower = drop.toLowerCase();
					return lower.contains("ore") || lower.contains("log") || lower.contains("bar") || lower.contains("herb")
						|| lower.startsWith("grimy ") || lower.contains(" weed") || lower.contains("seed")
						|| lower.contains("hide") || lower.contains("scale") || lower.contains("essence");
				}
			},
		SUPPLY
			{
				@Override
				boolean matches(String drop)
				{
					String lower = drop.toLowerCase();
					return lower.contains("bone") || lower.contains("rune") || lower.contains("arrow") || lower.contains("bolt")
						|| lower.contains("food") || lower.contains("shark") || lower.contains("lobster")
						|| lower.contains("potion") || lower.contains("ammo");
				}
			};

		abstract boolean matches(String drop);
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

package com.inspect.npc;

import com.inspect.item.ItemInspectInfo;
import lombok.Getter;

import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

public enum CombatStyleRecommendation
{
	STAB("Stab melee", "Stab defence")
		{
			@Override
			double score(ItemInspectInfo item)
			{
				return offensiveScore(item.getAttackStab(), item.getStrength(), item.getPrayer());
			}
		},
	SLASH("Slash melee", "Slash defence")
		{
			@Override
			double score(ItemInspectInfo item)
			{
				return offensiveScore(item.getAttackSlash(), item.getStrength(), item.getPrayer());
			}
		},
	CRUSH("Crush melee", "Crush defence")
		{
			@Override
			double score(ItemInspectInfo item)
			{
				return offensiveScore(item.getAttackCrush(), item.getStrength(), item.getPrayer());
			}
		},
	MAGIC("Magic", "Magic defence")
		{
			@Override
			double score(ItemInspectInfo item)
			{
				return offensiveScore(item.getAttackMagic(), item.getMagicDamage(), item.getPrayer());
			}
		},
	RANGED("Ranged", "Ranged defence")
		{
			@Override
			double score(ItemInspectInfo item)
			{
				return offensiveScore(item.getAttackRanged(), item.getRangedStrength(), item.getPrayer());
			}
		};

	@Getter
    private final String displayName;
	private final String defenceLabel;

	CombatStyleRecommendation(String displayName, String defenceLabel)
	{
		this.displayName = displayName;
		this.defenceLabel = defenceLabel;
	}

    String getDefenceLabel()
	{
		return defenceLabel;
	}

	abstract double score(ItemInspectInfo item);

	boolean isRelevant(ItemInspectInfo item)
	{
		return score(item) > 0;
	}

	public static CombatStyleRecommendation forNpc(NpcCombatInfo info)
	{
		if (info == null)
		{
			return null;
		}

		return java.util.stream.Stream.of(
				candidate(STAB, info.getStabDefence()),
				candidate(SLASH, info.getSlashDefence()),
				candidate(CRUSH, info.getCrushDefence()),
				candidate(MAGIC, info.getMagicDefence()),
				candidate(RANGED, lowest(info.getLightRangedDefence(), info.getStandardRangedDefence(), info.getHeavyRangedDefence()))
			)
			.filter(Objects::nonNull)
			.min(Comparator.comparingDouble(candidate -> candidate.defence))
			.map(candidate -> candidate.style)
			.orElse(null);
	}

	static Double numericValue(String value)
	{
		if (value == null || value.isEmpty())
		{
			return null;
		}

		String normalized = value.replace(",", "").toLowerCase(Locale.ENGLISH);
		StringBuilder number = new StringBuilder();
		boolean started = false;
		boolean hasDecimal = false;
		for (int i = 0; i < normalized.length(); i++)
		{
			char c = normalized.charAt(i);
			if (!started && (c == '+' || c == '-' || Character.isDigit(c)))
			{
				number.append(c);
				started = true;
				continue;
			}

			if (started && Character.isDigit(c))
			{
				number.append(c);
				continue;
			}

			if (started && c == '.' && !hasDecimal)
			{
				number.append(c);
				hasDecimal = true;
				continue;
			}

			if (started)
			{
				break;
			}
		}

		if (number.length() == 0 || "+".contentEquals(number) || "-".contentEquals(number))
		{
			return null;
		}

		try
		{
			return Double.parseDouble(number.toString());
		}
		catch (NumberFormatException ex)
		{
			return null;
		}
	}

	private static Candidate candidate(CombatStyleRecommendation style, String defence)
	{
		Double value = numericValue(defence);
		return value == null ? null : new Candidate(style, value);
	}

	private static String lowest(String... values)
	{
		Double lowest = null;
		for (String value : values)
		{
			Double numeric = numericValue(value);
			if (numeric != null && (lowest == null || numeric < lowest))
			{
				lowest = numeric;
			}
		}
		return lowest == null ? null : Double.toString(lowest);
	}

	private static double offensiveScore(String accuracy, String damage, String prayer)
	{
		return value(accuracy) + value(damage) * 1.5d + value(prayer) * 0.1d;
	}

	private static double value(String value)
	{
		Double numeric = numericValue(value);
		return numeric == null ? 0 : numeric;
	}

	private static final class Candidate
	{
		private final CombatStyleRecommendation style;
		private final double defence;

		private Candidate(CombatStyleRecommendation style, double defence)
		{
			this.style = style;
			this.defence = defence;
		}
	}
}

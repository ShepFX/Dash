package com.dash;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.runelite.api.WorldType;

/** Text the tiles show. Pure functions, so they are tested. */
final class DashFormat
{
	/** The poison varp counts up into venom at this value; below zero means immune. */
	static final int VENOM_THRESHOLD = 1_000_000;
	static final long SIX_HOURS_MS = 6L * 60 * 60 * 1000;

	private DashFormat()
	{
	}

	enum Poison
	{
		NONE, POISONED, VENOMED, IMMUNE
	}

	static Poison poison(int varp)
	{
		if (varp >= VENOM_THRESHOLD) return Poison.VENOMED;
		if (varp > 0) return Poison.POISONED;
		if (varp < 0) return Poison.IMMUNE;
		return Poison.NONE;
	}

	/** "1h 05m", "12m 03s", "45s". Never negative. */
	static String duration(long millis)
	{
		long seconds = Math.max(0, millis / 1000);
		long hours = seconds / 3600;
		long minutes = (seconds % 3600) / 60;
		long rest = seconds % 60;
		if (hours > 0) return hours + "h " + pad(minutes) + "m";
		if (minutes > 0) return minutes + "m " + pad(rest) + "s";
		return rest + "s";
	}

	/** "4:12:07" or "12:07", for a countdown that is watched. */
	static String clock(long millis)
	{
		long seconds = Math.max(0, millis / 1000);
		long hours = seconds / 3600;
		long minutes = (seconds % 3600) / 60;
		long rest = seconds % 60;
		if (hours > 0) return hours + ":" + pad(minutes) + ":" + pad(rest);
		return minutes + ":" + pad(rest);
	}

	private static String pad(long value)
	{
		return value < 10 ? "0" + value : String.valueOf(value);
	}

	/** "999", "12.3K", "1.20M", "2.5B": the game's own shorthand. */
	static String shortNumber(long value)
	{
		long magnitude = Math.abs(value);
		String sign = value < 0 ? "-" : "";
		if (magnitude < 10_000) return sign + magnitude;
		if (magnitude < 1_000_000) return sign + oneDecimal(magnitude / 1_000d) + "K";
		if (magnitude < 1_000_000_000L) return sign + twoDecimals(magnitude / 1_000_000d) + "M";
		return sign + twoDecimals(magnitude / 1_000_000_000d) + "B";
	}

	private static String oneDecimal(double value)
	{
		String text = String.format("%.1f", value);
		return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
	}

	private static String twoDecimals(double value)
	{
		String text = String.format("%.2f", value);
		if (text.endsWith("00")) return text.substring(0, text.length() - 3);
		if (text.endsWith("0")) return text.substring(0, text.length() - 1);
		return text;
	}

	/** "1,234,567" */
	static String commas(long value)
	{
		return String.format("%,d", value);
	}

	/** Experience per hour for {@code gained} over {@code millis}; zero until a minute has passed. */
	static long perHour(long gained, long millis)
	{
		if (millis < 60_000 || gained <= 0) return 0;
		return Math.round(gained * 3_600_000d / millis);
	}

	/** Short labels for the kinds of world that matter when you are about to do something there. */
	static List<String> worldTags(EnumSet<WorldType> types)
	{
		List<String> tags = new ArrayList<>();
		if (types == null) return tags;
		tags.add(types.contains(WorldType.MEMBERS) ? "Members" : "Free");
		if (types.contains(WorldType.PVP)) tags.add("PvP");
		if (types.contains(WorldType.HIGH_RISK)) tags.add("High risk");
		if (types.contains(WorldType.BOUNTY)) tags.add("Bounty");
		if (types.contains(WorldType.DEADMAN)) tags.add("Deadman");
		if (types.contains(WorldType.SEASONAL)) tags.add("Seasonal");
		if (types.contains(WorldType.FRESH_START_WORLD)) tags.add("Fresh start");
		if (types.contains(WorldType.SKILL_TOTAL)) tags.add("Total level");
		if (types.contains(WorldType.QUEST_SPEEDRUNNING)) tags.add("Speedrun");
		if (types.contains(WorldType.LAST_MAN_STANDING)) tags.add("LMS");
		if (types.contains(WorldType.PVP_ARENA)) tags.add("PvP Arena");
		if (types.contains(WorldType.TOURNAMENT_WORLD)) tags.add("Tournament");
		if (types.contains(WorldType.BETA_WORLD)) tags.add("Beta");
		return tags;
	}

	/** A world type that a player could be hurt by hopping into without noticing. */
	static boolean dangerous(EnumSet<WorldType> types)
	{
		return types != null && (types.contains(WorldType.PVP) || types.contains(WorldType.HIGH_RISK)
			|| types.contains(WorldType.DEADMAN) || types.contains(WorldType.BOUNTY));
	}

	/** Strips the light HTML RuneLite tooltips carry: line breaks become " · ", tags go. */
	static String plainText(String text)
	{
		if (text == null) return "";
		return text.replaceAll("(?i)<br\\s*/?>", " · ").replaceAll("(?i)</?br>", " · ")
			.replaceAll("<[^>]*>", "").replace("&nbsp;", " ").trim();
	}
}

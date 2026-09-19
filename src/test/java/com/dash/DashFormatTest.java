package com.dash;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.EnumSet;
import net.runelite.api.WorldType;
import org.junit.Test;

public class DashFormatTest
{
	@Test
	public void poisonComesFromTheVarp()
	{
		assertEquals(DashFormat.Poison.NONE, DashFormat.poison(0));
		assertEquals(DashFormat.Poison.POISONED, DashFormat.poison(1));
		assertEquals(DashFormat.Poison.POISONED, DashFormat.poison(999_999));
		assertEquals(DashFormat.Poison.VENOMED, DashFormat.poison(1_000_000));
		assertEquals(DashFormat.Poison.VENOMED, DashFormat.poison(1_000_040));
		assertEquals(DashFormat.Poison.IMMUNE, DashFormat.poison(-30));
	}

	@Test
	public void durationsReadNaturally()
	{
		assertEquals("0s", DashFormat.duration(0));
		assertEquals("0s", DashFormat.duration(-5000));
		assertEquals("45s", DashFormat.duration(45_000));
		assertEquals("12m 03s", DashFormat.duration(723_000));
		assertEquals("1h 05m", DashFormat.duration(3_900_000));
		assertEquals("4:12:07", DashFormat.clock(4 * 3_600_000 + 12 * 60_000 + 7_000));
		assertEquals("0:07", DashFormat.clock(7_000));
		assertEquals("0:00", DashFormat.clock(-1));
	}

	@Test
	public void numbersUseTheGamesShorthand()
	{
		assertEquals("0", DashFormat.shortNumber(0));
		assertEquals("9999", DashFormat.shortNumber(9_999));
		assertEquals("10K", DashFormat.shortNumber(10_000));
		assertEquals("12.3K", DashFormat.shortNumber(12_345));
		assertEquals("999.9K", DashFormat.shortNumber(999_949));
		assertEquals("1M", DashFormat.shortNumber(1_000_000));
		assertEquals("1.2M", DashFormat.shortNumber(1_200_000));
		assertEquals("1.23M", DashFormat.shortNumber(1_234_567));
		assertEquals("2.1B", DashFormat.shortNumber(2_100_000_000L));
		assertEquals("-12.3K", DashFormat.shortNumber(-12_345));
		assertEquals("1,234,567", DashFormat.commas(1_234_567));
	}

	@Test
	public void ratesWaitForAMinute()
	{
		assertEquals(0, DashFormat.perHour(1000, 30_000));
		assertEquals(0, DashFormat.perHour(0, 3_600_000));
		assertEquals(60_000, DashFormat.perHour(1000, 60_000));
		assertEquals(1000, DashFormat.perHour(1000, 3_600_000));
	}

	@Test
	public void worldTagsNameWhatMatters()
	{
		assertEquals(Arrays.asList("Free"), DashFormat.worldTags(EnumSet.noneOf(WorldType.class)));
		assertEquals(Arrays.asList("Members"), DashFormat.worldTags(EnumSet.of(WorldType.MEMBERS)));
		assertEquals(Arrays.asList("Members", "PvP", "High risk"),
			DashFormat.worldTags(EnumSet.of(WorldType.MEMBERS, WorldType.PVP, WorldType.HIGH_RISK)));
		assertEquals(Arrays.asList("Free", "Total level"), DashFormat.worldTags(EnumSet.of(WorldType.SKILL_TOTAL)));
		assertTrue(DashFormat.worldTags(null).isEmpty());
		assertTrue(DashFormat.dangerous(EnumSet.of(WorldType.MEMBERS, WorldType.PVP)));
		assertTrue(DashFormat.dangerous(EnumSet.of(WorldType.DEADMAN)));
		assertFalse(DashFormat.dangerous(EnumSet.of(WorldType.MEMBERS, WorldType.SKILL_TOTAL)));
		assertFalse(DashFormat.dangerous(null));
	}

	@Test
	public void tooltipsLoseTheirMarkup()
	{
		assertEquals("Antifire · 2:30", DashFormat.plainText("Antifire</br>2:30"));
		assertEquals("Stamina · 1:00", DashFormat.plainText("Stamina<br>1:00"));
		assertEquals("Prayer 43", DashFormat.plainText("<col=ff0000>Prayer</col> 43"));
		assertEquals("", DashFormat.plainText(null));
		assertEquals("", DashFormat.plainText("  "));
	}
}

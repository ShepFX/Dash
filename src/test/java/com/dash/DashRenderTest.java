package com.dash;

import static org.junit.Assert.assertTrue;
import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import javax.imageio.ImageIO;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Assume;
import org.junit.Test;

/**
 * Paints the window offscreen with made-up data and writes it to build/dash-preview.png, so the
 * layout can be looked at without a game client. Also checks a few things no screen is needed for.
 */
public class DashRenderTest
{
	private static final DashConfig DEFAULTS = new DashConfig()
	{
	};

	private static DashSnapshot sample(long now)
	{
		BufferedImage icon = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = icon.createGraphics();
		g.setColor(new Color(200, 160, 60));
		g.fillOval(4, 4, 24, 24);
		g.dispose();
		return DashSnapshot.builder()
			.takenAt(now).loggedIn(true)
			.hitpoints(23).maxHitpoints(99).prayer(41).maxPrayer(70).runEnergy(78).special(100)
			.poison(DashFormat.Poison.POISONED)
			.world(302).worldTags(Arrays.asList("Members", "PvP")).dangerousWorld(true)
			.playerName("Zezima").combatLevel(126).skulled(true)
			.lastActiveAt(now - 95_000).attackedAt(0)
			.offers(Arrays.asList(
				new DashSnapshot.Offer(0, "Rune scimitar", icon, true, 3, 5, 15_000, GrandExchangeOfferState.BUYING),
				new DashSnapshot.Offer(1, "Lobster", icon, false, 1000, 1000, 150, GrandExchangeOfferState.SOLD),
				new DashSnapshot.Offer(2, "Very long item name that will not fit in the row", icon, true, 0, 1, 1, GrandExchangeOfferState.CANCELLED_BUY)))
			.alerts(Arrays.asList(
				new DashSnapshot.Alert(now - 20_000, "Offer complete"),
				new DashSnapshot.Alert(now - 400_000, "PM from Friend: are you coming to the star on 308 or not, it is a tier 7"),
				new DashSnapshot.Alert(now - 3_700_000, "You are now idle!")))
			.boxes(Arrays.asList(
				new DashSnapshot.Box("Antifire", "2:30", Color.WHITE, icon),
				new DashSnapshot.Box("Stamina", "0:41", Color.RED, icon),
				new DashSnapshot.Box("Cannon", "24", null, icon)))
			.sessionStartedAt(now - 5_400_000).xpGained(123_456)
			.topGains(Arrays.asList(
				new DashSnapshot.SkillGain("Fishing", 90_000, 60_000),
				new DashSnapshot.SkillGain("Cooking", 33_456, 22_304)))
			.freeSlots(0).coins(1_234_567)
			.loginAt(now - 5_400_000)
			.build();
	}

	@Test
	public void rendersEveryTileWithSampleData() throws Exception
	{
		// A JFrame needs a display; a build server has none, and the arithmetic is covered elsewhere.
		Assume.assumeFalse(GraphicsEnvironment.isHeadless());
		DashWindow window = new DashWindow(DEFAULTS, () -> {}, () -> {}, notches -> {}, point -> {}, () -> {});
		try
		{
			window.show(sample(System.currentTimeMillis()));
			BufferedImage image = window.render(300);
			assertTrue("tall enough to hold every tile", image.getHeight() > 400);
			File out = new File("build", "dash-preview.png");
			ImageIO.write(image, "png", out);

			window.show(DashSnapshot.builder().loggedIn(false).alerts(Collections.emptyList()).build());
			BufferedImage loggedOut = window.render(300);
			assertTrue("logged out is shorter", loggedOut.getHeight() < image.getHeight());
			ImageIO.write(loggedOut, "png", new File("build", "dash-preview-logged-out.png"));
		}
		finally
		{
			window.dispose();
		}
	}
}

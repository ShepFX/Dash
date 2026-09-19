package com.dash;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.GrandExchangeOfferState;

/**
 * The tiles, and the little drawing language they share. Each tile says how tall it wants to be
 * for a given width, then paints itself at a y offset. Everything is drawn by hand so the window
 * looks the same on every platform and never takes focus.
 */
final class DashTiles
{
	static final Color BACKGROUND = new Color(20, 20, 20);
	static final Color PANEL = new Color(31, 31, 31);
	static final Color LINE = new Color(52, 52, 52);
	static final Color TEXT = new Color(232, 230, 227);
	static final Color MUTED = new Color(141, 138, 134);
	static final Color HITPOINTS = new Color(255, 70, 70);
	static final Color HITPOINTS_FULL = new Color(90, 200, 120);
	static final Color PRAYER = new Color(80, 155, 255);
	static final Color RUN = new Color(255, 190, 45);
	static final Color SPECIAL = new Color(255, 122, 47);
	static final Color WARN = new Color(255, 90, 90);
	static final Color GOOD = new Color(90, 200, 120);
	static final Color TRACK = new Color(44, 44, 44);
	/** Something that attacked recently is still a fight for this long. */
	static final long ATTACK_MEMORY_MS = 6000;

	private DashTiles()
	{
	}

	/** What every tile paints with. Built once per paint, or once per measure with no graphics. */
	static final class Layout
	{
		final Graphics2D g;
		final int width;
		final int pad;
		final Font font;
		final Font bold;
		final Font small;
		final FontMetrics fm;
		final FontMetrics boldFm;
		final FontMetrics smallFm;
		final int row;
		final DashSnapshot snapshot;
		final DashConfig config;
		final long now;

		Layout(Graphics2D g, int width, Font font, Font bold, Font small, FontMetrics fm, FontMetrics boldFm, FontMetrics smallFm,
			DashSnapshot snapshot, DashConfig config, long now)
		{
			this.g = g;
			this.width = width;
			this.pad = Math.max(6, Math.round(fm.getHeight() * 0.45f));
			this.font = font;
			this.bold = bold;
			this.small = small;
			this.fm = fm;
			this.boldFm = boldFm;
			this.smallFm = smallFm;
			this.row = fm.getHeight() + 4;
			this.snapshot = snapshot;
			this.config = config;
			this.now = now;
		}

		int inner()
		{
			return width - 2 * pad;
		}

		void text(String text, int x, int baseline, Color color, Font with)
		{
			g.setFont(with);
			g.setColor(color);
			g.drawString(text, x, baseline);
		}

		void textRight(String text, int right, int baseline, Color color, Font with)
		{
			g.setFont(with);
			g.setColor(color);
			g.drawString(text, right - g.getFontMetrics().stringWidth(text), baseline);
		}

		/** Cuts text to fit, with an ellipsis. */
		String fit(String text, int maxWidth, FontMetrics with)
		{
			if (text == null) return "";
			if (with.stringWidth(text) <= maxWidth) return text;
			String ellipsis = "…";
			int room = maxWidth - with.stringWidth(ellipsis);
			int end = text.length();
			while (end > 0 && with.stringWidth(text.substring(0, end)) > room) end--;
			return text.substring(0, end).trim() + ellipsis;
		}

		void bar(int x, int y, int width, int height, double fraction, Color fill)
		{
			g.setColor(TRACK);
			g.fillRoundRect(x, y, width, height, height, height);
			int filled = (int) Math.round(width * Math.max(0, Math.min(1, fraction)));
			if (filled > 0)
			{
				g.setColor(fill);
				g.fillRoundRect(x, y, Math.max(filled, height), height, height, height);
			}
		}

		void icon(Image image, int x, int y, int size)
		{
			if (image == null) return;
			int w = image.getWidth(null);
			int h = image.getHeight(null);
			if (w <= 0 || h <= 0) return;
			float scale = Math.min(size / (float) w, size / (float) h);
			int dw = Math.max(1, Math.round(w * scale));
			int dh = Math.max(1, Math.round(h * scale));
			g.drawImage(image, x + (size - dw) / 2, y + (size - dh) / 2, dw, dh, null);
		}
	}

	abstract static class Tile
	{
		abstract String title();

		abstract boolean enabled(DashConfig config);

		/** Height in pixels for this layout. */
		abstract int height(Layout l);

		/** Paints the tile with its top edge at {@code y}. */
		abstract void paint(Layout l, int y);

		/** A tile with a title line and rows under it. */
		int titled(Layout l, int rows)
		{
			return l.pad + l.smallFm.getHeight() + 2 + rows * l.row + l.pad;
		}

		/** Paints the title and returns the y of the first row's top. */
		int paintTitle(Layout l, int y)
		{
			int baseline = y + l.pad + l.smallFm.getAscent();
			l.text(title().toUpperCase(), l.pad, baseline, MUTED, l.small);
			return y + l.pad + l.smallFm.getHeight() + 2;
		}

		/** A one-line tile: label left, value right, no title. */
		int oneLine(Layout l)
		{
			return l.pad + l.row + l.pad / 2;
		}

		void paintOneLine(Layout l, int y, String label, String value, Color valueColor)
		{
			int baseline = y + l.pad / 2 + l.fm.getAscent() + 2;
			l.text(label, l.pad, baseline, MUTED, l.font);
			l.textRight(value, l.width - l.pad, baseline, valueColor, l.bold);
		}

		void paintEmpty(Layout l, int y, String text)
		{
			l.text(text, l.pad, y + l.fm.getAscent() + 2, MUTED, l.font);
		}
	}

	static List<Tile> all()
	{
		List<Tile> tiles = new ArrayList<>();
		tiles.add(new Vitals());
		tiles.add(new Status());
		tiles.add(new Offers());
		tiles.add(new Alerts());
		tiles.add(new Timers());
		tiles.add(new Session());
		tiles.add(new Inventory());
		tiles.add(new Logout());
		return tiles;
	}

	static final class Vitals extends Tile
	{
		@Override
		String title()
		{
			return "Vitals";
		}

		@Override
		boolean enabled(DashConfig config)
		{
			return config.showVitals();
		}

		@Override
		int height(Layout l)
		{
			int rows = l.snapshot.getPoison() == DashFormat.Poison.NONE ? 4 : 5;
			return titled(l, rows);
		}

		@Override
		void paint(Layout l, int y)
		{
			DashSnapshot s = l.snapshot;
			int top = paintTitle(l, y);
			int label = l.fm.stringWidth("Spec") + l.pad;
			int barX = l.pad + label;
			int barW = l.inner() - label;
			int barH = l.row - 6;
			Color hp = s.getMaxHitpoints() > 0 && s.getHitpoints() * 4 <= s.getMaxHitpoints() ? HITPOINTS : HITPOINTS_FULL;
			row(l, top, "HP", barX, barW, barH, s.getHitpoints(), s.getMaxHitpoints(), s.getHitpoints() + " / " + s.getMaxHitpoints(), hp);
			row(l, top + l.row, "Pray", barX, barW, barH, s.getPrayer(), s.getMaxPrayer(), s.getPrayer() + " / " + s.getMaxPrayer(), PRAYER);
			row(l, top + 2 * l.row, "Run", barX, barW, barH, s.getRunEnergy(), 100, s.getRunEnergy() + "%", RUN);
			row(l, top + 3 * l.row, "Spec", barX, barW, barH, s.getSpecial(), 100, s.getSpecial() + "%", SPECIAL);
			if (s.getPoison() != DashFormat.Poison.NONE)
			{
				String text = s.getPoison() == DashFormat.Poison.VENOMED ? "Venomed" : s.getPoison() == DashFormat.Poison.POISONED ? "Poisoned" : "Immune to poison";
				Color color = s.getPoison() == DashFormat.Poison.IMMUNE ? MUTED : WARN;
				l.text(text, l.pad, top + 4 * l.row + l.fm.getAscent() + 2, color, l.bold);
			}
		}

		private void row(Layout l, int y, String label, int barX, int barW, int barH, int value, int max, String text, Color color)
		{
			int baseline = y + l.fm.getAscent() + 2;
			l.text(label, l.pad, baseline, MUTED, l.font);
			l.bar(barX, y + 3, barW, barH, max > 0 ? value / (double) max : 0, color);
			l.g.setFont(l.small);
			int width = l.g.getFontMetrics().stringWidth(text);
			l.g.setColor(Color.BLACK);
			l.g.drawString(text, barX + (barW - width) / 2 + 1, y + 3 + (barH + l.smallFm.getAscent()) / 2);
			l.g.setColor(Color.WHITE);
			l.g.drawString(text, barX + (barW - width) / 2, y + 3 + (barH + l.smallFm.getAscent()) / 2 - 1);
		}
	}

	static final class Status extends Tile
	{
		@Override
		String title()
		{
			return "Status";
		}

		@Override
		boolean enabled(DashConfig config)
		{
			return config.showStatus();
		}

		@Override
		int height(Layout l)
		{
			return titled(l, l.snapshot.isLoggedIn() ? 3 : 1);
		}

		@Override
		void paint(Layout l, int y)
		{
			DashSnapshot s = l.snapshot;
			int top = paintTitle(l, y);
			if (!s.isLoggedIn())
			{
				paintEmpty(l, top, "Not logged in");
				return;
			}
			int baseline = top + l.fm.getAscent() + 2;
			String world = "World " + s.getWorld();
			l.text(world, l.pad, baseline, TEXT, l.bold);
			String tags = String.join(" · ", s.getWorldTags());
			int x = l.pad + l.boldFm.stringWidth(world) + l.pad;
			l.text(l.fit(tags, l.width - l.pad - x, l.fm), x, baseline, s.isDangerousWorld() ? WARN : MUTED, l.font);

			baseline += l.row;
			String who = s.getPlayerName() == null ? "" : s.getPlayerName();
			if (s.getCombatLevel() > 0) who += "  (" + s.getCombatLevel() + ")";
			l.text(l.fit(who, l.inner(), l.fm), l.pad, baseline, TEXT, l.font);
			if (s.isSkulled()) l.textRight("Skulled", l.width - l.pad, baseline, WARN, l.bold);

			baseline += l.row;
			String activity;
			Color color = MUTED;
			if (s.getAttackedAt() > 0 && l.now - s.getAttackedAt() < ATTACK_MEMORY_MS)
			{
				activity = "Under attack";
				color = WARN;
			}
			else if (s.getFighting() != null)
			{
				activity = "Fighting " + s.getFighting();
				color = TEXT;
			}
			else if (s.getLastActiveAt() > 0 && l.now - s.getLastActiveAt() >= 5000)
			{
				activity = "Idle " + DashFormat.duration(l.now - s.getLastActiveAt());
			}
			else
			{
				activity = "Active";
				color = GOOD;
			}
			l.text(activity, l.pad, baseline, color, l.font);
		}
	}

	static final class Offers extends Tile
	{
		@Override
		String title()
		{
			return "Grand Exchange";
		}

		@Override
		boolean enabled(DashConfig config)
		{
			return config.showOffers();
		}

		@Override
		int height(Layout l)
		{
			int count = l.snapshot.getOffers().size();
			return titled(l, count == 0 ? 1 : count * 2);
		}

		@Override
		void paint(Layout l, int y)
		{
			int top = paintTitle(l, y);
			List<DashSnapshot.Offer> offers = l.snapshot.getOffers();
			if (offers.isEmpty())
			{
				paintEmpty(l, top, l.snapshot.isLoggedIn() ? "No offers" : "Not logged in");
				return;
			}
			int icon = l.row + 2;
			for (DashSnapshot.Offer offer : offers)
			{
				int baseline = top + l.fm.getAscent() + 2;
				l.icon(offer.getIcon(), l.pad, top + 1, icon);
				int x = l.pad + icon + 6;
				String state;
				Color stateColor;
				if (offer.finished())
				{
					state = offer.isBuying() ? "Bought" : "Sold";
					stateColor = GOOD;
				}
				else if (offer.cancelled())
				{
					state = "Cancelled";
					stateColor = WARN;
				}
				else
				{
					state = offer.isBuying() ? "Buying" : "Selling";
					stateColor = MUTED;
				}
				int stateWidth = l.fm.stringWidth(state);
				l.text(l.fit(offer.getItemName(), l.width - l.pad - x - stateWidth - 8, l.boldFm), x, baseline, TEXT, l.bold);
				l.textRight(state, l.width - l.pad, baseline, stateColor, l.font);

				int barY = top + l.row + 4;
				int barH = l.row - 10;
				int total = Math.max(1, offer.getTotal());
				Color fill = offer.finished() ? GOOD : offer.cancelled() ? MUTED : offer.isBuying() ? PRAYER : RUN;
				l.bar(x, barY, l.inner() - icon - 6, barH, offer.getDone() / (double) total, fill);
				String progress = DashFormat.commas(offer.getDone()) + " / " + DashFormat.commas(offer.getTotal())
					+ "  @ " + DashFormat.commas(offer.getPrice());
				l.g.setFont(l.small);
				l.g.setColor(Color.WHITE);
				l.g.drawString(l.fit(progress, l.inner() - icon - 12, l.smallFm), x + 4, barY + (barH + l.smallFm.getAscent()) / 2 - 1);
				top += 2 * l.row;
			}
		}
	}

	static final class Alerts extends Tile
	{
		@Override
		String title()
		{
			return "Alerts";
		}

		@Override
		boolean enabled(DashConfig config)
		{
			return config.showAlerts();
		}

		@Override
		int height(Layout l)
		{
			return titled(l, Math.max(1, Math.min(l.config.alertCount(), l.snapshot.getAlerts().size())));
		}

		@Override
		void paint(Layout l, int y)
		{
			int top = paintTitle(l, y);
			List<DashSnapshot.Alert> alerts = l.snapshot.getAlerts();
			if (alerts.isEmpty())
			{
				paintEmpty(l, top, "Nothing yet");
				return;
			}
			int shown = Math.min(l.config.alertCount(), alerts.size());
			for (int i = 0; i < shown; i++)
			{
				DashSnapshot.Alert alert = alerts.get(i);
				int baseline = top + i * l.row + l.fm.getAscent() + 2;
				String age = DashFormat.duration(l.now - alert.getAt()) + " ago";
				int ageWidth = l.smallFm.stringWidth(age);
				l.textRight(age, l.width - l.pad, baseline, MUTED, l.small);
				Color color = i == 0 && l.now - alert.getAt() < 60_000 ? TEXT : MUTED;
				l.text(l.fit(alert.getText(), l.inner() - ageWidth - 8, l.fm), l.pad, baseline, color, l.font);
			}
		}
	}

	static final class Timers extends Tile
	{
		@Override
		String title()
		{
			return "Timers and boosts";
		}

		@Override
		boolean enabled(DashConfig config)
		{
			return config.showTimers();
		}

		@Override
		int height(Layout l)
		{
			return titled(l, Math.max(1, l.snapshot.getBoxes().size()));
		}

		@Override
		void paint(Layout l, int y)
		{
			int top = paintTitle(l, y);
			List<DashSnapshot.Box> boxes = l.snapshot.getBoxes();
			if (boxes.isEmpty())
			{
				paintEmpty(l, top, "None");
				return;
			}
			int icon = l.row - 2;
			for (DashSnapshot.Box box : boxes)
			{
				int baseline = top + l.fm.getAscent() + 2;
				l.icon(box.getImage(), l.pad, top + 1, icon);
				String value = box.getText() == null ? "" : box.getText();
				int valueWidth = l.boldFm.stringWidth(value);
				l.textRight(value, l.width - l.pad, baseline, box.getTextColor() == null ? TEXT : box.getTextColor(), l.bold);
				int x = l.pad + icon + 6;
				l.text(l.fit(box.getName(), l.width - l.pad - x - valueWidth - 8, l.fm), x, baseline, TEXT, l.font);
				top += l.row;
			}
		}
	}

	static final class Session extends Tile
	{
		@Override
		String title()
		{
			return "Session";
		}

		@Override
		boolean enabled(DashConfig config)
		{
			return config.showSession();
		}

		@Override
		int height(Layout l)
		{
			if (l.snapshot.getSessionStartedAt() == 0) return titled(l, 1);
			return titled(l, 1 + l.snapshot.getTopGains().size());
		}

		@Override
		void paint(Layout l, int y)
		{
			DashSnapshot s = l.snapshot;
			int top = paintTitle(l, y);
			if (s.getSessionStartedAt() == 0)
			{
				paintEmpty(l, top, "Not logged in");
				return;
			}
			int baseline = top + l.fm.getAscent() + 2;
			l.text("Logged in " + DashFormat.duration(l.now - s.getSessionStartedAt()), l.pad, baseline, TEXT, l.font);
			l.textRight(DashFormat.shortNumber(s.getXpGained()) + " xp", l.width - l.pad, baseline, s.getXpGained() > 0 ? GOOD : MUTED, l.bold);
			for (DashSnapshot.SkillGain gain : s.getTopGains())
			{
				baseline += l.row;
				l.text(gain.getSkill(), l.pad, baseline, MUTED, l.font);
				String rate = gain.getPerHour() > 0 ? DashFormat.shortNumber(gain.getPerHour()) + "/h" : "";
				l.textRight(rate, l.width - l.pad, baseline, MUTED, l.small);
				int rateWidth = l.smallFm.stringWidth(rate);
				l.textRight(DashFormat.shortNumber(gain.getGained()), l.width - l.pad - rateWidth - (rate.isEmpty() ? 0 : 10), baseline, TEXT, l.bold);
			}
		}
	}

	static final class Inventory extends Tile
	{
		@Override
		String title()
		{
			return "Inventory";
		}

		@Override
		boolean enabled(DashConfig config)
		{
			return config.showInventory();
		}

		@Override
		int height(Layout l)
		{
			return oneLine(l);
		}

		@Override
		void paint(Layout l, int y)
		{
			DashSnapshot s = l.snapshot;
			if (!s.isLoggedIn())
			{
				paintOneLine(l, y, "Inventory", "—", MUTED);
				return;
			}
			String slots = s.getFreeSlots() == 0 ? "Full" : s.getFreeSlots() + " free";
			String coins = DashFormat.shortNumber(s.getCoins()) + " gp";
			paintOneLine(l, y, "Inventory  " + slots, coins, s.getFreeSlots() == 0 ? WARN : TEXT);
		}
	}

	static final class Logout extends Tile
	{
		@Override
		String title()
		{
			return "Six hour logout";
		}

		@Override
		boolean enabled(DashConfig config)
		{
			return config.showLogout();
		}

		@Override
		int height(Layout l)
		{
			return oneLine(l);
		}

		@Override
		void paint(Layout l, int y)
		{
			DashSnapshot s = l.snapshot;
			if (s.getLoginAt() == 0)
			{
				paintOneLine(l, y, "Six hour logout", "—", MUTED);
				return;
			}
			long left = s.getLoginAt() + DashFormat.SIX_HOURS_MS - l.now;
			Color color = left < 15 * 60_000 ? WARN : left < 60 * 60_000 ? RUN : TEXT;
			paintOneLine(l, y, "Six hour logout", DashFormat.clock(left), color);
		}
	}

	/** A graphics to measure with when the window has not been shown yet. */
	static Graphics2D measuringGraphics()
	{
		Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		return g;
	}
}

package com.dash;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Comparator;

/** Where the Dash window goes. Pure arithmetic, so it is tested. */
final class DashGeometry
{
	static final int MIN_WIDTH = 200;
	static final int MAX_WIDTH = 800;

	private DashGeometry()
	{
	}

	static int clampWidth(int width)
	{
		return Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, width));
	}

	/** The top-left point that puts a window of {@code size} in the given corner of the work area. */
	static Point place(Corner corner, int margin, Rectangle workArea, Dimension size)
	{
		int x = corner.isLeft() ? workArea.x + margin : workArea.x + workArea.width - size.width - margin;
		int y = corner.isTop() ? workArea.y + margin : workArea.y + workArea.height - size.height - margin;
		return clamp(new Point(x, y), size, workArea);
	}

	/**
	 * Like {@link #place}, but prefers a corner where the window would not sit on top of
	 * {@code avoid} - the RuneLite window, when it is on the same screen. The preferred corner wins
	 * if it is clear; otherwise the first clear corner going round; otherwise the preferred corner
	 * anyway.
	 */
	static Point place(Corner preferred, int margin, Rectangle workArea, Dimension size, Rectangle avoid)
	{
		Point first = place(preferred, margin, workArea, size);
		if (avoid == null || !new Rectangle(first, size).intersects(avoid)) return first;
		// Slide along the same edge before jumping to the other one.
		Corner[] fallbacks = Arrays.copyOf(Corner.values(), Corner.values().length);
		Arrays.sort(fallbacks, Comparator.comparingInt((Corner corner) -> corner.isTop() == preferred.isTop() ? 0 : 1)
			.thenComparingInt(corner -> corner.isLeft() == preferred.isLeft() ? 0 : 1));
		for (Corner corner : fallbacks)
		{
			if (corner == preferred) continue;
			Point candidate = place(corner, margin, workArea, size);
			if (!new Rectangle(candidate, size).intersects(avoid)) return candidate;
		}
		return first;
	}

	/** Keeps a window on screen: fully visible if it fits, pinned to the top-left if it does not. */
	static Point clamp(Point point, Dimension size, Rectangle workArea)
	{
		int maxX = workArea.x + workArea.width - size.width;
		int maxY = workArea.y + workArea.height - size.height;
		int x = Math.max(workArea.x, Math.min(point.x, maxX));
		int y = Math.max(workArea.y, Math.min(point.y, maxY));
		return new Point(x, y);
	}

	/**
	 * Where a window that was at {@code location} with {@code oldSize} should go now that it is
	 * {@code newSize}: a window in a bottom corner grows upwards, one in a right corner grows leftwards,
	 * so the edge nearest the screen edge stays put.
	 */
	static Point regrow(Point location, Dimension oldSize, Dimension newSize, Rectangle workArea)
	{
		int centreX = location.x + oldSize.width / 2;
		int centreY = location.y + oldSize.height / 2;
		boolean right = centreX > workArea.x + workArea.width / 2;
		boolean bottom = centreY > workArea.y + workArea.height / 2;
		int x = right ? location.x + oldSize.width - newSize.width : location.x;
		int y = bottom ? location.y + oldSize.height - newSize.height : location.y;
		return clamp(new Point(x, y), newSize, workArea);
	}

	/** Reads an "x,y" position saved by {@link #formatPosition}; null for anything else. */
	static Point parsePosition(String text)
	{
		if (text == null) return null;
		String[] parts = text.trim().split(",");
		if (parts.length != 2) return null;
		try
		{
			return new Point(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
		}
		catch (NumberFormatException exception)
		{
			return null;
		}
	}

	static String formatPosition(Point point)
	{
		return point.x + "," + point.y;
	}

	/** A new width after a scroll of {@code notches}: ten percent per notch, within the limits. */
	static int scrolledWidth(int width, int notches)
	{
		double factor = Math.pow(1.1, -notches);
		return clampWidth((int) Math.round(width * factor));
	}
}

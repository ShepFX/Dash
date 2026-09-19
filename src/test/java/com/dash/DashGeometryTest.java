package com.dash;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import org.junit.Test;

public class DashGeometryTest
{
	private static final Rectangle SCREEN = new Rectangle(0, 0, 1920, 1040);
	private static final Dimension SIZE = new Dimension(280, 400);

	@Test
	public void placesInEachCorner()
	{
		assertEquals(new Point(16, 16), DashGeometry.place(Corner.TOP_LEFT, 16, SCREEN, SIZE));
		assertEquals(new Point(1624, 16), DashGeometry.place(Corner.TOP_RIGHT, 16, SCREEN, SIZE));
		assertEquals(new Point(16, 624), DashGeometry.place(Corner.BOTTOM_LEFT, 16, SCREEN, SIZE));
		assertEquals(new Point(1624, 624), DashGeometry.place(Corner.BOTTOM_RIGHT, 16, SCREEN, SIZE));
	}

	@Test
	public void placementRespectsTheScreenOffset()
	{
		Rectangle second = new Rectangle(1920, 0, 1920, 1040);
		assertEquals(new Point(3544, 16), DashGeometry.place(Corner.TOP_RIGHT, 16, second, SIZE));
	}

	@Test
	public void avoidsTheClientWhenAnotherCornerIsClear()
	{
		// RuneLite fills the right half of the screen: the top-right corner is covered.
		Rectangle client = new Rectangle(960, 0, 960, 1040);
		assertEquals(new Point(16, 16), DashGeometry.place(Corner.TOP_RIGHT, 16, SCREEN, SIZE, client));
		// It fills the whole screen: nowhere is clear, so the preferred corner it is.
		assertEquals(new Point(1624, 16), DashGeometry.place(Corner.TOP_RIGHT, 16, SCREEN, SIZE, SCREEN));
		// Nothing to avoid.
		assertEquals(new Point(1624, 16), DashGeometry.place(Corner.TOP_RIGHT, 16, SCREEN, SIZE, null));
	}

	@Test
	public void slidesAlongTheSameEdgeFirst()
	{
		// The top-right is covered but the bottom-right is clear; still, the same edge (top-left) wins.
		Rectangle client = new Rectangle(960, 0, 960, 500);
		assertEquals(new Point(16, 16), DashGeometry.place(Corner.TOP_RIGHT, 16, SCREEN, SIZE, client));
		// Now the whole top is covered, so it drops to the bottom on the same side.
		Rectangle top = new Rectangle(0, 0, 1920, 500);
		assertEquals(new Point(1624, 624), DashGeometry.place(Corner.TOP_RIGHT, 16, SCREEN, SIZE, top));
	}

	@Test
	public void clampsToTheWorkArea()
	{
		assertEquals(new Point(0, 0), DashGeometry.clamp(new Point(-50, -50), SIZE, SCREEN));
		assertEquals(new Point(1640, 640), DashGeometry.clamp(new Point(5000, 5000), SIZE, SCREEN));
		assertEquals(new Point(100, 100), DashGeometry.clamp(new Point(100, 100), SIZE, SCREEN));
		// A window taller than the screen sits at the top rather than off it.
		assertEquals(new Point(0, 0), DashGeometry.clamp(new Point(0, 100), new Dimension(280, 2000), SCREEN));
	}

	@Test
	public void regrowingKeepsTheNearestEdges()
	{
		Dimension taller = new Dimension(280, 500);
		// Top-left: the top-left corner stays put.
		assertEquals(new Point(16, 16), DashGeometry.regrow(new Point(16, 16), SIZE, taller, SCREEN));
		// Bottom-right: the bottom-right corner stays put, so it grows up and left.
		assertEquals(new Point(1624, 524), DashGeometry.regrow(new Point(1624, 624), SIZE, taller, SCREEN));
		// Shrinking in the bottom-right keeps the bottom edge.
		assertEquals(new Point(1624, 724), DashGeometry.regrow(new Point(1624, 624), SIZE, new Dimension(280, 300), SCREEN));
	}

	@Test
	public void readsAndWritesPositions()
	{
		assertEquals(new Point(12, 34), DashGeometry.parsePosition("12,34"));
		assertEquals(new Point(-12, 34), DashGeometry.parsePosition(" -12 , 34 "));
		assertNull(DashGeometry.parsePosition(""));
		assertNull(DashGeometry.parsePosition(null));
		assertNull(DashGeometry.parsePosition("12"));
		assertNull(DashGeometry.parsePosition("a,b"));
		assertEquals("12,34", DashGeometry.formatPosition(new Point(12, 34)));
	}

	@Test
	public void scrollingChangesWidthByTenPercent()
	{
		assertEquals(308, DashGeometry.scrolledWidth(280, -1));
		assertEquals(255, DashGeometry.scrolledWidth(280, 1));
		assertEquals(DashGeometry.MIN_WIDTH, DashGeometry.scrolledWidth(DashGeometry.MIN_WIDTH, 5));
		assertEquals(DashGeometry.MAX_WIDTH, DashGeometry.scrolledWidth(DashGeometry.MAX_WIDTH, -5));
	}

	@Test
	public void widthIsClamped()
	{
		assertEquals(DashGeometry.MIN_WIDTH, DashGeometry.clampWidth(1));
		assertEquals(DashGeometry.MAX_WIDTH, DashGeometry.clampWidth(10_000));
		assertEquals(300, DashGeometry.clampWidth(300));
	}
}

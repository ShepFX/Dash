package com.dash;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.FontManager;

/**
 * The always-on-top window. It paints whatever snapshot it was last given as a stack of tiles,
 * and never takes keyboard focus, so it can sit over other applications without interrupting them.
 */
class DashWindow extends JFrame
{
	private static final int BORDER = 2;
	private static final int DRAG_THRESHOLD = 4;
	/** The hide button in the top-right corner. */
	private static final int BUTTON = 14;
	/** Countdowns and ages tick once a second while the window is up. */
	private static final int CLOCK_MS = 1000;

	private final Runnable onClick;
	private final Runnable onHide;
	private final IntConsumer onScroll;
	private final Consumer<Point> onMoved;
	private final Runnable onHeightChanged;
	private final DashConfig config;
	private final List<DashTiles.Tile> tiles = DashTiles.all();
	private final Timer clock;
	private volatile DashSnapshot snapshot = DashSnapshot.builder().build();
	private Point dragStart;
	private Point windowStart;
	private boolean dragged;
	private boolean hoveringHide;
	private int lastHeight = -1;

	DashWindow(DashConfig config, Runnable onClick, Runnable onHide, IntConsumer onScroll, Consumer<Point> onMoved, Runnable onHeightChanged)
	{
		super("Dash");
		this.config = config;
		this.onClick = onClick;
		this.onHide = onHide;
		this.onScroll = onScroll;
		this.onMoved = onMoved;
		this.onHeightChanged = onHeightChanged;
		setUndecorated(true);
		// A utility window stays off the taskbar and out of alt-tab.
		setType(Type.UTILITY);
		setAlwaysOnTop(true);
		setFocusableWindowState(false);
		setAutoRequestFocus(false);
		setIconImage(ClientUI.ICON_128);
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

		Panel panel = new Panel();
		panel.setBackground(DashTiles.BACKGROUND);
		Mouse mouse = new Mouse();
		panel.addMouseListener(mouse);
		panel.addMouseMotionListener(mouse);
		panel.addMouseWheelListener(mouse);
		setContentPane(panel);

		clock = new Timer(CLOCK_MS, event -> repaint());
		clock.setRepeats(true);
	}

	/** Hands the window a new snapshot. Safe from any thread; the paint happens on the Swing thread. */
	void show(DashSnapshot next)
	{
		if (next == null) return;
		snapshot = next;
		repaint();
	}

	DashSnapshot getSnapshot()
	{
		return snapshot;
	}

	@Override
	public void setVisible(boolean visible)
	{
		super.setVisible(visible);
		if (visible) clock.start();
		else clock.stop();
	}

	@Override
	public void dispose()
	{
		clock.stop();
		super.dispose();
	}

	/** The height the tiles need at the given width. Swing thread only. */
	int preferredHeight(int width)
	{
		Graphics2D g = DashTiles.measuringGraphics();
		try
		{
			DashTiles.Layout layout = layoutFor(g, width);
			int height = BORDER;
			for (DashTiles.Tile tile : tiles)
			{
				if (!tile.enabled(config)) continue;
				height += tile.height(layout) + 1;
			}
			return Math.max(height + BORDER, 3 * layout.row);
		}
		finally
		{
			g.dispose();
		}
	}

	void setOpacityPercent(int percent)
	{
		float opacity = Math.max(0.2f, Math.min(1f, percent / 100f));
		try
		{
			GraphicsDevice device = getGraphicsConfiguration().getDevice();
			if (device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT))
			{
				setOpacity(opacity);
			}
		}
		catch (RuntimeException ignored)
		{
			// Some window managers refuse; a solid window is fine.
		}
	}

	private DashTiles.Layout layoutFor(Graphics2D g, int width)
	{
		float points = config.textSize().getPoints();
		Font font = FontManager.getRunescapeFont().deriveFont(points + 2);
		Font bold = FontManager.getRunescapeBoldFont().deriveFont(points + 2);
		Font small = FontManager.getRunescapeSmallFont().deriveFont(points + 1);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		FontMetrics fm = g.getFontMetrics(font);
		FontMetrics boldFm = g.getFontMetrics(bold);
		FontMetrics smallFm = g.getFontMetrics(small);
		return new DashTiles.Layout(g, width - 2 * BORDER, font, bold, small, fm, boldFm, smallFm, snapshot, config, System.currentTimeMillis());
	}

	private Rectangle hideButton()
	{
		return new Rectangle(getWidth() - BORDER - BUTTON - 3, BORDER + 3, BUTTON, BUTTON);
	}

	/** Paints the tiles and frame into any graphics. Returns the height the tiles wanted. */
	int paintTiles(Graphics2D g, int width, int height, boolean hover)
	{
		g.setColor(DashTiles.BACKGROUND);
		g.fillRect(0, 0, width, height);
		DashTiles.Layout layout = layoutFor(g, width);
		int y = BORDER;
		g.translate(BORDER, 0);
		for (DashTiles.Tile tile : tiles)
		{
			if (!tile.enabled(config)) continue;
			int tall = tile.height(layout);
			g.setColor(DashTiles.PANEL);
			g.fillRoundRect(0, y, layout.width, tall, 8, 8);
			tile.paint(layout, y);
			y += tall + 1;
		}
		g.translate(-BORDER, 0);
		g.setColor(DashTiles.LINE);
		g.setStroke(new BasicStroke(BORDER));
		g.drawRect(BORDER / 2, BORDER / 2, width - BORDER, height - BORDER);
		Rectangle button = new Rectangle(width - BORDER - BUTTON - 3, BORDER + 3, BUTTON, BUTTON);
		g.setColor(hover ? new Color(255, 255, 255, 200) : new Color(255, 255, 255, 90));
		g.setStroke(new BasicStroke(2));
		g.drawLine(button.x + 3, button.y + button.height / 2, button.x + button.width - 3, button.y + button.height / 2);
		return y + BORDER;
	}

	/** The window as an image, for looking at it without a screen. Swing thread not required. */
	BufferedImage render(int width)
	{
		int height = preferredHeight(width);
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try
		{
			paintTiles(g, width, height, false);
		}
		finally
		{
			g.dispose();
		}
		return image;
	}

	private final class Panel extends JPanel
	{
		@Override
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			int wanted = paintTiles((Graphics2D) graphics, getWidth(), getHeight(), hoveringHide);
			if (wanted != lastHeight)
			{
				// Contents changed shape - an offer finished, a timer ended - so the window follows.
				lastHeight = wanted;
				onHeightChanged.run();
			}
		}
	}

	private final class Mouse extends MouseAdapter
	{
		@Override
		public void mouseMoved(MouseEvent event)
		{
			boolean overHide = hideButton().contains(event.getPoint());
			if (overHide != hoveringHide)
			{
				hoveringHide = overHide;
				repaint();
			}
			setCursor(Cursor.getPredefinedCursor(overHide ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
		}

		@Override
		public void mouseExited(MouseEvent event)
		{
			if (hoveringHide)
			{
				hoveringHide = false;
				repaint();
			}
		}

		@Override
		public void mousePressed(MouseEvent event)
		{
			if (event.getButton() == MouseEvent.BUTTON1 && hideButton().contains(event.getPoint()))
			{
				dragStart = null;
				onHide.run();
				return;
			}
			dragStart = event.getLocationOnScreen();
			windowStart = getLocation();
			dragged = false;
		}

		@Override
		public void mouseDragged(MouseEvent event)
		{
			if (dragStart == null) return;
			Point now = event.getLocationOnScreen();
			int dx = now.x - dragStart.x;
			int dy = now.y - dragStart.y;
			if (!dragged && Math.abs(dx) < DRAG_THRESHOLD && Math.abs(dy) < DRAG_THRESHOLD) return;
			dragged = true;
			setLocation(windowStart.x + dx, windowStart.y + dy);
		}

		@Override
		public void mouseReleased(MouseEvent event)
		{
			if (dragStart == null) return;
			dragStart = null;
			if (dragged) onMoved.accept(getLocation());
			else if (event.getButton() == MouseEvent.BUTTON1) onClick.run();
		}

		@Override
		public void mouseWheelMoved(MouseWheelEvent event)
		{
			onScroll.accept(event.getWheelRotation());
		}
	}

	Dimension sizeFor(int width)
	{
		return new Dimension(width, preferredHeight(width));
	}
}

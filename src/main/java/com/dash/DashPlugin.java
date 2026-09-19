package com.dash;

import com.google.inject.Provides;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NotificationFired;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Dash",
	description = "A small always-on-top window of your vitals, Grand Exchange offers, timers and alerts for a second monitor or beside another app",
	tags = {"dashboard", "window", "always on top", "second monitor", "afk", "grand exchange", "vitals", "timers"}
)
public class DashPlugin extends Plugin
{
	private static final int INVENTORY_SLOTS = 28;
	private static final int MAX_ALERTS = 10;
	private static final int TOP_GAINS = 3;
	/** How often to look again while RuneLite is unfocused but probably still in view. */
	private static final int RECHECK_MS = 1000;
	/** The window fades to this share of its opacity while RuneLite is the active window. */
	private static final float DIM = 0.55f;
	/** Hitpoints at or below this share of the maximum raise an alert, once per dip. */
	private static final int LOW_HITPOINTS_PERCENT = 25;
	private static final String LEVEL_UP = "Congratulations, you've just advanced";

	@Inject private Client client;
	@Inject private DashConfig config;
	@Inject private ConfigManager configManager;
	@Inject private KeyManager keyManager;
	@Inject private ItemManager itemManager;
	@Inject private InfoBoxManager infoBoxManager;

	private final HotkeyListener hotkey = new HotkeyListener(() -> config.hotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			SwingUtilities.invokeLater(DashPlugin.this::toggle);
		}
	};
	private final WindowFocusListener focusListener = new WindowFocusListener()
	{
		@Override
		public void windowGainedFocus(WindowEvent event)
		{
			clientFocused();
		}

		@Override
		public void windowLostFocus(WindowEvent event)
		{
			clientUnfocused();
		}
	};

	// Swing thread state.
	private DashWindow window;
	private Window clientWindow;
	private Timer showTimer;
	private boolean pinned;
	private boolean autoShown;
	/** Set when the user dismissed the window; it waits for RuneLite to be focused again first. */
	private boolean dismissed;
	private boolean focused = true;

	// Client thread state.
	private final Deque<DashSnapshot.Alert> alerts = new ArrayDeque<>();
	private final Map<Integer, AsyncBufferedImage> icons = new HashMap<>();
	private final Map<Skill, Integer> startXp = new EnumMap<>(Skill.class);
	private long sessionStartedAt;
	private long loginAt;
	private long lastActiveAt;
	private long attackedAt;
	private boolean lowHitpoints;
	private final Map<Integer, GrandExchangeOfferState> offerStates = new HashMap<>();
	private WorldPoint lastPosition;
	private int lastAnimation = -1;

	@Override
	protected void startUp()
	{
		keyManager.registerKeyListener(hotkey);
		SwingUtilities.invokeLater(() ->
		{
			window = new DashWindow(config, this::clicked, this::hide, this::scrolled, this::moved, this::heightChanged);
			showTimer = new Timer(RECHECK_MS, event -> autoShow());
			showTimer.setRepeats(true);
			applyWindowConfig();
			attachToClientWindow();
			updateVisibility();
		});
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(hotkey);
		alerts.clear();
		icons.clear();
		resetSession();
		SwingUtilities.invokeLater(() ->
		{
			if (showTimer != null) showTimer.stop();
			if (clientWindow != null) clientWindow.removeWindowFocusListener(focusListener);
			if (window != null) window.dispose();
			window = null;
			clientWindow = null;
			showTimer = null;
			pinned = false;
			autoShown = false;
			dismissed = false;
			focused = true;
		});
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		// The canvas can be parented after the plugin starts; keep trying until the window is found.
		if (clientWindow == null && window != null) SwingUtilities.invokeLater(this::attachToClientWindow);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!DashConfig.GROUP.equals(event.getGroup())) return;
		// Picking a corner is a request to go there, so a dragged position no longer applies.
		if (DashConfig.KEY_CORNER.equals(event.getKey())) configManager.unsetConfiguration(DashConfig.GROUP, DashConfig.KEY_POSITION);
		SwingUtilities.invokeLater(() ->
		{
			applyWindowConfig();
			updateVisibility();
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGIN_SCREEN:
			case LOGIN_SCREEN_AUTHENTICATOR:
				resetSession();
				publish();
				break;
			case LOGGING_IN:
				resetSession();
				break;
			case HOPPING:
				// A hop starts the six hour clock again but is the same session.
				loginAt = 0;
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// The first tick after a login or hop starts the clocks; the plugin may also have been
		// switched on mid-session, in which case now is the best guess there is.
		long now = System.currentTimeMillis();
		if (loginAt == 0) loginAt = now;
		if (sessionStartedAt == 0) sessionStartedAt = now;
		trackActivity();
		checkHitpoints();
		publish();
	}

	private void checkHitpoints()
	{
		int max = client.getRealSkillLevel(Skill.HITPOINTS);
		int current = client.getBoostedSkillLevel(Skill.HITPOINTS);
		boolean low = max > 0 && current * 100 <= LOW_HITPOINTS_PERCENT * max;
		if (low && !lowHitpoints) remember("Hitpoints low: " + current + " / " + max);
		lowHitpoints = low;
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Player me = client.getLocalPlayer();
		if (event.getActor() != me) return;
		Actor attacker = me.getInteracting();
		attacked(attacker != null && attacker != me ? attacker : null);
	}

	/** A hit or a hostile target a while after the last one is a new fight, and worth an alert. */
	private void attacked(Actor by)
	{
		long now = System.currentTimeMillis();
		boolean fresh = now - attackedAt >= DashTiles.ATTACK_MEMORY_MS;
		attackedAt = now;
		if (!fresh) return;
		String name = by != null && by.getName() != null ? Text.removeTags(by.getName()) : null;
		remember(name != null ? "Under attack by " + name : "Under attack");
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		Player me = client.getLocalPlayer();
		if (me == null || event.getTarget() != me || event.getSource() == me) return;
		// A shopkeeper or clerk turns to face you too; only something that can fight counts.
		Actor source = event.getSource();
		boolean hostile = source instanceof Player || (source instanceof NPC && ((NPC) source).getCombatLevel() > 0);
		if (hostile) attacked(source);
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		GrandExchangeOffer offer = event.getOffer();
		GrandExchangeOfferState state = offer.getState();
		// Every slot reports its state on login; only a change seen while watching is news.
		GrandExchangeOfferState previous = offerStates.put(event.getSlot(), state);
		if (previous == null || previous == state) return;
		if (state == GrandExchangeOfferState.BOUGHT || state == GrandExchangeOfferState.SOLD)
		{
			String name = itemManager.getItemComposition(offer.getItemId()).getName();
			String verb = state == GrandExchangeOfferState.BOUGHT ? "Bought " : "Sold ";
			remember(verb + DashFormat.commas(offer.getTotalQuantity()) + " " + name);
		}
	}

	@Subscribe
	public void onNotificationFired(NotificationFired event)
	{
		if (event.getMessage() != null) remember(event.getMessage());
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		ChatMessageType type = event.getType();
		if (type == ChatMessageType.GAMEMESSAGE && event.getMessage() != null && event.getMessage().contains(LEVEL_UP))
		{
			remember(Text.removeTags(event.getMessage()));
			return;
		}
		if (type != ChatMessageType.PRIVATECHAT && type != ChatMessageType.MODPRIVATECHAT) return;
		String from = Text.removeTags(event.getName());
		remember("PM from " + from + ": " + Text.removeTags(event.getMessage()));
	}

	private void remember(String text)
	{
		alerts.addFirst(new DashSnapshot.Alert(System.currentTimeMillis(), text));
		while (alerts.size() > MAX_ALERTS) alerts.removeLast();
		publish();
	}

	private void resetSession()
	{
		sessionStartedAt = 0;
		loginAt = 0;
		startXp.clear();
		lastActiveAt = 0;
		attackedAt = 0;
		lowHitpoints = false;
		offerStates.clear();
		lastPosition = null;
		lastAnimation = -1;
	}

	/** Anything the player does - moving, animating, interacting - counts as being active. */
	private void trackActivity()
	{
		Player me = client.getLocalPlayer();
		if (me == null) return;
		long now = System.currentTimeMillis();
		WorldPoint position = me.getWorldLocation();
		int animation = me.getAnimation();
		boolean moved = lastPosition != null && !lastPosition.equals(position);
		boolean animating = animation != -1 && animation != lastAnimation;
		if (moved || animating || me.getInteracting() != null || lastActiveAt == 0) lastActiveAt = now;
		lastPosition = position;
		lastAnimation = animation;
	}

	/** Reads everything on the client thread and hands the window an immutable copy. */
	private void publish()
	{
		DashWindow target = window;
		if (target == null) return;
		target.show(snapshot());
	}

	private DashSnapshot snapshot()
	{
		long now = System.currentTimeMillis();
		DashSnapshot.DashSnapshotBuilder b = DashSnapshot.builder().takenAt(now).alerts(new ArrayList<>(alerts));
		Player me = client.getLocalPlayer();
		boolean loggedIn = client.getGameState() == GameState.LOGGED_IN && me != null;
		b.loggedIn(loggedIn);
		if (!loggedIn) return b.build();

		b.hitpoints(client.getBoostedSkillLevel(Skill.HITPOINTS)).maxHitpoints(client.getRealSkillLevel(Skill.HITPOINTS))
			.prayer(client.getBoostedSkillLevel(Skill.PRAYER)).maxPrayer(client.getRealSkillLevel(Skill.PRAYER))
			.runEnergy(client.getEnergy() / 100).special(client.getVarpValue(VarPlayerID.SA_ENERGY) / 10)
			.poison(DashFormat.poison(client.getVarpValue(VarPlayerID.POISON)));

		b.world(client.getWorld()).worldTags(DashFormat.worldTags(client.getWorldType()))
			.dangerousWorld(DashFormat.dangerous(client.getWorldType()))
			.playerName(me.getName()).combatLevel(me.getCombatLevel()).skulled(me.getSkullIcon() != -1)
			.lastActiveAt(lastActiveAt).attackedAt(attackedAt);
		Actor target = me.getInteracting();
		if (target != null && target.getName() != null && fighting(me, target, now)) b.fighting(Text.removeTags(target.getName()));

		b.offers(offers());
		b.boxes(boxes());
		session(b, now);

		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory != null)
		{
			int used = 0;
			for (Item item : inventory.getItems())
			{
				if (item.getId() > 0) used++;
			}
			b.freeSlots(Math.max(0, INVENTORY_SLOTS - used)).coins(inventory.count(ItemID.COINS));
		}
		b.loginAt(loginAt);
		return b.build();
	}

	/**
	 * Interacting with something is not fighting it: clerks, bankers and quest NPCs are interacted
	 * with too. It is a fight when the other side can fight and is engaging back, or has hit us.
	 */
	private boolean fighting(Player me, Actor target, long now)
	{
		boolean canFight = target instanceof Player || (target instanceof NPC && ((NPC) target).getCombatLevel() > 0);
		if (!canFight) return false;
		return target.getInteracting() == me || now - attackedAt < DashTiles.ATTACK_MEMORY_MS;
	}

	private List<DashSnapshot.Offer> offers()
	{
		List<DashSnapshot.Offer> offers = new ArrayList<>();
		GrandExchangeOffer[] slots = client.getGrandExchangeOffers();
		if (slots == null) return offers;
		for (int slot = 0; slot < slots.length; slot++)
		{
			GrandExchangeOffer offer = slots[slot];
			if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY || offer.getItemId() <= 0) continue;
			GrandExchangeOfferState state = offer.getState();
			boolean buying = state == GrandExchangeOfferState.BUYING || state == GrandExchangeOfferState.BOUGHT
				|| state == GrandExchangeOfferState.CANCELLED_BUY;
			String name = itemManager.getItemComposition(offer.getItemId()).getName();
			offers.add(new DashSnapshot.Offer(slot, name, icon(offer.getItemId()), buying,
				offer.getQuantitySold(), offer.getTotalQuantity(), offer.getPrice(), state));
		}
		return offers;
	}

	private AsyncBufferedImage icon(int itemId)
	{
		AsyncBufferedImage image = icons.get(itemId);
		if (image == null)
		{
			image = itemManager.getImage(itemId);
			icons.put(itemId, image);
			image.onLoaded(() ->
			{
				DashWindow target = window;
				if (target != null) target.repaint();
			});
		}
		return image;
	}

	/** Every infobox RuneLite is showing, as the picture, text and name it gives them. */
	private List<DashSnapshot.Box> boxes()
	{
		List<DashSnapshot.Box> boxes = new ArrayList<>();
		for (InfoBox box : infoBoxManager.getInfoBoxes())
		{
			if (!box.render()) continue;
			String name = DashFormat.plainText(box.getTooltip());
			if (name.isEmpty()) name = box.getName() == null ? "" : box.getName();
			int cut = name.indexOf(" · ");
			if (cut > 0) name = name.substring(0, cut);
			boxes.add(new DashSnapshot.Box(name, box.getText(), box.getTextColor(), box.getImage()));
		}
		return boxes;
	}

	// OVERALL is deprecated but still in values(), and it is not a skill to measure.
	@SuppressWarnings("deprecation")
	private void session(DashSnapshot.DashSnapshotBuilder b, long now)
	{
		if (sessionStartedAt == 0) return;
		if (startXp.isEmpty())
		{
			// Experience arrives a moment after login; until it has, there is nothing to measure from.
			long total = 0;
			for (Skill skill : Skill.values())
			{
				if (skill == Skill.OVERALL) continue;
				int xp = client.getSkillExperience(skill);
				total += xp;
				startXp.put(skill, xp);
			}
			if (total == 0) startXp.clear();
		}
		long gained = 0;
		List<DashSnapshot.SkillGain> gains = new ArrayList<>();
		long elapsed = now - sessionStartedAt;
		for (Map.Entry<Skill, Integer> entry : startXp.entrySet())
		{
			long delta = client.getSkillExperience(entry.getKey()) - entry.getValue();
			if (delta <= 0) continue;
			gained += delta;
			gains.add(new DashSnapshot.SkillGain(entry.getKey().getName(), delta, DashFormat.perHour(delta, elapsed)));
		}
		gains.sort(Comparator.comparingLong(DashSnapshot.SkillGain::getGained).reversed());
		b.sessionStartedAt(sessionStartedAt).xpGained(gained).topGains(gains.subList(0, Math.min(TOP_GAINS, gains.size())));
	}

	// Window management, Swing thread from here on.

	private void attachToClientWindow()
	{
		if (clientWindow != null || client.getCanvas() == null) return;
		Window found = SwingUtilities.getWindowAncestor(client.getCanvas());
		if (found == null) return;
		clientWindow = found;
		clientWindow.addWindowFocusListener(focusListener);
		// The window may have been placed before RuneLite's own window existed, on the wrong screen.
		if (window.isVisible()) position();
		if (!clientWindow.isFocused()) clientUnfocused();
	}

	private void clientFocused()
	{
		focused = true;
		if (showTimer != null) showTimer.stop();
		autoShown = false;
		if (config.mode() != DashMode.ALWAYS) dismissed = false;
		applyOpacity();
		updateVisibility();
	}

	private void clientUnfocused()
	{
		focused = false;
		applyOpacity();
		if (config.mode() != DashMode.WHEN_UNFOCUSED || dismissed || showTimer == null) return;
		showTimer.setInitialDelay(config.delay());
		showTimer.restart();
	}

	private void autoShow()
	{
		// Focus may have gone to another RuneLite window - a config dialog, say - rather than away.
		if (clientWindow == null || clientWindow.isFocused() || anyOwnWindowFocused()) return;
		if (config.mode() != DashMode.WHEN_UNFOCUSED || dismissed) return;
		showTimer.stop();
		autoShown = true;
		updateVisibility();
	}

	/** Whether one of the client's own dialogs has focus rather than another program. */
	private boolean anyOwnWindowFocused()
	{
		for (Window owned : clientWindow.getOwnedWindows())
		{
			if (owned.isFocused()) return true;
		}
		return false;
	}

	/** The hotkey: shows the window if it is hidden, hides it if it is showing. */
	private void toggle()
	{
		if (window == null) return;
		if (window.isVisible())
		{
			hide();
			return;
		}
		dismissed = false;
		pinned = config.mode() != DashMode.ALWAYS;
		updateVisibility();
	}

	private void updateVisibility()
	{
		if (window == null) return;
		boolean visible;
		switch (config.mode())
		{
			case ALWAYS:
				visible = !dismissed;
				break;
			case WHEN_UNFOCUSED:
				visible = pinned || autoShown;
				break;
			default:
				visible = pinned;
				break;
		}
		if (visible && !window.isVisible())
		{
			position();
			window.setVisible(true);
		}
		else if (!visible && window.isVisible())
		{
			window.setVisible(false);
		}
	}

	private void applyWindowConfig()
	{
		if (window == null) return;
		applyOpacity();
		if (config.mode() != DashMode.WHEN_UNFOCUSED) autoShown = false;
		if (config.mode() != DashMode.HOTKEY_ONLY && config.mode() != DashMode.WHEN_UNFOCUSED) pinned = false;
		if (window.isVisible()) position();
	}

	private void applyOpacity()
	{
		if (window == null) return;
		int opacity = config.opacity();
		if (focused && config.dimWhenFocused() && config.mode() == DashMode.ALWAYS) opacity = Math.round(opacity * DIM);
		window.setOpacityPercent(opacity);
	}

	private void position()
	{
		Dimension size = window.sizeFor(DashGeometry.clampWidth(config.width()));
		Point custom = DashGeometry.parsePosition(config.position());
		Point location;
		if (custom != null)
		{
			// A dragged window stays on whichever screen it was dragged to.
			location = DashGeometry.clamp(custom, size, workArea(screenContaining(custom)));
		}
		else
		{
			location = DashGeometry.place(config.corner(), config.margin(), workArea(clientScreen()), size, clientBounds());
		}
		window.setSize(size);
		window.setLocation(location);
		log.debug("Dash window at {} size {} on screen {}", location, size, workArea(screenContaining(location)));
	}

	/** The contents changed height; keep the edge nearest the screen edge where it was. */
	private void heightChanged()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (window == null || !window.isVisible()) return;
			Dimension old = window.getSize();
			Dimension size = window.sizeFor(old.width);
			if (size.equals(old)) return;
			Point custom = DashGeometry.parsePosition(config.position());
			Rectangle area = workArea(screenContaining(window.getLocation()));
			Point location = custom != null
				? DashGeometry.regrow(window.getLocation(), old, size, area)
				: DashGeometry.place(config.corner(), config.margin(), workArea(clientScreen()), size, clientBounds());
			window.setSize(size);
			window.setLocation(location);
		});
	}

	/** Where the RuneLite window is, or null when it is minimised or not to be avoided. */
	private Rectangle clientBounds()
	{
		if (!config.avoidClient() || clientWindow == null || !clientWindow.isShowing()) return null;
		if (clientWindow instanceof Frame && (((Frame) clientWindow).getExtendedState() & Frame.ICONIFIED) != 0) return null;
		return clientWindow.getBounds();
	}

	private GraphicsConfiguration clientScreen()
	{
		return clientWindow != null ? clientWindow.getGraphicsConfiguration() : window.getGraphicsConfiguration();
	}

	private GraphicsConfiguration screenContaining(Point point)
	{
		for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())
		{
			GraphicsConfiguration graphics = device.getDefaultConfiguration();
			if (graphics.getBounds().contains(point)) return graphics;
		}
		return clientScreen();
	}

	/** The usable area of a screen, taskbar excluded. */
	private static Rectangle workArea(GraphicsConfiguration graphics)
	{
		Rectangle bounds = graphics.getBounds();
		Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(graphics);
		return new Rectangle(bounds.x + insets.left, bounds.y + insets.top,
			bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom);
	}

	private void clicked()
	{
		if (config.clickAction() == ClickAction.HIDE_DASH) hide();
	}

	/** Hides the window. In always mode the hotkey brings it back; otherwise focusing RuneLite does. */
	private void hide()
	{
		pinned = false;
		autoShown = false;
		dismissed = true;
		updateVisibility();
	}

	private void scrolled(int notches)
	{
		int width = DashGeometry.scrolledWidth(config.width(), notches);
		if (width != config.width()) configManager.setConfiguration(DashConfig.GROUP, DashConfig.KEY_WIDTH, width);
	}

	private void moved(Point location)
	{
		configManager.setConfiguration(DashConfig.GROUP, DashConfig.KEY_POSITION, DashGeometry.formatPosition(location));
	}

	@Provides
	DashConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DashConfig.class);
	}
}

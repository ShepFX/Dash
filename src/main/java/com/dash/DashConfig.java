package com.dash;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(DashConfig.GROUP)
public interface DashConfig extends Config
{
	String GROUP = "dash";
	String KEY_WIDTH = "width";
	String KEY_POSITION = "position";
	String KEY_CORNER = "corner";

	@ConfigSection(name = "Showing", description = "When the Dash window appears", position = 0)
	String showing = "showing";

	@ConfigSection(name = "Window", description = "Where it goes and what it looks like", position = 1)
	String window = "window";

	@ConfigSection(name = "Tiles", description = "What the window shows, top to bottom", position = 2)
	String tiles = "tiles";

	@ConfigItem(keyName = "mode", name = "Show", description = "Always: on screen the whole time. Only when unfocused: appears when RuneLite is not the active window and goes when it is again. Only with hotkey: shown and hidden by the hotkey alone", section = showing, position = 0)
	default DashMode mode()
	{
		return DashMode.ALWAYS;
	}

	@ConfigItem(keyName = "delay", name = "Show after", description = "When showing only while RuneLite is not the active window: how long it must be unfocused before the window appears", section = showing, position = 1)
	@Units(Units.MILLISECONDS)
	@Range(max = 10000)
	default int delay()
	{
		return 1000;
	}

	@ConfigItem(keyName = "hotkey", name = "Show / hide hotkey", description = "Shows the window, or hides it. Only works while RuneLite is the active window - other programs keep their own shortcuts", section = showing, position = 2)
	default Keybind hotkey()
	{
		return new Keybind(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
	}

	@ConfigItem(keyName = "clickAction", name = "On click", description = "What a click on the Dash window does. Dragging always moves it", section = showing, position = 3)
	default ClickAction clickAction()
	{
		return ClickAction.NOTHING;
	}

	@ConfigItem(keyName = "dimWhenFocused", name = "Dim when RuneLite is active", description = "Fade the window while RuneLite is the active window, since the game itself is in view then", section = showing, position = 4)
	default boolean dimWhenFocused()
	{
		return true;
	}

	@ConfigItem(keyName = KEY_CORNER, name = "Corner", description = "Which corner of the screen the window sits in, until you drag it somewhere else", section = window, position = 0)
	default Corner corner()
	{
		return Corner.TOP_RIGHT;
	}

	@ConfigItem(keyName = "avoidClient", name = "Keep off RuneLite", description = "When the RuneLite window is on that screen, use a corner beside it if there is one", section = window, position = 1)
	default boolean avoidClient()
	{
		return true;
	}

	@ConfigItem(keyName = "margin", name = "Margin", description = "Gap between the window and the edge of the screen", section = window, position = 2)
	@Units(Units.PIXELS)
	@Range(max = 200)
	default int margin()
	{
		return 16;
	}

	@ConfigItem(keyName = KEY_WIDTH, name = "Width", description = "Width of the window; the height follows its contents. Scrolling over the window changes this too", section = window, position = 3)
	@Units(Units.PIXELS)
	@Range(min = DashGeometry.MIN_WIDTH, max = DashGeometry.MAX_WIDTH)
	default int width()
	{
		return 280;
	}

	@ConfigItem(keyName = "opacity", name = "Opacity", description = "How solid the window is", section = window, position = 4)
	@Units(Units.PERCENT)
	@Range(min = 20, max = 100)
	default int opacity()
	{
		return 100;
	}

	@ConfigItem(keyName = "textSize", name = "Text size", description = "How large the text is", section = window, position = 5)
	default TextSize textSize()
	{
		return TextSize.NORMAL;
	}

	@ConfigItem(keyName = "showVitals", name = "Vitals", description = "Hitpoints, prayer, run energy and special attack, with poison and venom", section = tiles, position = 0)
	default boolean showVitals()
	{
		return true;
	}

	@ConfigItem(keyName = "showStatus", name = "Status", description = "World and its type, who you are logged in as, whether you are idle, in combat or skulled", section = tiles, position = 1)
	default boolean showStatus()
	{
		return true;
	}

	@ConfigItem(keyName = "showOffers", name = "Grand Exchange", description = "Every slot with an offer in it, and how far along it is", section = tiles, position = 2)
	default boolean showOffers()
	{
		return true;
	}

	@ConfigItem(keyName = "showAlerts", name = "Alerts", description = "The last few things worth knowing about: being attacked, low hitpoints, an offer finishing, a level up, a private message, or any RuneLite notification, with how long ago each arrived", section = tiles, position = 3)
	default boolean showAlerts()
	{
		return true;
	}

	@ConfigItem(keyName = "showTimers", name = "Timers and boosts", description = "Everything RuneLite is showing as an infobox: timers, stat boosts, counters", section = tiles, position = 4)
	default boolean showTimers()
	{
		return true;
	}

	@ConfigItem(keyName = "showSession", name = "Session", description = "Time logged in and experience gained since, with the rate for the skills that gained most", section = tiles, position = 5)
	default boolean showSession()
	{
		return true;
	}

	@ConfigItem(keyName = "showInventory", name = "Inventory", description = "Free slots and coins carried", section = tiles, position = 6)
	default boolean showInventory()
	{
		return true;
	}

	@ConfigItem(keyName = "showLogout", name = "Six hour logout", description = "Time until the game logs you out for having been logged in six hours", section = tiles, position = 7)
	default boolean showLogout()
	{
		return true;
	}

	@ConfigItem(keyName = "alertCount", name = "Alerts to keep", description = "How many recent alerts the Alerts tile lists", section = tiles, position = 8)
	@Range(min = 1, max = 10)
	default int alertCount()
	{
		return 4;
	}

	/** "x,y" of where the window was last dragged to, or empty to use the corner. */
	@ConfigItem(keyName = KEY_POSITION, name = "", description = "", hidden = true)
	default String position()
	{
		return "";
	}
}

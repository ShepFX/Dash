package com.dash;

import java.awt.Color;
import java.awt.Image;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import net.runelite.api.GrandExchangeOfferState;

/**
 * Everything the window shows, read on the client thread once a tick and handed to the Swing
 * thread whole. Immutable, so the two threads never share anything that changes.
 */
@Value
@Builder
class DashSnapshot
{
	long takenAt;
	boolean loggedIn;

	// Vitals.
	int hitpoints;
	int maxHitpoints;
	int prayer;
	int maxPrayer;
	/** 0 to 100. */
	int runEnergy;
	/** 0 to 100. */
	int special;
	@Builder.Default
	DashFormat.Poison poison = DashFormat.Poison.NONE;

	// Status.
	int world;
	@Builder.Default
	List<String> worldTags = Collections.emptyList();
	boolean dangerousWorld;
	String playerName;
	int combatLevel;
	boolean skulled;
	/** When the player last did anything; 0 when unknown. */
	long lastActiveAt;
	/** What the player is interacting with, or null. */
	String fighting;
	/** When something last hit or targeted the player; 0 when never. */
	long attackedAt;

	@Builder.Default
	List<Offer> offers = Collections.emptyList();
	@Builder.Default
	List<Alert> alerts = Collections.emptyList();
	@Builder.Default
	List<Box> boxes = Collections.emptyList();

	// Session.
	/** 0 when not logged in. */
	long sessionStartedAt;
	long xpGained;
	@Builder.Default
	List<SkillGain> topGains = Collections.emptyList();

	// Inventory.
	int freeSlots;
	long coins;

	/** When this login (or hop) happened, for the six hour logout; 0 when unknown. */
	long loginAt;

	@Value
	static class Offer
	{
		int slot;
		String itemName;
		/** May still be loading; it repaints itself when it arrives. */
		Image icon;
		boolean buying;
		int done;
		int total;
		/** Widened to long in RuneLite 1.13.0 for prices past the old signed 32 bit ceiling. */
		long price;
		GrandExchangeOfferState state;

		boolean finished()
		{
			return state == GrandExchangeOfferState.BOUGHT || state == GrandExchangeOfferState.SOLD;
		}

		boolean cancelled()
		{
			return state == GrandExchangeOfferState.CANCELLED_BUY || state == GrandExchangeOfferState.CANCELLED_SELL;
		}
	}

	@Value
	static class Alert
	{
		long at;
		String text;
	}

	/** An infobox as RuneLite draws it: a picture, a bit of text, and a name from its tooltip. */
	@Value
	static class Box
	{
		String name;
		String text;
		Color textColor;
		Image image;
	}

	@Value
	static class SkillGain
	{
		String skill;
		long gained;
		long perHour;
	}
}

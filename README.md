# Dash

A [RuneLite](https://runelite.net) plugin that puts a small always-on-top window of the numbers you
keep glancing at - hitpoints, prayer, run energy, Grand Exchange offers, timers, alerts - on a
second monitor, or in a corner beside whatever else you are doing.

It shows nothing the game does not already show you, and it does nothing in the game. It is a
window of data. Click it and nothing happens; drag it and it moves.

## What it shows

Tiles, top to bottom, each of which can be switched off:

- **Vitals** - hitpoints, prayer, run energy and special attack as bars, and whether you are
  poisoned or venomed.
- **Status** - the world and what kind of world it is (free, members, PvP, high risk, Deadman and
  so on, with the dangerous ones in red), who you are logged in as, whether you are skulled, and
  whether you are active, idle, fighting something or under attack.
- **Grand Exchange** - every slot with an offer in it: the item, buying or selling, how far along
  it is, and the price. Finished offers turn green.
- **Alerts** - the last few things worth knowing about, with how long ago each happened, so
  anything you missed while away is still readable: being attacked (once per fight, naming the
  attacker), hitpoints dropping to a quarter, an offer finishing, a level up, a private message,
  and any RuneLite notification such as the idle notifier.
- **Timers and boosts** - everything RuneLite is showing as an infobox: potion timers, stat
  boosts, counters, whatever other plugins add.
- **Session** - how long you have been logged in, the experience gained since, and the rate for
  the skills that gained most.
- **Inventory** - free slots and coins carried.
- **Six hour logout** - time until the game logs you out for having been logged in six hours.
  It goes amber in the last hour and red in the last fifteen minutes.

The window grows and shrinks with its contents, and holds the edge nearest the screen edge still
while it does.

## Showing it

- **Always** (the default) keeps it on screen. While RuneLite is the active window it fades, since
  the game itself is in view then; that can be turned off.
- **When RuneLite is not the active window** shows it a moment after you switch away and hides it
  when you come back, the way [Lookout](https://github.com/CoreyUK/Lookout) does.
- **Only with the hotkey** leaves it to you. Ctrl+Shift+D by default; it only works while
  RuneLite is the active window, because the Dash window never takes keyboard focus and other
  programs keep their own shortcuts.

The **-** in the corner hides it. In always mode the hotkey brings it back; in the other modes,
focusing RuneLite does.

## Settings

- **Show**, **Show after**, **Show / hide hotkey**, **On click**, **Dim when RuneLite is active**.
- **Corner**, **Keep off RuneLite**, **Margin**, **Width**, **Opacity**, **Text size**. Scrolling
  over the window changes the width too, and a dragged window remembers where it was put, on
  whichever screen that was.
- One switch per tile, and how many alerts to keep.

## Building

    ./gradlew build

`./gradlew run` starts a development client with the plugin loaded. `./gradlew test` also writes
`build/dash-preview.png`, the window painted with made-up data, which is the quickest way to see
what a layout change did.

## Licence

BSD 2-Clause. See [LICENSE](LICENSE).

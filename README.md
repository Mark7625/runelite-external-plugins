# Hitsplat Styles

Gives your hitsplats the look of an older RuneScape. Pick a year and every hit, block, poison and
venom splat is drawn in that era's art.

![Hitsplat Styles](docs/showoff.png)

## What it does

- **Four looks to choose from** - OSRS, 2002, 2010 and 2011.
- **Combat style icons** - a little melee, ranged, magic or cannon icon next to each hit, so you can
  see at a glance what landed. Comes in a modern or an OSRS flavour.
- **Mix and match blocks** - stay on OSRS splats but borrow the 2010 or 2011 shield for blocks.
- **Cleaner blocks** - drop the `0` and the icon from blocked hits so the shield speaks for itself.
- **Float and fade** - splats drift up and fade away instead of blinking out.
- **Drop shadows** - a soft shadow so splats stand out against bright ground.
- **Tinting** - the game dims hits you had no hand in. Keep it, switch it off, or dim everything.

## Settings

| Setting | Default | What it does |
| --- | --- | --- |
| Style | 2010 | The era your splats are drawn in |
| Tint other people's hits | Game default | Dim hits you had no part in, as the game does |
| OSRS block splat | Default | Borrow the 2010 or 2011 shield for blocks while on OSRS |
| Hide blocked damage text | On | No `0` on blocked hits |
| Hide icon on blocked hits | On | No style icon on blocked hits |
| Style icons | Modern | Which icon set sits beside the splat |
| Drop shadows | On | Soft shadow behind splats and icons |
| Fade out | On | Float up and fade instead of vanishing |
| Fade length | 25 | How long that fade lasts, in client cycles |

## Seen a wrong splat?

The combat style icon is worked out from what hit you - the projectile, the attacker's weapon, or
their animation - so it can guess wrong, and a splat type the plugin doesn't cover yet will fall
back to plain damage art. Both are easy to fix once I can reproduce them.

Please open an issue at
[github.com/Mark7625/runelite-external-plugins/issues](https://github.com/Mark7625/runelite-external-plugins/issues)
with:

- **Which style** you had selected, and which icon set.
- **What you were fighting**, or who was fighting you.
- **The weapon or spell** involved - the exact one, since the icon is picked from it.
- **What it showed, and what it should have been** - "melee icon on a blowpipe hit", say.
- **A screenshot** if you can. It usually answers all of the above at once.

## Good to know

The game only loads its hitsplat graphics when you log in, so if you turn the plugin on while you're
already in game you'll get a chat message asking you to relog. Turning it off is the same - the old
splats come back on your next login.

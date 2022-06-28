# HQ Item Icons

Sharper, high quality item icons. Renders each item's 3D model with antialiasing instead of
using the game's default icon, in your inventory, bank, and equipment.

## Before / After

<table>
<tr>
<th>Before</th>
<th>After</th>
</tr>
<tr>
<td><img src="docs/off_bank.png" alt="Default item icons" width="400"></td>
<td><img src="docs/on_bank.png" alt="HQ item icons" width="400"></td>
</tr>
<tr>
<td><img src="docs/off_inventory.png" alt="Default item icons close-up" width="180"></td>
<td><img src="docs/on_inventory.png" alt="HQ item icons close-up" width="180"></td>
</tr>
</table>

## Config

- **Icon cache storage** — `Memory` (default) or `Disk`. Disk keeps rendered icons between
  client restarts so they don't need to be re-rendered.
- **Icon quality** — `Low`, `Medium` (default), or `High`. Controls how much antialiasing is
  used when rendering each icon; higher quality looks smoother but takes longer to render.

## Credit

Original idea by Maiz.

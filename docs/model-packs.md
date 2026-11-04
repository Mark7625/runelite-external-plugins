# Custom item-icon model packs (prototype)

Model packs supply **actual replacement geometry**, not just camera settings. They affect item
icons rendered by HQ Item Icons, using its existing compositing for menus, quantity text,
selection outlines and dragging. A pack does not replace models worn by players or drawn in
the world. This is a proof of concept: in-client visual verification is still required.

## Trying a pack

1. Enable HQ Item Icons and **Edit icons in the side panel**.
2. Open its sidebar and select **Models → Import model pack**. Select the ZIP.
3. Mapped items use the replacement model; other items retain normal HQ rendering. Import
   replaces the one active pack, rather than merging assets from multiple packs.
4. Search for a mapped item in the **Icon** tab (or use the normal edit menu) to adjust its
   camera. **Export model pack** includes the original meshes/textures and your saved camera
   settings for mapped items, so the resulting ZIP can be shared directly.
5. **Remove model pack** restores normal models. Imported packs are copied to the plugin's data
   directory and loaded on the next startup; the original ZIP need not stay in place.

The existing **Import / Export** buttons under Icon/Groups still exchange settings only.
Pack operations have their own buttons under Models. A failed import keeps the previous pack.

To build the included original crystal example:

```shell
gradle exampleModelPack
```

This creates `build/distributions/example-model-pack.zip`, replacing the abyssal whip's icon
(4151) with a blue crystal. Change the ID in the manifest to try another item. The crystal is
original geometry under the repository's license; it contains no exported game assets.

## Pack layout

ZIP the **contents** of the pack folder, with `pack.json` directly at the ZIP root:

```text
pack.json
models/my-item.obj
textures/my-item.png       # optional
```

Minimal `pack.json`:

```json
{
  "formatVersion": 1,
  "name": "My item icons",
  "author": "Your name",
  "items": {
    "4151": {
      "model": "models/my-item.obj",
      "color": "#b0b0b0",
      "camera": {"pitch": 0, "yaw": 0, "roll": 0, "zoom": 2200}
    }
  }
}
```

Mappings are exact item IDs. A mapped ID replaces its whole icon, regardless of quantity.
Notes and placeholders require their own mappings; an unmapped note uses the normal HQ
note composition, even if its underlying base item has a pack mapping. The mapping contains
geometry and presentation only: names, actual item identity and actions stay with the game.

Each mapping accepts:

| Field | Meaning / default |
| --- | --- |
| `model` | Required OBJ path relative to ZIP root |
| `color` | `#RRGGBB` material colour for an untextured mesh; default `#b0b0b0` |
| `texture` | Optional PNG path; replaces the material colour |
| `scrollDirection` | 0 (still), 1/2 (vertical opposite directions), 3/4 (horizontal opposite directions) |
| `scrollSpeed` | 0 (still), or 1–16 texture pixels per client cycle |
| `camera.pitch/yaw/roll` | 0–2047 angle units per turn; default 0 |
| `camera.zoom` | Camera distance, 256–20000; larger means smaller icon; default 2200 |
| `camera.offsetX/offsetY` | Pan, -1000–1000; default 0 |
| `camera.scaleX/scaleY/scaleZ` | Per-axis scale, 128 = 100%, range 1–512 |

The mesh is centred and normalized to a longest-axis extent of 100 units. This makes camera
defaults predictable across authoring applications and export units. Use Y-up OBJ coordinates;
the loader flips Y for the icon renderer. Faces are double-sided and flat-lit. Only geometry
and UVs are read from OBJ; MTL files, vertex normals and smoothing groups do not affect the
render. A single PNG can contain an atlas for multiple face colours/material appearances.

PNG textures must be square powers of two, from 8×8 to 1024×1024. UVs repeat in both axes and
must be present on every textured face. PNG alpha is a cutout: below 128 is transparent,
otherwise opaque. Translucent materials and PBR/shader graphs are not supported. Scrolling
textures use HQ's existing animation-frame cache and obey **Animate textures**. Skinning,
keyframed geometry and whole-icon rotation animations are not part of this format.

If **Custom icon rotations** is enabled, existing saved editor settings take precedence over a
pack's camera defaults. Reset that item's camera in the editor to use the pack defaults, or
turn off Custom icon rotations to ignore saved settings. Export includes saved settings for
mapped IDs only; unrelated settings/groups/presets are not added to the pack. Export validates
the result, including the documented camera bounds, before writing it.

Editor settings currently remain per item (the existing settings store), not per pack. A
camera edited for a custom model also remains saved after removing the pack; reset that item
or disable Custom icon rotations if you want its native default framing again.

## Exporting from Blender

1. Model your item, or prepare an original model you have permission to share.
2. Apply transforms and triangulate the mesh (a Triangulate modifier is fine).
3. For a texture, unwrap UVs and bake the desired material appearance to one square PNG atlas.
4. Export the selected mesh as **Wavefront OBJ**, with UVs, triangulated faces and **Y Up**.
   Enable **Apply Modifiers** if needed. Exported MTL references are ignored.
5. Put the OBJ and optional PNG into the folder above, edit `pack.json`, and ZIP the contents.
6. Import it, adjust framing using the existing icon editor, then export the pack to share it.

## Implementation / limits

Import parses all files on a dedicated worker and publishes only a fully validated pack on
the client thread. No files are extracted from the ZIP. Paths must be pack-relative, without
parent traversal. Limits: 16 MiB archive, 32 MiB expanded, 4 MiB per file, 512 archive entries,
256 item mappings, 4096 vertices/8192 triangles per mesh, 50000 mapped triangles per pack,
and four million mapped texture pixels (a texture counts once per item mapping).

Both in-memory and disk-cache selection change with the pack's SHA-256 content fingerprint.
Installing, changing or removing a pack cannot pick up another pack's cached frames.
Unmapped items keep the game's model capture and silhouette fitting; custom models instead
use explicit pack/editor cameras because their silhouettes need not match the game's icon.

This imports mesh data into HQ's renderer. It does not import another plugin's Java rendering
code or promise identical lighting, projection or outlines to another renderer.

## Verification

```shell
gradle test exampleModelPack
```

Before treating this prototype as ready, manually check:

- Import the crystal pack: mapped item changes, unmapped items retain their normal icons.
- Inspect inventory, bank and equipment; check stacks, hover menus, clipping, item selection,
  dragging and existing overlays with both GPU and software UI rendering.
- Rotate/pan/zoom the custom mesh in the editor; reset uses the pack defaults.
- Export after editing, remove, then import the export and confirm the same model/framing.
- With Disk cache enabled, switch between two packs, remove a pack and restart the client.
  Confirm no stale model/frame remains, including after a render was still in progress.
- Import a malformed ZIP/missing model: the previous pack remains active, with an error shown.
- Try a UV-textured pack, PNG cutouts and scrolling textures, including Animate textures off.
- Disable/re-enable the plugin while loading a pack; no stale editor or late publication.

Use the [RuneLite Jagex Accounts development-client login instructions](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
if needed. These in-game checks require a person; automated parser/render tests do not prove
the client compositing is visually correct.

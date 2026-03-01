# F2P Utilities

A RuneLite plugin for Free-to-Play players that highlights members-only items and areas with customizable visual overlays. Helps you quickly identify what you can and can't use while playing on F2P worlds.

![img.png](preview.png)

## Features

### Item Overlay

Visual indicators for members-only items in your inventory, equipment, and bank:

| Mode | Description |
|------|-------------|
| **Black and White** | Converts members items to grayscale |
| **Outline** | Draws a colored outline around members items |
| **Fill** | Fills members items with a colored overlay |

- **Item icon** — Optional icon prefix for members item names (0–390). Use `-1` to disable.
- **Exclude items** — Right-click items while holding your configured keybind to exclude them from the overlay. Access via **F2P Utilities** → **Exclude from overlay** / **Include in overlay** in the context menu.

### World Map

- Grayscale overlay on members-only areas outside the F2P region
- Normal-colored F2P area with customizable border
- Override areas for special cases (e.g. members areas within F2P, non-members areas outside F2P)
- Configurable border color, thickness, fill color, and alpha

## Configuration

### Item Overlay Settings

| Option | Description |
|--------|-------------|
| **Overlay Active** | When to show overlays: Always, Never, or Free Worlds Only |
| **Item Icon** | Icon ID for members item names (0–390, -1 = none) |
| **Item Mode** | Black and White, Outline, or Fill |
| **Item Overlay Color** | Color for outline and fill modes |
| **Item Overlay Alpha** | Transparency (0–255) |
| **Exclude Keybind** | Hold and right-click items to exclude/include from overlay |

### World Map Settings

| Option | Description |
|--------|-------------|
| **Show Map Overlay** | Toggle the world map overlay |
| **Border Color** | Color of the F2P area border |
| **Border Thickness** | 1–10 |
| **Fill Color** | Grayscale overlay fill color |
| **Overlay Alpha** | Transparency (0–255) |

## Icon Reference

Available icon IDs for the Item Icon setting (0–390):

![Icon 0](https://user-images.githubusercontent.com/72366279/177834980-134f339b-ca2f-4a0f-92f8-00c6956a9a49.png)
![Icon 1](https://user-images.githubusercontent.com/72366279/177834982-e6f124d3-9112-496a-be8e-1e8c8ebfc8a2.png)
![Icon 2](https://user-images.githubusercontent.com/72366279/177834983-783e9f0a-b1b7-4964-a62c-eb21c0d07411.png)
![Icon 3](https://user-images.githubusercontent.com/72366279/177835018-897577be-b3de-4f6d-948f-bfdb8bf01637.png)
![Icon 4](https://user-images.githubusercontent.com/72366279/177835021-245c2e3a-86a4-40d5-8611-fa34b3b9e308.png)
![Icon 5](https://user-images.githubusercontent.com/72366279/177835022-26cb32ae-db89-49d7-835d-3aef4b1b91e5.png)

## Support

- **Author**: Mark7625
- **Support**: [GitHub](https://github.com/Mark7625/runelite-external-plugins/tree/f2p-highlight)

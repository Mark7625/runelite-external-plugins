# Remastered XP Globes

Remakes 2011 like xp and level up orbs.

<img width="361" height="324" alt="image" src="https://github.com/user-attachments/assets/4921af00-721e-4d9e-9d30-fa0ea4f002ac" />

## Features

### XP Globes Overlay
- **Animated progress arcs** showing your XP progress toward the next level
- **Skill icons** displayed inside each globe
- **Hover tooltips** with detailed XP information (XP left, actions left, XP/hour)
- **Percentage display** on hover showing progress to next level
- **Smooth sliding animation** when globes are added or removed (optional)
- **Customizable appearance**: scale, spacing, duration, fade effects

### XP Drop Popups
- **Floating XP popups** displayed at the center of the screen when you gain experience
- **Animated movement** as popups rise and fade
- **Optional skill icons** shown next to the XP amount
- **Customizable**: speed, font color, scale, offset, and background box for visibility

### Level-Up Popups
- **Classic 2011-style level-up notifications** with animated globe
- **Gold orbs** for level 99+ achievements
- **Milestone messages** showing:
  - Quest requirements met
  - All quest requirements completed
  - Skill guide unlocks (new items, activities)
- **Customizable display duration** and fade effects
- **Scalable size** to fit your preferences

### Custom sprites folder

You can override the built-in sprites by setting **Custom sprites folder** in the plugin config. Use the same layout and file names as below. Only images with the exact sizes listed are loaded; anything else is ignored and the default sprite is used.

| Path | Size (W×H) |
|------|------------|
| `globe.png` | 219 × 210 |
| `arc.png` | 219 × 210 |
| `level_up_silver/left.png` | (must match internal) |
| `level_up_silver/middle.png` | (must match internal) |
| `level_up_silver/right.png` | (must match internal) |
| `level_up_silver/level_up_0.png` … `level_up_5.png` | 144 × 98 |
| `level_up_gold/left.png` | (must match internal) |
| `level_up_gold/middle.png` | (must match internal) |
| `level_up_gold/right.png` | (must match internal) |
| `level_up_gold/level_up_0.png` … `level_up_5.png` | 144 × 98 |
| `numbers/0.png` … `numbers/9.png` | 33 × 48 |

**Example folder structure:**

```
your-custom-folder/
├── globe.png
├── arc.png
├── level_up_silver/
│   ├── left.png
│   ├── middle.png
│   ├── right.png
│   ├── level_up_0.png
│   ├── level_up_1.png
│   ├── level_up_2.png
│   ├── level_up_3.png
│   ├── level_up_4.png
│   └── level_up_5.png
├── level_up_gold/
│   ├── left.png
│   ├── middle.png
│   ├── right.png
│   ├── level_up_0.png
│   └── … level_up_5.png
└── numbers/
    ├── 0.png
    ├── 1.png
    └── … 9.png
```

If any file is missing or has the wrong size, the plugin uses the internal sprite for that asset.

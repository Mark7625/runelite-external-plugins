# Plugin API

Other plugins can talk to HQ Item Icons with RuneLite's
[`PluginMessage`](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/events/PluginMessage.java),
posted on the event bus under the `hq-item-icons` namespace. Neither plugin needs to depend on
the other, and nothing breaks if this plugin isn't installed — the messages simply go unheard.

## Messages you can send

### `update-icon`

Tells HQ Item Icons that an item's icon is out of date, so it renders again from scratch. Useful
if your plugin changes something the icon is derived from, such as an item's model or colours.

```java
Map<String, Object> data = new HashMap<>();
data.put("itemId", ItemID.ABYSSAL_WHIP);
eventBus.post(new PluginMessage("hq-item-icons", "update-icon", data));
```

| Key | Type | Required | Meaning |
| --- | --- | --- | --- |
| `itemId` | any `Number` | No | The item to update. Omit to update every item. |

Omit `itemId` to update everything:

```java
eventBus.post(new PluginMessage("hq-item-icons", "update-icon"));
```

### `update-icons`

The same thing for several items at once, which is cheaper than a message each.

```java
Map<String, Object> data = new HashMap<>();
data.put("itemIds", Set.of(ItemID.ABYSSAL_WHIP, ItemID.DRAGON_SCIMITAR));
eventBus.post(new PluginMessage("hq-item-icons", "update-icons", data));
```

| Key | Type | Required | Meaning |
| --- | --- | --- | --- |
| `itemIds` | any `Collection` of `Number` | No | The items to update. Omit to update every item. |

Any `Collection` works — `List`, `Set`, or anything else. An empty one updates nothing, as does
one holding no numbers; only leaving `itemIds` out entirely updates everything, so a malformed
message can't clear the whole cache by accident. Entries that aren't numbers are ignored rather
than rejecting the message.

### Both

Both the in-memory cache and the saved image on disk are dropped for the items named, so the next
time each is drawn it gets rendered fresh. You can post from any thread; the ids are copied
immediately, the work is handed to the client thread, and the disk cleanup runs in the
background. Updating an item that was never cached is a no-op, not an error.

## Messages you can listen for

HQ Item Icons announces itself, so you can tell whether it's running without depending on it.
Both carry the plugin's name.

| Name | Data | Posted when |
| --- | --- | --- |
| `startup` | `plugin` — `"HQ Item Icons"` | The plugin has finished starting up |
| `shutdown` | `plugin` — `"HQ Item Icons"` | The plugin has shut down |

```java
@Subscribe
public void onPluginMessage(PluginMessage event) {
    if (!"hq-item-icons".equals(event.getNamespace())) {
        return;
    }
    switch (event.getName()) {
        case "startup":
            // HQ Item Icons is now running
            break;
        case "shutdown":
            // ...and has now stopped
            break;
    }
}
```

If your plugin starts first, you'll get `startup` when this one does. If this one is already
running when yours starts, you won't get a `startup` for it — send `update-icon` / `update-icons`
anyway if that's all you need, since it's harmless either way.

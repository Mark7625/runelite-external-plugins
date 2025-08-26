package io.mark.runecast.resourcepacks;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

import io.mark.runecast.RuneCastPlugin;
import io.mark.runecast.gui.components.MessagePanel;
import io.mark.runecast.resourcepacks.data.Manifest;
import io.mark.runecast.resourcepacks.impl.DefaultResourcePack;
import io.mark.runecast.resourcepacks.impl.FileResourcePack;
import io.mark.runecast.utils.Props;
import io.mark.runecast.utils.ResourcePath;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

import static io.mark.runecast.utils.ResourcePath.path;

@Singleton
@Slf4j
public class ResourcePackManager {

    public static ResourcePath RESOURCE_PACK_DIR = Props.getFolder("runecast", () -> path(new File(RuneLite.RUNELITE_DIR,"runecast").getPath()));

    private static final FileFilter RESOURCE_PACK_FILTER = file ->
            file.isFile() || file.isDirectory() && (new File(file, "runecast.properties")).isFile();

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private RuneCastPlugin plugin;

    @Getter
    private ArrayList<AbstractResourcePack> installedPacks = new ArrayList<>();

    @Getter
    private final HashMap<String, Manifest> downloadablePacks = new HashMap<>();

    @Getter
    private MessagePanel statusMessage;

    private long lastCheckForUpdates;

    public void startUp() {
        if (RESOURCE_PACK_DIR.exists()) {
            for (File path : Objects.requireNonNull(RESOURCE_PACK_DIR.toFile().listFiles(RESOURCE_PACK_FILTER))) {
                AbstractResourcePack pack = new FileResourcePack(path);
                pack.setNeedsUpdating(false);
                pack.setDevelopmentPack(true);

                if (pack.isValid()) {
                    installedPacks.add(pack);
                }
            }
        }

        installedPacks.add(new DefaultResourcePack(path(RuneCastPlugin.class, "default")));
    }

    public void removeResourcePack(String internalName) {
        installedPacks.removeIf(p -> p.getManifest().getInternalName().equals(internalName));
        plugin.getSidebar().refresh();
    }

    public void shutDown() {
        installedPacks.clear();
    }

    public AbstractResourcePack getInstalledPack(String internalName) {
        for (var pack : installedPacks)
            if (pack.getManifest().getInternalName().equals(internalName))
                return pack;
        return null;
    }

    public ResourcePath locateFile(String... parts) {
        for (AbstractResourcePack pack : installedPacks) {
            var path = pack.path.resolve(parts);
            if (path.exists())
                return path;
        }

        return null;
    }
}
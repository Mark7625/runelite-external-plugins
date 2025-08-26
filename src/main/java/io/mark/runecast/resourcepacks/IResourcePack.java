package io.mark.runecast.resourcepacks;

import io.mark.runecast.resourcepacks.data.Manifest;
import io.mark.runecast.utils.ResourcePath;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public interface IResourcePack
{
    InputStream getInputStream(String... parts) throws IOException;

    ResourcePath getResource(String... parts);

    Manifest getManifest();

    BufferedImage getPackImage();

    boolean hasPackImage();

    String getPackName();
}
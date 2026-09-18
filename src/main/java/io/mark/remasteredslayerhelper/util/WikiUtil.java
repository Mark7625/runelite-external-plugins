package io.mark.remasteredslayerhelper.util;
import io.mark.remasteredslayerhelper.ui.components.Tab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class WikiUtil {

    public static JButton createLinkButton(String text, final String url) {
        JButton button = new JButton(text);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setFocusPainted(false);

        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openWebpage(url);
            }
        });

        return button;
    }

    private static void openWebpage(String url) {
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            try {
                desktop.browse(new URI(url));
            } catch (IOException | URISyntaxException e) {
                e.printStackTrace();
                // Handle the error according to your needs
                // For example, show a dialog to the user with the error message
                JOptionPane.showMessageDialog(null,
                        "Failed to open the URL. Please check your system settings.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            // Handle the case where the desktop is not supported
            JOptionPane.showMessageDialog(null,
                    "Desktop is not supported. Unable to open the browser.",
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    public static String getWikiUrl(String type, String name) {
        String baseUrl = "https://oldschool.runescape.wiki/w/";
        String pattern = "_(.+)$"; // Matches an underscore followed by anything at the end
        switch (type.toLowerCase()) {
            case "none":
                return "about:blank";
            case "map location":
            case "items needed":
            case "monster attack style":
            case "slayer master":
                return baseUrl + name.replace(" ", "_").replaceFirst(pattern, "");
            case "monsters attributes":
                return baseUrl + name + "_(attribute)";
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }
    }
}


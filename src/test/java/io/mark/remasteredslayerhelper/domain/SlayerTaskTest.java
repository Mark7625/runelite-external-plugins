package io.mark.remasteredslayerhelper.domain;

import io.mark.remasteredslayerhelper.data.SlayerMaster;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class SlayerTaskTest {

    private SlayerTask slayerTask;

    @Before
    public void setUp() {
        // Create an example SlayerTask instance for testing
        slayerTask = new SlayerTask(
                "Test Monster",
                75,
                100,
                new String[]{"Location 1", "Location 2"},
                new Item[]{new Item("Item 1", "item1.png"), new Item("Item 2", "item2.png")},
                new String[]{"Attribute 1", "Attribute 2"},
                new String[]{"Attack Style 1", "Attack Style 2"},
                new String[]{"Alternative 1", "Alternative 2"},
                List.of(SlayerMaster.CHAELDAR, SlayerMaster.NIEVE),
                Map.of(Skill.SLAYER, 75),
                Map.of()
        );
    }

    @Test
    public void testGetMonsterLowerCase() {
        // Verify that getMonsterLowerCase returns the monster name in lowercase
        assertEquals("test monster", slayerTask.getMonsterLowerCase());
    }

    @Test
    public void testGetMonsterFileName() {
        // Verify that getMonsterFileName returns the expected file name
        assertEquals("/images/monsters/test_monster.png", slayerTask.getMonsterFileName());
    }

    @Test
    public void testGetItemsRequiredNames() {
        // Verify that getItemsRequiredNames returns the names of required items
        String[] expectedItemNames = {"Item 1", "Item 2"};
        assertArrayEquals(expectedItemNames, slayerTask.getItemsRequiredNames());
    }

    @Test
    public void testGetItemsRequiredIcons() {
        // Verify that getItemsRequiredIcons returns the icons of required items
        String[] expectedItemIcons = {"item1.png", "item2.png"};
        assertArrayEquals(expectedItemIcons, slayerTask.getItemsRequiredIcons());
    }

    @Test
    public void testToString() {
        // Verify that toString returns the expected string representation (e.g., monster name)
        assertEquals("Test Monster", slayerTask.toString());
    }
}

package io.mark.remasteredslayerhelper.domain;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ItemTest {

    private Item item;

    @Before
    public void setUp() {
        // Create an Item instance for testing
        item = new Item("Test Item", "test_icon.png");
    }

    @Test
    public void testGetName() {
        // Test the getName() method
        assertEquals("Test Item", item.getName());
    }

    @Test
    public void testGetIcon() {
        // Test the getIcon() method
        assertEquals("test_icon.png", item.getIcon());
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorWithNullName() {
        // Test that the constructor throws a NullPointerException when the name is null
        new Item(null, "test_icon.png");
    }
}

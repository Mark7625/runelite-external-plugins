package io.mark.remasteredslayerhelper.ui;

import io.mark.remasteredslayerhelper.ui.components.SearchBar;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.components.IconTextField.Icon;
import static org.mockito.Mockito.clearInvocations;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SearchBarTest {
    private IconTextField iconTextFieldMock;
    private SearchBar.OnKeyTypedHandler onKeyTypedHandlerMock;
    private SearchBar.OnClearHandler onClearHandlerMock;
    private SearchBar searchBar;

    @Before
    public void setUp() {
        iconTextFieldMock = mock(IconTextField.class);
        onKeyTypedHandlerMock = mock(SearchBar.OnKeyTypedHandler.class);
        onClearHandlerMock = mock(SearchBar.OnClearHandler.class);
        searchBar = new SearchBar(onKeyTypedHandlerMock, onClearHandlerMock, iconTextFieldMock);
    }

    @Test
    public void testKeyReleasedHandler() {
        ArgumentCaptor<KeyAdapter> keyAdapterCaptor = ArgumentCaptor.forClass(KeyAdapter.class);
        verify(iconTextFieldMock).addKeyListener(keyAdapterCaptor.capture());
        KeyAdapter keyAdapter = keyAdapterCaptor.getValue();
        assertNotNull("KeyAdapter should not be null", keyAdapter);

        when(iconTextFieldMock.getText()).thenReturn("test");

        KeyEvent keyEvent = new KeyEvent(iconTextFieldMock, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0,
                KeyEvent.VK_UNDEFINED, 't');
        keyAdapter.keyReleased(keyEvent);

        verify(onKeyTypedHandlerMock).run("test");
    }

    @Test
    public void testClearListener() {
        ArgumentCaptor<Runnable> clearListenerCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(iconTextFieldMock).addClearListener(clearListenerCaptor.capture());
        Runnable clearListener = clearListenerCaptor.getValue();
        assertNotNull("ClearListener should not be null", clearListener);

        clearInvocations(iconTextFieldMock, onClearHandlerMock);
        clearListener.run();

        verify(onClearHandlerMock).run();

        verify(iconTextFieldMock, times(1)).setIcon(Icon.SEARCH);
        verify(iconTextFieldMock, times(1)).setEditable(true);
    }

}

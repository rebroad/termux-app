package com.termux.view;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TerminalViewTest {

    @Test
    public void screenUpdateFollowsOutputWhenViewportWasAtBottom() {
        assertEquals(0, TerminalView.calculateTopRowAfterScreenUpdate(0, 12, 3, true));
    }

    @Test
    public void screenUpdatePreservesScrolledUpViewport() {
        assertEquals(-5, TerminalView.calculateTopRowAfterScreenUpdate(-2, 12, 3, false));
    }

    @Test
    public void screenUpdateClampsPreservedViewportToHistory() {
        assertEquals(-4, TerminalView.calculateTopRowAfterScreenUpdate(-3, 4, 3, false));
    }
}

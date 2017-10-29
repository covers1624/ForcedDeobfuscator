/*
 * (C) 2017 covers1624
 * All Rights Reserved
 */
package net.covers1624.forceddeobf.util;

import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.function.Consumer;

/**
 * Wrapper for a lambda because swing is retarded.
 * Created by covers1624 on 21/10/2017.
 */
public class WindowCloseListener implements WindowListener {

    private Consumer<WindowEvent> onClosing;

    public WindowCloseListener(Consumer<WindowEvent> onClosing) {
        this.onClosing = onClosing;
    }

    @Override
    public void windowClosing(WindowEvent e) {
        onClosing.accept(e);
    }

    //@formatter:off
    @Override public void windowOpened(WindowEvent e) { }
    @Override public void windowClosed(WindowEvent e) { }
    @Override public void windowIconified(WindowEvent e) { }
    @Override public void windowDeiconified(WindowEvent e) { }
    @Override public void windowActivated(WindowEvent e) { }
    @Override public void windowDeactivated(WindowEvent e) { }
    //@formatter:on
}

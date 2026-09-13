package dev.poldy.lab;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TaskProfileReaderTest {
    @Test public void acceptsBothOrientationsAtNativePanelSize() {
        assertTrue(TaskProfileReader.nativeBounds(false,1248,1972));
        assertTrue(TaskProfileReader.nativeBounds(false,1972,1248));
        assertTrue(TaskProfileReader.nativeBounds(true,2448,1848));
        assertTrue(TaskProfileReader.nativeBounds(true,1848,2448));
    }

    @Test public void rejectsScaledOrCrossPanelBounds() {
        assertFalse(TaskProfileReader.nativeBounds(false,2448,1848));
        assertFalse(TaskProfileReader.nativeBounds(true,1248,1972));
        assertFalse(TaskProfileReader.nativeBounds(false,1200,1920));
    }
}

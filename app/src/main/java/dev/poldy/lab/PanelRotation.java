package dev.poldy.lab;

/** Preserves user-selected rotation modes when Samsung swaps the logical display IDs. */
final class PanelRotation {
    private String inner, outer;

    boolean ready() { return inner != null && outer != null; }

    void capture(boolean primaryInner, String display0, String display1) {
        display0 = normalize(display0);
        display1 = normalize(display1);
        if (primaryInner) { inner = display0; outer = display1; }
        else { outer = display0; inner = display1; }
    }

    void observeActive(boolean primaryInner, String display0) {
        display0 = normalize(display0);
        if (primaryInner) inner = display0;
        else outer = display0;
    }

    String mode(boolean targetInner, int displayId) {
        if (!ready() || (displayId != 0 && displayId != 1))
            throw new IllegalStateException("Rotation preferences are unavailable");
        boolean panelInner = displayId == 0 ? targetInner : !targetInner;
        return panelInner ? inner : outer;
    }

    String panelMode(boolean panelInner) {
        if (!ready()) throw new IllegalStateException("Rotation preferences are unavailable");
        return panelInner ? inner : outer;
    }

    void clear() { inner = null; outer = null; }

    static String normalize(String mode) {
        String value = mode == null ? "" : mode.trim().replaceAll("\\s+", " ");
        if (value.equals("free") || value.matches("lock [0-3]")) return value;
        throw new IllegalArgumentException("Unexpected rotation mode: " + value);
    }
}

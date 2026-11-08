package org.levimc.launcher.core.mods.inbuilt.overlay;

/** Ghost Client: a HUD widget managed by InbuiltOverlayManager (text widgets, crosshair, ...). */
public interface ShadowHudElement {
    String getModId();

    void show(int startX, int startY);

    void hide();

    void setVisibility(int visibility);

    void setHudEditorMode(boolean active);

    void applyConfigurationChanges();

    void updatePosition(int x, int y);
}

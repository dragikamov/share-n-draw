package com.example.terz99.whiteboard;

import android.graphics.Path;

/**
 * Fingerpath for drawing.
 */
public class FingerPath {

    public int color;
    public Path path;

    public FingerPath(int color, Path path) {
        this.color = color;
        this.path = path;
    }
}

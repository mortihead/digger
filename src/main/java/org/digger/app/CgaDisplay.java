package org.digger.app;

import java.awt.*;
import java.awt.image.*;

/**
 * Software emulation of the IBM CGA (Color Graphics Adapter) video output.
 *
 * <p>Maintains a 320×200 pixel buffer where each pixel is 2-bit (4 colors).
 * Two CGA palette sets (normal and intensified) are provided, each with an
 * AWT {@link MemoryImageSource} for real-time rendering.
 *
 * <p>Pixel storage: internally uses 1 byte per pixel (values 0-3) for
 * convenient masking. The original CGA packed 4 pixels per byte; packing/unpacking
 * is handled in {@link #drawSprite}, {@link #readSpritePixels}, etc.
 */
class CgaDisplay {

    private final Digger dig;

    /** AWT images for the two palette intensities. */
    final Image[] image = new Image[2];
    Image currentImage;

    /** Pixel update sources for the two palette intensities. */
    final MemoryImageSource[] source = new MemoryImageSource[2];
    MemoryImageSource currentSource;

    /** Display dimensions (CGA mode 4: 320×200). */
    final int width = 320, height = 200;
    private final int size = width * height;

    /** Unpacked pixel buffer: 1 byte per pixel, values 0-3. */
    int[] pixels;

    /** RGB palettes: [paletteSet][rgbChannel][colorIndex]. Index 0 is unused padding. */
    final byte[][][] palettes = {
            {{0, (byte) 0x00, (byte) 0xAA, (byte) 0xAA},   // normal: green/red/brown/black
                    {0, (byte) 0xAA, (byte) 0x00, (byte) 0x54},
                    {0, (byte) 0x00, (byte) 0x00, (byte) 0x00}},
            {{0, (byte) 0x54, (byte) 0xFF, (byte) 0xFF},   // intensified: cyan/magenta/white/grey
                    {0, (byte) 0xFF, (byte) 0x54, (byte) 0xFF},
                    {0, (byte) 0x54, (byte) 0x54, (byte) 0x54}}};

    CgaDisplay(Digger d) {
        dig = d;
    }

    /** Clears the entire screen to black (color 0) and refreshes the display. */
    void clearScreen() {
        for (int i = 0; i < size; i++)
            pixels[i] = 0;
        currentSource.newPixels();
    }

    /** Returns current system time in milliseconds (replaces hardware timer read). */
    long getCurrentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * Reads a rectangular block of pixels from the screen into a packed short array.
     * Each short contains 4 CGA pixels (2 bits each), packed MSB-first.
     *
     * @param x  left edge (aligned to 4-pixel boundary)
     * @param y  top edge
     * @param p  output packed pixel array
     * @param w  width in packed units (each unit = 4 pixels)
     * @param h  height in rows
     */
    void readSpritePixels(int x, int y, short[] p, int w, int h) {
        int src = 0;
        int dest = y * width + (x & 0xfffc);
        for (int i = 0; i < h; i++) {
            int d = dest;
            for (int j = 0; j < w; j++) {
                p[src++] = (short) ((((((pixels[d] << 2) | pixels[d + 1]) << 2) | pixels[d + 2]) << 2) | pixels[d + 3]);
                d += 4;
                if (src == p.length)
                    return;
            }
            dest += width;
        }
    }

    /**
     * Reads a single packed pixel value at the given coordinates.
     * Returns 4 CGA pixels packed into a short (2 bits each).
     */
    int getPixel(int x, int y) {
        int ofs = width * y + (x & 0xfffc);
        return (((((pixels[ofs] << 2) | pixels[ofs + 1]) << 2) | pixels[ofs + 2]) << 2) | pixels[ofs + 3];
    }

    /** No-op (in original: initialized CGA mode 4 hardware registers). */
    void init() {
    }

    /**
     * Switches between normal (0) and intensified (1) palette.
     * This maps to the CGA color-select register (port 3D9h, bit 5).
     */
    void setIntensity(int inten) {
        currentSource = source[inten & 1];
        currentImage = image[inten & 1];
        currentSource.newPixels();
    }

    /** No-op (in original: selected CGA palette 0 or 1 via port 3D9h). */
    void setPalette(int pal) {
    }

    /**
     * Draws a packed sprite onto the screen without transparency mask.
     * Overwrites all pixels in the target rectangle.
     *
     * @param x  left edge (aligned to 4-pixel boundary)
     * @param y  top edge
     * @param p  packed pixel data (4 pixels per short)
     * @param w  width in packed units
     * @param h  height in rows
     */
    void drawSprite(int x, int y, short[] p, int w, int h) {
        int src = 0;
        int dest = y * width + (x & 0xfffc);
        for (int i = 0; i < h; i++) {
            int d = dest;
            for (int j = 0; j < w; j++) {
                short px = p[src++];
                pixels[d + 3] = px & 3;
                px >>= 2;
                pixels[d + 2] = px & 3;
                px >>= 2;
                pixels[d + 1] = px & 3;
                px >>= 2;
                pixels[d] = px & 3;
                d += 4;
                if (src == p.length)
                    return;
            }
            dest += width;
        }
    }

    /**
     * Draws a masked sprite onto the screen. Transparent pixels (mask bits = 1)
     * are skipped, preserving the background.
     *
     * @param ch index into {@link CgaGrafx#cgatable}: sprite at [ch*2], mask at [ch*2+1]
     */
    void drawSpriteMasked(int x, int y, int ch, int w, int h) {
        short[] spr = CgaGrafx.cgatable[ch * 2];
        short[] msk = CgaGrafx.cgatable[ch * 2 + 1];
        int src = 0;
        int dest = y * width + (x & 0xfffc);
        for (int i = 0; i < h; i++) {
            int d = dest;
            for (int j = 0; j < w; j++) {
                short px = spr[src];
                short mx = msk[src];
                src++;
                if ((mx & 3) == 0)
                    pixels[d + 3] = px & 3;
                px >>= 2;
                if ((mx & (3 << 2)) == 0)
                    pixels[d + 2] = px & 3;
                px >>= 2;
                if ((mx & (3 << 4)) == 0)
                    pixels[d + 1] = px & 3;
                px >>= 2;
                if ((mx & (3 << 6)) == 0)
                    pixels[d] = px & 3;
                d += 4;
                if (src == spr.length || src == msk.length)
                    return;
            }
            dest += width;
        }
    }

    /**
     * Decodes and draws the title screen from RLE-compressed CGA data.
     * Handles the CGA interleaved memory layout (even/odd scanline banks).
     */
    void drawTitleScreen() {
        int src = 0, dest = 0;
        while (true) {
            if (src >= CgaGrafx.cgatitledat.length)
                break;
            int b = CgaGrafx.cgatitledat[src++], l, c;
            if (b == 0xfe) {
                l = CgaGrafx.cgatitledat[src++];
                if (l == 0)
                    l = 256;
                c = CgaGrafx.cgatitledat[src++];
            } else {
                l = 1;
                c = b;
            }
            for (int i = 0; i < l; i++) {
                int px = c, adst;
                if (dest < 32768)
                    adst = (dest / 320) * 640 + dest % 320;
                else
                    adst = 320 + ((dest - 32768) / 320) * 640 + (dest - 32768) % 320;
                pixels[adst + 3] = px & 3;
                px >>= 2;
                pixels[adst + 2] = px & 3;
                px >>= 2;
                pixels[adst + 1] = px & 3;
                px >>= 2;
                pixels[adst + 0] = px & 3;
                dest += 4;
                if (dest >= 65535)
                    break;
            }
            if (dest >= 65535)
                break;
        }
    }

    /** Draws a character without immediate display refresh. */
    void drawChar(int x, int y, int ch, int color) {
        drawChar(x, y, ch, color, false);
    }

    /**
     * Draws a single character from the CGA font bitmap.
     * Characters are 12×12 pixels, looked up from {@link Alphabet#ascii2cga}.
     *
     * @param x     left edge in pixels
     * @param y     top edge in pixels
     * @param ch    ASCII character code
     * @param color CGA color index (0-3)
     * @param refresh  true to immediately update the display region
     */
    void drawChar(int x, int y, int ch, int color, boolean refresh) {
        int dest = x + y * width, ofs = 0, c = color & 3;
        ch -= 32;
        if ((ch < 0) || (ch > 0x5f))
            return;
        short[] chartab = Alphabet.ascii2cga[ch];
        if (chartab == null)
            return;
        for (int i = 0; i < 12; i++) {
            int d = dest;
            for (int j = 0; j < 3; j++) {
                int px = chartab[ofs++];
                pixels[d + 3] = px & c;
                px >>= 2;
                pixels[d + 2] = px & c;
                px >>= 2;
                pixels[d + 1] = px & c;
                px >>= 2;
                pixels[d] = px & c;
                d += 4;
            }
            dest += width;
        }
        if (refresh)
            currentSource.newPixels(x, y, 12, 12);
    }
}

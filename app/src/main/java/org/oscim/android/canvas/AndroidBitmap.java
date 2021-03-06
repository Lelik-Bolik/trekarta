/*
 * Copyright 2013 Hannes Janetzek
 * Copyright 2016 Longri
 * Copyright 2016-2018 devemux86
 *
 * This file is part of the OpenScienceMap project (http://www.opensciencemap.org).
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.oscim.android.canvas;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import org.oscim.backend.CanvasAdapter;
import org.oscim.utils.GraphicUtils;
import org.oscim.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static android.graphics.Bitmap.Config.ARGB_8888;

public class AndroidBitmap implements org.oscim.backend.canvas.Bitmap {
    static final Logger log = LoggerFactory.getLogger(AndroidBitmap.class);

    Bitmap mBitmap;

    public AndroidBitmap(InputStream inputStream) {
        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
        try {
            GLUtils.getType(bitmap);
        } catch (IllegalArgumentException e) {
            bitmap = bitmap.copy(ARGB_8888, false);
        }
        mBitmap = bitmap;
    }

    public AndroidBitmap(InputStream inputStream, int width, int height, int percent, int color) {
        this(inputStream);
        float[] newSize = GraphicUtils.imageSize(getWidth(), getHeight(), CanvasAdapter.getScale(), width, height, percent);
        scaleTo((int) newSize[0], (int) newSize[1]);
        if (color != 0) {
            Paint paint = new Paint();
            paint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            Bitmap bitmap = Bitmap.createBitmap((int) newSize[0], (int) newSize[1], ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawBitmap(mBitmap, 0, 0, paint);
            mBitmap = bitmap;
        }
    }

    @Override
    public boolean isValid() {
        return mBitmap != null;
    }

    /**
     * @param format ignored always ARGB8888
     */
    public AndroidBitmap(int width, int height, int format) {
        mBitmap = android.graphics.Bitmap
                .createBitmap(width, height, ARGB_8888);
    }

    public AndroidBitmap(android.graphics.Bitmap bitmap) {
        mBitmap = bitmap;
    }

    @Override
    public int getWidth() {
        return mBitmap.getWidth();
    }

    @Override
    public int getHeight() {
        return mBitmap.getHeight();
    }

    @Override
    public int[] getPixels() {
        int width = getWidth();
        int height = getHeight();
        int[] colors = new int[width * height];
        mBitmap.getPixels(colors, 0, width, 0, 0, width, height);
        return colors;
    }

    @Override
    public void eraseColor(int color) {
        //int a = android.graphics.Color.TRANSPARENT;
        mBitmap.eraseColor(color);
    }

    @Override
    public void uploadToTexture(boolean replace) {
        if (mBitmap.isRecycled()) {
            log.error("Attempted to upload recycled bitmap to texture");
            return;
        }
        int format = GLUtils.getInternalFormat(mBitmap);
        int type = GLUtils.getType(mBitmap);

        if (replace)
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0,
                    mBitmap, format, type);
        else
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, format,
                    mBitmap, type, 0);
    }

    @Override
    public void recycle() {
        if (mBitmap == null)
            return;

        mBitmap.recycle();
    }

    @Override
    public byte[] getPngEncodedData() {
        ByteArrayOutputStream outputStream = null;
        try {
            outputStream = new ByteArrayOutputStream();
            mBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            return outputStream.toByteArray();
        } finally {
            IOUtils.closeQuietly(outputStream);
        }
    }

    @Override
    public void scaleTo(int width, int height) {
        if (getWidth() != width || getHeight() != height) {
            // The effect of the filter argument to createScaledBitmap is not well documented in the
            // official android docs, but according to
            // http://stackoverflow.com/questions/2895065/what-does-the-filter-parameter-to-createscaledbitmap-do
            // passing true results in smoother edges, less pixelation.
            // If smoother corners improve the readability of map labels is perhaps debatable.
            mBitmap = Bitmap.createScaledBitmap(mBitmap, width, height, true);
        }
    }
}

/*
 * This is a modified version of org/apache/commons/imaging/formats/bmp/BmpImageParser.java
 * We removed all reader parts and removed the palette functionality since apparently Evolis
 * printers do not like paletted images and always expect 24 bit images.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.pretix.pretixprint.imaging;

import static org.apache.commons.imaging.ImagingConstants.PARAM_KEY_FORMAT;
import static org.apache.commons.imaging.ImagingConstants.PARAM_KEY_PIXEL_DENSITY;

import org.apache.commons.imaging.ImageWriteException;
import org.apache.commons.imaging.PixelDensity;
import org.apache.commons.imaging.common.BinaryOutputStream;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

public class BmpImageParser {
    private static final String DEFAULT_EXTENSION = ".bmp";
    private static final String[] ACCEPTED_EXTENSIONS = {DEFAULT_EXTENSION,};
    private static final byte[] BMP_HEADER_SIGNATURE = {0x42, 0x4d,};
    private static final int BI_RGB = 0;
    private static final int BI_RLE4 = 2;
    private static final int BI_RLE8 = 1;
    private static final int BI_BITFIELDS = 3;
    private static final int BITMAP_FILE_HEADER_SIZE = 14;
    private static final int BITMAP_INFO_HEADER_SIZE = 40;

    public void writeImage(final BufferedImage src, final OutputStream os, Map<String, Object> params)
            throws ImageWriteException, IOException {
        // make copy of params; we'll clear keys as we consume them.
        params = (params == null) ? new HashMap<String, Object>() : new HashMap<String, Object>(params);

        PixelDensity pixelDensity = null;

        // clear format key.
        if (params.containsKey(PARAM_KEY_FORMAT)) {
            params.remove(PARAM_KEY_FORMAT);
        }
        if (params.containsKey(PARAM_KEY_PIXEL_DENSITY)) {
            pixelDensity = (PixelDensity) params
                    .remove(PARAM_KEY_PIXEL_DENSITY);
        }
        if (!params.isEmpty()) {
            final Object firstKey = params.keySet().iterator().next();
            throw new ImageWriteException("Unknown parameter: " + firstKey);
        }

        BmpWriter writer;
        writer = new BmpWriterRgb();

        final byte[] imagedata = writer.getImageData(src);
        final BinaryOutputStream bos = new BinaryOutputStream(os, ByteOrder.LITTLE_ENDIAN);

        // write BitmapFileHeader
        os.write(0x42); // B, Windows 3.1x, 95, NT, Bitmap
        os.write(0x4d); // M

        final int filesize = BITMAP_FILE_HEADER_SIZE + BITMAP_INFO_HEADER_SIZE + // header
                // size
                4 * writer.getPaletteSize() + // palette size in bytes
                imagedata.length;
        bos.write4Bytes(filesize);

        bos.write4Bytes(0); // reserved
        bos.write4Bytes(BITMAP_FILE_HEADER_SIZE + BITMAP_INFO_HEADER_SIZE
                + 4 * writer.getPaletteSize()); // Bitmap Data Offset

        final int width = src.getWidth();
        final int height = src.getHeight();

        // write BitmapInfoHeader
        bos.write4Bytes(BITMAP_INFO_HEADER_SIZE); // Bitmap Info Header Size
        bos.write4Bytes(width); // width
        bos.write4Bytes(height); // height
        bos.write2Bytes(1); // Number of Planes
        bos.write2Bytes(writer.getBitsPerPixel()); // Bits Per Pixel

        bos.write4Bytes(BI_RGB); // Compression
        bos.write4Bytes(imagedata.length); // Bitmap Data Size
        bos.write4Bytes(pixelDensity != null ? (int) Math
                .round(pixelDensity.horizontalDensityMetres()) : 0); // HResolution
        bos.write4Bytes(pixelDensity != null ? (int) Math
                .round(pixelDensity.verticalDensityMetres()) : 0); // VResolution
        bos.write4Bytes(0); // Colors
        bos.write4Bytes(0); // Important Colors
        // bos.write_4_bytes(0); // Compression

        // write Palette
        writer.writePalette(bos);
        // write Image Data
        bos.write(imagedata);
    }

}

/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.integration.common.utils;

import static org.junit.Assert.assertTrue;

import com.google.common.truth.Truth;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Helper class to read image files.
 */
public class ImageHelper {

    public static final int RED = 0xFFFF0000;

    public static final int GREEN = 0xFF00FF00;

    public static final int BLUE = 0x00FFFF00;

    /**
     * Check the color of the first pixel in imageFile is as expected.
     */
    public static void checkImageColor(File imageFile, int expectedColor) throws IOException {
        assertTrue(
                "File '" + imageFile.getAbsolutePath() + "' does not exist.",
                imageFile.isFile());

        BufferedImage image = ImageIO.read(imageFile);
        int rgb = image.getRGB(0, 0);
        Truth.assertThat(rgb).named("color for file " + imageFile).isEqualTo(expectedColor);
    }

    /** Check the color of the first pixel in file at imagePath is as expected. */
    public static void checkImageColor(Path imagePath, int expectedColor) throws IOException {
        try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(imagePath))) {
            BufferedImage image = ImageIO.read(bis);
            int rgb = image.getRGB(0, 0);
            Truth.assertThat(rgb).named("color for path " + imagePath).isEqualTo(expectedColor);
        }
    }

    public static void checkImageColor(File folder, String fileName, int expectedColor)
            throws IOException {
        File f = new File(folder, fileName);
        checkImageColor(f, expectedColor);
    }
}

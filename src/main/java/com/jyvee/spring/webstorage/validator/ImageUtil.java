/*
 * Copyright (c) 2023 Rahim Alizada
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jyvee.spring.webstorage.validator;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@Slf4j
public final class ImageUtil {

    static {
        // noinspection SpellCheckingInspection
        System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider");
    }

    private ImageUtil() {}

    public static ImageReader getReader(final String contentType) throws IOException {
        if (ImageIO.getImageReadersByMIMEType(contentType).hasNext()) {
            return ImageIO.getImageReadersByMIMEType(contentType).next();
        }
        throw new IOException("Image reader for content type '" + contentType + "' was not found");
    }

    public static BufferedImage toBufferedImage(final byte[] bytes, final String contentType) throws IOException {
        final ImageReader imageReader = ImageUtil.getReader(contentType);
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
             ImageInputStream imageInputStream = ImageIO.createImageInputStream(byteArrayInputStream)) {
            imageReader.setInput(imageInputStream, false, false);
            return imageReader.read(0);
        } finally {
            imageReader.dispose();
        }
    }

}

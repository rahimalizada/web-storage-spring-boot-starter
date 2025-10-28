/*
 * Copyright (c) 2023-2025 Rahim Alizada
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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

class ImageUtilTest {

    @Test
    void getReader_validArgs_ok() throws IOException {
        Assertions.assertNotNull(ImageUtil.getReader("image/jpeg"));
        Assertions.assertNotNull(ImageUtil.getReader("image/png"));
    }

    @Test
    void getReader_invalidArgs_exception() {
        Assertions.assertThrows(IOException.class, () -> ImageUtil.getReader("image/invalid"));
    }

    @Test
    void toBufferedImage_validArgs_ok() throws IOException {
        final byte[] payload = Files.readAllBytes(new ClassPathResource("image.jpeg").getFile().toPath());
        final BufferedImage bufferedImage = ImageUtil.toBufferedImage(payload, "image/jpeg");

        Assertions.assertNotNull(bufferedImage);
        Assertions.assertEquals(600, bufferedImage.getWidth());
        Assertions.assertEquals(400, bufferedImage.getHeight());
    }

    @Test
    void toBufferedImage_invalidArgs_exception() throws IOException {
        final byte[] payload = Files.readAllBytes(new ClassPathResource("image.jpeg").getFile().toPath());

        Assertions.assertThrows(IOException.class, () -> ImageUtil.toBufferedImage(payload, "image/invalid"));
        Assertions.assertThrows(IOException.class,
            () -> ImageUtil.toBufferedImage("test".getBytes(StandardCharsets.UTF_8), "image/invalid"));
        Assertions.assertThrows(IOException.class,
            () -> ImageUtil.toBufferedImage("test".getBytes(StandardCharsets.UTF_8), "image/jpeg"));

    }

}

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

import com.jyvee.spring.test.webstorage.WebFileType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

class StorageImageValidatorTest {

    private static byte[] payload;

    private final StorageImageValidator validator = new StorageImageValidator();

    @BeforeAll
    static void beforeAll() throws IOException {
        payload = Files.readAllBytes(new ClassPathResource("image.jpeg").getFile().toPath());
    }

    @Test
    void validate_ValidArgs_Ok() {
        Assertions.assertTrue(this.validator.validate(WebFileType.NO_CHECK, "image/jpeg", payload).isEmpty());
        final Map<String, String> metadata = this.validator.validate(WebFileType.VALID, "image/jpeg", payload);
        Assertions.assertEquals("600", metadata.get("width"));
        Assertions.assertEquals("400", metadata.get("height"));
    }

    @Test
    void validate_InvalidPayload_Exception() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> this.validator.validate(WebFileType.VALID, "image/jpeg", "payload".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void validate_InvalidArgs_Exception() {
        Assertions.assertEquals("Image width is too small",
            Assertions
                .assertThrows(IllegalArgumentException.class,
                    () -> this.validator.validate(WebFileType.INVALID_MIN_WIDTH, "image/jpeg", payload))
                .getMessage());

        Assertions.assertEquals("Image height is too small",
            Assertions
                .assertThrows(IllegalArgumentException.class,
                    () -> this.validator.validate(WebFileType.INVALID_MIN_HEIGHT, "image/jpeg", payload))
                .getMessage());

        Assertions.assertEquals("Image width is too big",
            Assertions
                .assertThrows(IllegalArgumentException.class,
                    () -> this.validator.validate(WebFileType.INVALID_MAX_WIDTH, "image/jpeg", payload))
                .getMessage());

        Assertions.assertEquals("Image height is too big",
            Assertions
                .assertThrows(IllegalArgumentException.class,
                    () -> this.validator.validate(WebFileType.INVALID_MAX_HEIGHT, "image/jpeg", payload))
                .getMessage());
    }

}

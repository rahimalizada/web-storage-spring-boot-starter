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

import com.jyvee.spring.test.webstorage.WebFileType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;

class StorageContentTypeValidatorTest {

    private static byte[] payload;

    final StorageContentTypeValidator validator = new StorageContentTypeValidator();

    @BeforeAll
    public static void beforeAll() throws IOException {
        payload = Files.readAllBytes(new ClassPathResource("image.jpeg").getFile().toPath());
    }

    @Test
    public void validate_ValidArgs_Ok() {
        Assertions.assertTrue(this.validator.validate(WebFileType.NO_CHECK, "image/jpeg", payload).isEmpty());
        Assertions.assertTrue(this.validator.validate(WebFileType.VALID, "image/jpeg", payload).isEmpty());
    }

    @Test
    public void validate_invalidConfig_emptyMap() {
        Assertions.assertTrue(this.validator.validate(new Object(), "image/jpeg", payload).isEmpty());
        Assertions.assertTrue(this.validator.validate(new Object(), "unused", payload).isEmpty());
    }

    @Test
    public void validate_InvalidArgs_Exception() {
        Assertions.assertEquals("Content type is not allowed",
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> this.validator.validate(WebFileType.INVALID_MIN_SIZE, "image/bmp", payload))
                .getMessage());

    }

}

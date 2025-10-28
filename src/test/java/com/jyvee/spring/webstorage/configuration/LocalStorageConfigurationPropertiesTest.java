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

package com.jyvee.spring.webstorage.configuration;

import com.jyvee.spring.test.webstorage.TestApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;

@SpringBootTest(classes = TestApplication.class)
class LocalStorageConfigurationPropertiesTest {

    private static String basePath;

    @Autowired
    private LocalStorageConfigurationProperties config;

    @DynamicPropertySource
    static void setProperties(final DynamicPropertyRegistry registry) throws IOException {
        basePath = Files.createTempDirectory("").toString();
        registry.add("web-storage.local.endpoint", () -> "https://s3.url/local");
        registry.add("web-storage.local.path", () -> basePath);
    }

    @Test
    void testMethod() {
        Assertions.assertEquals(basePath, this.config.getPath().toString());
        Assertions.assertEquals("https://s3.url/local", this.config.getEndpoint().toString());
    }

}

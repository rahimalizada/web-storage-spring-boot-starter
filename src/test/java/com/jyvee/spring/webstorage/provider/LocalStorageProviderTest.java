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

package com.jyvee.spring.webstorage.provider;

import com.jyvee.spring.test.webstorage.LocalStorageProvider;
import com.jyvee.spring.webstorage.configuration.LocalStorageConfigurationProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LocalStorageProviderTest extends AbstractStorageProviderTest {

    @BeforeAll
    public void beforeAll() throws IOException {
        final Path basePath = Files.createTempDirectory("");

        final LocalStorageConfigurationProperties configurationProperties =
            new LocalStorageConfigurationProperties(URI.create("https://site.url/base"), basePath);
        setProvider(new LocalStorageProvider(configurationProperties));
    }

}

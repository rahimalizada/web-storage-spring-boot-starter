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

package com.jyvee.spring.webstorage.provider;

import com.jyvee.spring.test.webstorage.WebFile;
import lombok.Setter;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Setter
abstract class AbstractStorageProviderTest {

    private StorageProvider<@NonNull WebFile, ?> provider;

    @BeforeEach
    void beforeEach() throws IOException {
        this.provider.delete(this.provider.list("temp-path"));
        this.provider.delete(this.provider.list("_temp_path"));
        this.provider.delete("temp-path");
        this.provider.delete("_temp-path");

    }

    @SuppressWarnings("OverlyLongMethod")
    @Test
    void allMethods_validArgs_ok() throws IOException {
        final Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        final WebFile saved = saveTestFile("temp-path/test.txt");
        Assertions.assertEquals(this.provider.getConfiguration().getEndpoint() + "/temp_path/test.txt",
            saved.getUri().toString());
        Assertions.assertEquals(this.provider.getConfiguration().getStorageId(), saved.getStorageId());
        Assertions.assertEquals("temp_path/test.txt", saved.getPath());
        Assertions.assertEquals("text/plain", saved.getContentType());
        Assertions.assertEquals(4, saved.getSize());
        Assertions.assertEquals("0cbc6611f5540bd0809a388dc95a615b", saved.getChecksum());
        Assertions.assertEquals("value", saved.getMetadata().get("key"));
        Assertions.assertTrue(saved.getTimestamp().equals(now) || saved.getTimestamp().isAfter(now) && saved
            .getTimestamp()
            .equals(Instant.now()) || saved.getTimestamp().isBefore(Instant.now()));

        final WebFile loaded = this.provider.load("temp_path/test.txt"); // sanitized path
        Assertions.assertEquals(saved.getUri(), loaded.getUri());
        Assertions.assertEquals(saved.getStorageId(), loaded.getStorageId());
        Assertions.assertEquals(saved.getPath(), loaded.getPath());
        Assertions.assertEquals(saved.getContentType(), loaded.getContentType());
        Assertions.assertEquals(saved.getSize(), loaded.getSize());
        Assertions.assertEquals(saved.getChecksum(), loaded.getChecksum());
        Assertions.assertEquals(saved.getMetadata(), loaded.getMetadata());
        Assertions.assertNotNull(saved.getTimestamp());

        this.provider.move("temp_path/test.txt", "temp_path/test2.txt");
        final WebFile moved = this.provider.load("temp_path/test2.txt");
        Assertions.assertEquals(this.provider.getConfiguration().getEndpoint() + "/temp_path/test2.txt",
            moved.getUri().toString());
        Assertions.assertEquals("temp_path/test2.txt", moved.getPath());
        Assertions.assertThrows(IOException.class, () -> this.provider.load("temp_path/test.txt"));

        this.provider.copy("temp_path/test2.txt", "temp_path/test.txt");
        Assertions.assertNotNull(this.provider.load("temp_path/test.txt"));

        Assertions.assertTrue(this.provider
            .list("temp_path")
            .containsAll(List.of("temp_path/test.txt", "temp_path/test2.txt")));
        Assertions.assertEquals(2, this.provider.list("temp_path").size());
        Assertions.assertEquals(2, this.provider.list("temp_path").size());
        Assertions.assertEquals(0, this.provider.list("invalid-path").size());

        this.provider.delete(List.of());
        Assertions.assertEquals(2, this.provider.list("temp_path").size());

        this.provider.delete("invalid");
        Assertions.assertEquals(2, this.provider.list("temp_path").size());

        this.provider.delete("temp_path");
        Assertions.assertEquals(2, this.provider.list("temp_path").size());

        this.provider.delete("temp_path/test2.txt");
        Assertions.assertEquals(1, this.provider.list("temp_path").size());

    }

    @Test
    void allMethods_pathWithInvalidPrefixSuffix_sanitizedPath() throws IOException {
        Assertions.assertEquals("temp_path/test.txt", saveTestFile("temp-path/test.txt").getPath());
        Assertions.assertEquals("temp_path/test2.txt", saveTestFile("/temp-path/test2.txt").getPath());
        Assertions.assertEquals("_temp_path/test3.txt", saveTestFile("/ temp-path/test3.txt").getPath());

    }

    @Test
    void delete_directoryPath_deleteNothing() throws IOException {
        saveTestFile("temp-path/test.txt");
        Assertions.assertTrue(this.provider.list("temp_path").contains("temp_path/test.txt"));
        this.provider.delete("temp-path");
        Assertions.assertTrue(this.provider.list("temp_path").contains("temp_path/test.txt"));
        this.provider.delete("temp-path/2");
        Assertions.assertTrue(this.provider.list("temp_path").contains("temp_path/test.txt"));
        this.provider.delete("invalid");
        Assertions.assertTrue(this.provider.list("temp_path").contains("temp_path/test.txt"));
    }

    private WebFile saveTestFile(final String path) throws IOException {
        return this.provider.save(path, "text/plain", "Test".getBytes(StandardCharsets.UTF_8), Map.of("key", "value"));
    }

}

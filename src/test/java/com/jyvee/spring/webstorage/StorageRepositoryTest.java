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

package com.jyvee.spring.webstorage;

import com.jyvee.spring.test.webstorage.WebFile;
import com.jyvee.spring.test.webstorage.WebFileType;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
abstract class StorageRepositoryTest {

    private final StorageRepository<@NonNull WebFile, ?> repository;

    @BeforeEach
    void beforeEach() throws IOException {
        this.repository.delete(this.repository.list("temp-path"));
        this.repository.delete(this.repository.list("_temp_path"));
        this.repository.delete("temp-path");
        this.repository.delete("_temp-path");

    }

    @Test
    void saveByFileType_validArgs_Ok() throws IOException {
        final Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        final WebFile saved = this.repository.save(WebFileType.VALID_TEXT,
            "temp-path/test.txt",
            "text/plain",
            "Test".getBytes(StandardCharsets.UTF_8),
            Map.of("key", "value"));
        Assertions.assertEquals(this.repository.getConfiguration().getEndpoint()
                                + "/test/files/temp_path/0/c/0cbc6611f5540bd0809a388dc95a615b/test.txt",
            saved.getUri().toString());
        Assertions.assertEquals(this.repository.getConfiguration().getStorageId(), saved.getStorageId());
        Assertions.assertEquals("test/files/temp_path/0/c/0cbc6611f5540bd0809a388dc95a615b/test.txt", saved.getPath());
        Assertions.assertEquals("text/plain", saved.getContentType());
        Assertions.assertEquals(4, saved.getSize());
        Assertions.assertEquals("0cbc6611f5540bd0809a388dc95a615b", saved.getChecksum());
        Assertions.assertEquals("value", saved.getMetadata().get("key"));
        Assertions.assertEquals("VALID_TEXT", saved.getMetadata().get("fileType"));
        Assertions.assertEquals("test.txt", saved.getMetadata().get("filename"));
        Assertions.assertTrue(saved.getTimestamp().equals(now) || saved.getTimestamp().isAfter(now) && saved
            .getTimestamp()
            .equals(Instant.now()) || saved.getTimestamp().isBefore(Instant.now()));
    }

    @Test
    void saveMultipart_validArgs_Ok() throws IOException {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> this.repository.save(WebFileType.VALID_TEXT, List.of()));

        final List<MockMultipartFile> multipartFiles = List.of(new MockMultipartFile("unused",
                "temp-path/test.txt",
                "text/plain",
                "Test".getBytes(StandardCharsets.UTF_8)),
            new MockMultipartFile("unused",
                "temp-path/test.txt",
                "text/plain",
                "Test".getBytes(StandardCharsets.UTF_8)));

        Assertions.assertEquals(1,
            this.repository.save(WebFileType.VALID_TEXT, List.of(multipartFiles.getFirst())).size());

        final List<? extends WebFile> testStorageFiles = this.repository.save(WebFileType.VALID_TEXT, multipartFiles);
        Assertions.assertEquals(2, testStorageFiles.size());
        for (final WebFile saved : testStorageFiles) {
            Assertions.assertEquals(this.repository.getConfiguration().getEndpoint()
                                    + "/test/files/temp_path/0/c/0cbc6611f5540bd0809a388dc95a615b/test.txt",
                saved.getUri().toString());
            Assertions.assertEquals(this.repository.getConfiguration().getStorageId(), saved.getStorageId());
            Assertions.assertEquals("test/files/temp_path/0/c/0cbc6611f5540bd0809a388dc95a615b/test.txt",
                saved.getPath());
            Assertions.assertEquals("text/plain", saved.getContentType());
            Assertions.assertEquals(4, saved.getSize());
            Assertions.assertEquals("0cbc6611f5540bd0809a388dc95a615b", saved.getChecksum());
            Assertions.assertEquals("VALID_TEXT", saved.getMetadata().get("fileType"));
            Assertions.assertEquals("test.txt", saved.getMetadata().get("filename"));
        }
    }

    @Test
    void service_validArgs_validatorsExist() {
        Assertions.assertEquals(3, this.repository.getValidators().size());
    }

}

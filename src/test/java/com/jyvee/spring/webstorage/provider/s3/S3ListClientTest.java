/*
 * Copyright (c) 2026 Rahim Alizada
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

package com.jyvee.spring.webstorage.provider.s3;

import com.adobe.testing.s3mock.testcontainers.S3MockContainer;
import com.jyvee.spring.webstorage.configuration.S3StorageConfigurationProperties;
import com.jyvee.spring.webstorage.configuration.S3StorageConfigurationPropertiesImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class S3ListClientTest {

    @Container
    private static final S3MockContainer S3_MOCK = new S3MockContainer("latest").withInitialBuckets("bucket");

    private S3ListClient client;

    @BeforeAll
    void beforeAll() throws IOException {
        final S3StorageConfigurationProperties props = new S3StorageConfigurationPropertiesImpl(URI.create(
            S3_MOCK.getHttpEndpoint()
            + "/?region=region&bucket=bucket&key=key&secret=secret&endpoint=https://site.url"));
        this.client = new S3ListClient(HttpClientProvider.get().getHttpClient(), props);

        // Pre-populate the bucket using S3RawPutClient
        final S3PutClient putClient = new S3PutClient(HttpClientProvider.get().getHttpClient(), props);
        final byte[] content = "data".getBytes(StandardCharsets.UTF_8);
        putClient.put("images/photo.jpg", "image/jpeg", content, Map.of());
        putClient.put("images/icon.png", "image/png", content, Map.of());
        putClient.put("documents/report.pdf", "application/pdf", content, Map.of());
        putClient.put("documents/notes.txt", "text/plain", content, Map.of());
        putClient.put("readme.txt", "text/plain", content, Map.of());
    }

    @Test
    void list_emptyPrefix_returnsAllKeys() throws IOException {
        final List<String> keys = this.client.list("");
        Assertions.assertEquals(5, keys.size());
        Assertions.assertTrue(keys.contains("images/photo.jpg"));
        Assertions.assertTrue(keys.contains("images/icon.png"));
        Assertions.assertTrue(keys.contains("documents/report.pdf"));
        Assertions.assertTrue(keys.contains("documents/notes.txt"));
        Assertions.assertTrue(keys.contains("readme.txt"));
    }

    @Test
    void list_imagesPrefix_returnsTwoImageKeys() throws IOException {
        final List<String> keys = this.client.list("images");
        Assertions.assertEquals(2, keys.size());
        Assertions.assertTrue(keys.contains("images/photo.jpg"));
        Assertions.assertTrue(keys.contains("images/icon.png"));
    }

    @Test
    void list_documentsPrefix_returnsTwoDocumentKeys() throws IOException {
        final List<String> keys = this.client.list("documents");
        Assertions.assertEquals(2, keys.size());
        Assertions.assertTrue(keys.contains("documents/report.pdf"));
        Assertions.assertTrue(keys.contains("documents/notes.txt"));
    }

    @Test
    void list_readmePrefix_returnsSingleKey() throws IOException {
        final List<String> keys = this.client.list("readme");
        Assertions.assertEquals(1, keys.size());
        Assertions.assertEquals("readme.txt", keys.getFirst());
    }

    @Test
    void list_nonExistentPrefix_returnsEmptyNotTruncated() throws IOException {
        final List<String> keys = this.client.list("nonexistent/");
        Assertions.assertTrue(keys.isEmpty());
    }

    @Test
    void list_nonExistentBucket_throwsIOException() {
        final S3StorageConfigurationProperties props = new S3StorageConfigurationPropertiesImpl(URI.create(
            S3_MOCK.getHttpEndpoint()
            + "/?region=region&bucket=nonexistent&key=key&secret=secret&endpoint=https://site.url"));
        final S3ListClient errorClient = new S3ListClient(HttpClientProvider.get().getHttpClient(), props);
        Assertions.assertThrows(IOException.class, () -> errorClient.list(""));
    }

    @Test
    void list_smallPageSize_returnsAllKeysAcrossPages() throws IOException {
        final List<String> keys = this.client.list("", 2);
        Assertions.assertEquals(5, keys.size());
        Assertions.assertTrue(keys.contains("images/photo.jpg"));
        Assertions.assertTrue(keys.contains("images/icon.png"));
        Assertions.assertTrue(keys.contains("documents/report.pdf"));
        Assertions.assertTrue(keys.contains("documents/notes.txt"));
        Assertions.assertTrue(keys.contains("readme.txt"));
    }

}

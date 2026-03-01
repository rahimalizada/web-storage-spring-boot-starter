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
import java.time.Instant;
import java.util.Map;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class S3GetClientTest {

    @Container
    private static final S3MockContainer S3_MOCK = new S3MockContainer("latest").withInitialBuckets("bucket");

    private S3GetClient client;

    @BeforeAll
    void beforeAll() throws IOException {
        final S3StorageConfigurationProperties props = new S3StorageConfigurationPropertiesImpl(URI.create(
            S3_MOCK.getHttpEndpoint()
            + "/?region=region&bucket=bucket&key=key&secret=secret&endpoint=https://site.url"));
        this.client = new S3GetClient(HttpClientProvider.get().getHttpClient(), props);

        final S3PutClient putClient = new S3PutClient(HttpClientProvider.get().getHttpClient(), props);
        putClient.put("load/file.txt", "text/plain", "Test".getBytes(StandardCharsets.UTF_8), Map.of());
        putClient.put("load/nested/file.json", "application/json", "{}".getBytes(StandardCharsets.UTF_8), Map.of());
        putClient.put("load/meta.bin", "application/octet-stream", "meta".getBytes(StandardCharsets.UTF_8),
            Map.of("filename", "report.txt", "owner", "user1"));
        putClient.put("load/empty.bin", "application/octet-stream", new byte[0], Map.of());
    }

    @Test
    void get_existingKey_returnsExpectedHeaders() throws IOException {
        final S3GetResponse response = this.client.get("load/file.txt");
        Assertions.assertEquals("text/plain", response.contentType());
        Assertions.assertEquals(4L, response.contentLength());
        Assertions.assertEquals("0cbc6611f5540bd0809a388dc95a615b", stripQuotes(response.eTag()));
        Assertions.assertTrue(response.metadata().isEmpty());
        Assertions.assertNotNull(response.lastModified());
        Assertions.assertFalse(response.lastModified().isAfter(Instant.now()));
    }

    @Test
    void get_nestedPath_returnsExpectedSizeAndContentType() throws IOException {
        final S3GetResponse response = this.client.get("load/nested/file.json");
        Assertions.assertEquals("application/json", response.contentType());
        Assertions.assertEquals(2L, response.contentLength());
        Assertions.assertNotNull(response.lastModified());
    }

    @Test
    void get_withCustomMetadata_returnsMetadataMap() throws IOException {
        final S3GetResponse response = this.client.get("load/meta.bin");
        Assertions.assertEquals("report.txt", response.metadata().get("filename"));
        Assertions.assertEquals("user1", response.metadata().get("owner"));
    }

    @Test
    void get_emptyPayload_returnsZeroContentLength() throws IOException {
        final S3GetResponse response = this.client.get("load/empty.bin");
        Assertions.assertEquals(0L, response.contentLength());
        Assertions.assertEquals("application/octet-stream", response.contentType());
    }

    @Test
    void get_nonExistentKey_throwsIOException() {
        Assertions.assertThrows(IOException.class, () -> this.client.get("load/missing.txt"));
    }

    @Test
    void get_nonExistentBucket_throwsIOException() {
        final S3StorageConfigurationProperties props = new S3StorageConfigurationPropertiesImpl(URI.create(
            S3_MOCK.getHttpEndpoint()
            + "/?region=region&bucket=nonexistent&key=key&secret=secret&endpoint=https://site.url"));
        final S3GetClient errorClient = new S3GetClient(HttpClientProvider.get().getHttpClient(), props);
        Assertions.assertThrows(IOException.class, () -> errorClient.get("key.txt"));
    }

    private static String stripQuotes(final String eTag) {
        if (eTag.startsWith("\"") && eTag.endsWith("\"")) {
            return eTag.substring(1, eTag.length() - 1);
        }
        return eTag;
    }

}

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
class S3RawDeleteClientTest {

    @Container
    private static final S3MockContainer S3_MOCK = new S3MockContainer("latest").withInitialBuckets("bucket");

    private S3RawDeleteClient deleteClient;

    private S3RawPutClient putClient;

    private S3RawListClient listClient;

    @BeforeAll
    void beforeAll() {
        final S3StorageConfigurationProperties props = new S3StorageConfigurationPropertiesImpl(URI.create(
            S3_MOCK.getHttpEndpoint()
            + "/?region=region&bucket=bucket&key=key&secret=secret&endpoint=https://site.url"));
        this.deleteClient = new S3RawDeleteClient(HttpClientProvider.get().getHttpClient(), props);
        this.putClient = new S3RawPutClient(HttpClientProvider.get().getHttpClient(), props);
        this.listClient = new S3RawListClient(HttpClientProvider.get().getHttpClient(), props);
    }

    @Test
    void deleteObjects_existingSingleKey_removesObject() throws IOException {
        final String key = "delete/single/file.txt";
        this.putClient.putObject(key, "text/plain", "data".getBytes(StandardCharsets.UTF_8), Map.of());
        Assertions.assertTrue(this.listClient.listObjects("delete/single/").contains(key));

        this.deleteClient.deleteObjects(List.of(key));

        Assertions.assertFalse(this.listClient.listObjects("delete/single/").contains(key));
    }

    @Test
    void deleteObjects_multipleExistingKeys_removesAll() throws IOException {
        final List<String> keys = List.of("delete/multi/a.txt", "delete/multi/b.txt", "delete/multi/c/file.txt");
        for (final String key : keys) {
            this.putClient.putObject(key, "text/plain", "data".getBytes(StandardCharsets.UTF_8), Map.of());
        }

        this.deleteClient.deleteObjects(keys);

        Assertions.assertTrue(this.listClient.listObjects("delete/multi/").isEmpty());
    }

    @Test
    void deleteObjects_mixedExistingAndMissingKeys_deletesExistingAndSucceeds() throws IOException {
        final String existing = "delete/mixed/existing.txt";
        this.putClient.putObject(existing, "text/plain", "data".getBytes(StandardCharsets.UTF_8), Map.of());

        Assertions.assertDoesNotThrow(
            () -> this.deleteClient.deleteObjects(List.of(existing, "delete/mixed/missing.txt")));
        Assertions.assertTrue(this.listClient.listObjects("delete/mixed/").isEmpty());
    }

    @Test
    void deleteObjects_nonExistentKeysOnly_succeeds() {
        Assertions.assertDoesNotThrow(
            () -> this.deleteClient.deleteObjects(List.of("delete/missing/a.txt", "delete/missing/b.txt")));
    }

    @Test
    void deleteObjects_nestedPathsAndDots_removesObjects() throws IOException {
        final List<String> keys = List.of("delete/nested/a/b/c.json", "delete/nested/version-1.2.3/file.bin");
        for (final String key : keys) {
            this.putClient.putObject(key, "application/octet-stream", "x".getBytes(StandardCharsets.UTF_8), Map.of());
        }

        this.deleteClient.deleteObjects(keys);

        Assertions.assertTrue(this.listClient.listObjects("delete/nested/").isEmpty());
    }

    @Test
    void deleteObjects_nonExistentBucket_throwsIOException() {
        final S3StorageConfigurationProperties props = new S3StorageConfigurationPropertiesImpl(URI.create(
            S3_MOCK.getHttpEndpoint()
            + "/?region=region&bucket=nonexistent&key=key&secret=secret&endpoint=https://site.url"));
        final S3RawDeleteClient errorClient = new S3RawDeleteClient(HttpClientProvider.get().getHttpClient(), props);
        Assertions.assertThrows(IOException.class, () -> errorClient.deleteObjects(List.of("key.txt")));
    }

}

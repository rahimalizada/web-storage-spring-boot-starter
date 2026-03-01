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
import java.util.Map;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class S3CopyClientTest {

    @Container
    private static final S3MockContainer S3_MOCK = new S3MockContainer("latest").withInitialBuckets("bucket");

    private S3CopyClient copyClient;

    private S3PutClient putClient;

    private S3GetClient getClient;

    private S3ListClient listClient;

    @BeforeAll
    void beforeAll() {
        final S3StorageConfigurationProperties props = new S3StorageConfigurationPropertiesImpl(URI.create(
            S3_MOCK.getHttpEndpoint()
            + "/?region=region&bucket=bucket&key=key&secret=secret&endpoint=https://site.url"));
        this.copyClient = new S3CopyClient(HttpClientProvider.get().getHttpClient(), props);
        this.putClient = new S3PutClient(HttpClientProvider.get().getHttpClient(), props);
        this.getClient = new S3GetClient(HttpClientProvider.get().getHttpClient(), props);
        this.listClient = new S3ListClient(HttpClientProvider.get().getHttpClient(), props);
    }

    @Test
    void copyObject_existingKey_createsDestinationWithSameMetadata() throws IOException {
        final String fromKey = "copy/metadata/source.txt";
        final String toKey = "copy/metadata/destination.txt";
        this.putClient.put(fromKey, "text/plain", "Test".getBytes(StandardCharsets.UTF_8),
            Map.of("filename", "report.txt", "owner", "user1"));

        this.copyClient.copy(fromKey, toKey);

        final S3GetResponse source = this.getClient.get(fromKey);
        final S3GetResponse copied = this.getClient.get(toKey);
        Assertions.assertEquals(source.contentType(), copied.contentType());
        Assertions.assertEquals(source.contentLength(), copied.contentLength());
        Assertions.assertEquals(stripQuotes(source.eTag()), stripQuotes(copied.eTag()));
        Assertions.assertEquals(source.metadata(), copied.metadata());
    }

    @Test
    void copyObject_nestedDestination_creates() throws IOException {
        final String fromKey = "copy/nested/from.txt";
        final String toKey = "copy/nested/deep/path/to.txt";
        this.putClient.put(fromKey, "text/plain", "data".getBytes(StandardCharsets.UTF_8), Map.of());

        this.copyClient.copy(fromKey, toKey);

        Assertions.assertTrue(this.listClient.list("copy/nested/deep/path/").contains(toKey));
    }

    @Test
    void copy_existingDestination_overwritesWithSourceContent() throws IOException {
        final String fromKey = "copy/overwrite/from.txt";
        final String toKey = "copy/overwrite/to.txt";
        this.putClient.put(fromKey, "text/plain", "new".getBytes(StandardCharsets.UTF_8), Map.of());
        this.putClient.put(toKey, "text/plain", "old".getBytes(StandardCharsets.UTF_8), Map.of());

        this.copyClient.copy(fromKey, toKey);

        final S3GetResponse source = this.getClient.get(fromKey);
        final S3GetResponse copied = this.getClient.get(toKey);
        Assertions.assertEquals(stripQuotes(source.eTag()), stripQuotes(copied.eTag()));
        Assertions.assertEquals(source.contentLength(), copied.contentLength());
    }

    @Test
    void copy_missingSource_throwsIOException() {
        Assertions.assertThrows(IOException.class,
            () -> this.copyClient.copy("copy/missing/source.txt", "copy/missing/to.txt"));
    }

    @Test
    void copy_nonExistentBucket_throwsIOException() {
        final S3StorageConfigurationProperties props = new S3StorageConfigurationPropertiesImpl(URI.create(
            S3_MOCK.getHttpEndpoint()
            + "/?region=region&bucket=nonexistent&key=key&secret=secret&endpoint=https://site.url"));
        final S3CopyClient errorClient = new S3CopyClient(HttpClientProvider.get().getHttpClient(), props);

        Assertions.assertThrows(IOException.class, () -> errorClient.copy("copy/source.txt", "copy/destination.txt"));
    }

    private static String stripQuotes(final String eTag) {
        if (eTag.startsWith("\"") && eTag.endsWith("\"")) {
            return eTag.substring(1, eTag.length() - 1);
        }
        return eTag;
    }

}

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
class S3RawCopyClientTest {

    @Container
    private static final S3MockContainer S3_MOCK = new S3MockContainer("latest").withInitialBuckets("bucket");

    private S3RawCopyClient copyClient;

    private S3RawPutClient putClient;

    private S3RawGetClient getClient;

    private S3RawListClient listClient;

    @BeforeAll
    void beforeAll() {
        final S3StorageConfigurationProperties props = new S3StorageConfigurationPropertiesImpl(URI.create(
            S3_MOCK.getHttpEndpoint()
            + "/?region=region&bucket=bucket&key=key&secret=secret&endpoint=https://site.url"));
        this.copyClient = new S3RawCopyClient(HttpClientProvider.get().getHttpClient(), props);
        this.putClient = new S3RawPutClient(HttpClientProvider.get().getHttpClient(), props);
        this.getClient = new S3RawGetClient(HttpClientProvider.get().getHttpClient(), props);
        this.listClient = new S3RawListClient(HttpClientProvider.get().getHttpClient(), props);
    }

    @Test
    void copyObject_existingKey_createsDestinationWithSameObjectMetadata() throws IOException {
        final String fromKey = "copy/metadata/source.txt";
        final String toKey = "copy/metadata/destination.txt";
        this.putClient.putObject(fromKey, "text/plain", "Test".getBytes(StandardCharsets.UTF_8),
            Map.of("filename", "report.txt", "owner", "user1"));

        this.copyClient.copyObject(fromKey, toKey);

        final S3RawGetResponse source = this.getClient.getObject(fromKey);
        final S3RawGetResponse copied = this.getClient.getObject(toKey);
        Assertions.assertEquals(source.contentType(), copied.contentType());
        Assertions.assertEquals(source.contentLength(), copied.contentLength());
        Assertions.assertEquals(stripQuotes(source.eTag()), stripQuotes(copied.eTag()));
        Assertions.assertEquals(source.metadata(), copied.metadata());
    }

    @Test
    void copyObject_nestedDestination_createsObject() throws IOException {
        final String fromKey = "copy/nested/from.txt";
        final String toKey = "copy/nested/deep/path/to.txt";
        this.putClient.putObject(fromKey, "text/plain", "data".getBytes(StandardCharsets.UTF_8), Map.of());

        this.copyClient.copyObject(fromKey, toKey);

        Assertions.assertTrue(this.listClient.listObjects("copy/nested/deep/path/").contains(toKey));
    }

    @Test
    void copyObject_existingDestination_overwritesWithSourceContent() throws IOException {
        final String fromKey = "copy/overwrite/from.txt";
        final String toKey = "copy/overwrite/to.txt";
        this.putClient.putObject(fromKey, "text/plain", "new".getBytes(StandardCharsets.UTF_8), Map.of());
        this.putClient.putObject(toKey, "text/plain", "old".getBytes(StandardCharsets.UTF_8), Map.of());

        this.copyClient.copyObject(fromKey, toKey);

        final S3RawGetResponse source = this.getClient.getObject(fromKey);
        final S3RawGetResponse copied = this.getClient.getObject(toKey);
        Assertions.assertEquals(stripQuotes(source.eTag()), stripQuotes(copied.eTag()));
        Assertions.assertEquals(source.contentLength(), copied.contentLength());
    }

    @Test
    void copyObject_missingSource_throwsIOException() {
        Assertions.assertThrows(IOException.class,
            () -> this.copyClient.copyObject("copy/missing/source.txt", "copy/missing/to.txt"));
    }

    @Test
    void copyObject_nonExistentBucket_throwsIOException() {
        final S3StorageConfigurationProperties props = new S3StorageConfigurationPropertiesImpl(URI.create(
            S3_MOCK.getHttpEndpoint()
            + "/?region=region&bucket=nonexistent&key=key&secret=secret&endpoint=https://site.url"));
        final S3RawCopyClient errorClient = new S3RawCopyClient(HttpClientProvider.get().getHttpClient(), props);

        Assertions.assertThrows(IOException.class,
            () -> errorClient.copyObject("copy/source.txt", "copy/destination.txt"));
    }

    private static String stripQuotes(final String eTag) {
        if (eTag.startsWith("\"") && eTag.endsWith("\"")) {
            return eTag.substring(1, eTag.length() - 1);
        }
        return eTag;
    }

}

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class S3PutClientTest {

    @Container
    private static final S3MockContainer S3_MOCK = new S3MockContainer("latest").withInitialBuckets("bucket");

    private S3PutClient client;

    @BeforeAll
    void beforeAll() {
        final S3StorageConfigurationProperties props = new S3StorageConfigurationPropertiesImpl(URI.create(
            S3_MOCK.getHttpEndpoint()
            + "/?region=region&bucket=bucket&key=key&secret=secret&endpoint=https://site.url"));
        this.client = new S3PutClient(HttpClientProvider.get().getHttpClient(), props);
    }

    // --- Key path variations ---

    @ParameterizedTest(name = "key={0}")
    @ValueSource(strings = {"file.txt", "nested/path/file.txt", "deep/a/b/c/object.json", "with_underscores.txt",
        "with.multiple.dots.txt", "single"})
    void put_variousKeys_returnsValidEtag(final String key) throws IOException {
        assertValidEtag(this.client.put(key, "text/plain", "data".getBytes(StandardCharsets.UTF_8), Map.of()).eTag());
    }

    // --- Content-type variations ---

    @ParameterizedTest(name = "contentType={0}")
    @ValueSource(strings = {"text/plain", "application/json", "image/jpeg", "image/png", "application/octet-stream",
        "application/pdf"})
    void put_variousContentTypes_returnsValidEtag(final String contentType) throws IOException {
        assertValidEtag(this.client
            .put("content-type/" + contentType.replace("/", "_"), contentType,
                "payload".getBytes(StandardCharsets.UTF_8), Map.of())
            .eTag());
    }

    // --- Payload size/type variations ---

    static Stream<Arguments> payloadArguments() {
        return Stream.of(Arguments.of("empty", new byte[0]),
            Arguments.of("small-text", "hello world".getBytes(StandardCharsets.UTF_8)),
            Arguments.of("binary-non-utf8", new byte[]{(byte) 0xFF, (byte) 0xFE, 0x00, 0x01}),
            Arguments.of("one-megabyte", new byte[1024 * 1024]));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("payloadArguments")
    void put_variousPayloads_returnsValidEtag(final String label, final byte[] payload) throws IOException {
        assertValidEtag(this.client.put("payloads/" + label, "application/octet-stream", payload, Map.of()).eTag());
    }

    // --- Metadata count variations ---

    static Stream<Arguments> metadataArguments() {
        return Stream.of(Arguments.of("no-metadata", Map.of()),
            Arguments.of("one-entry", Map.of("filename", "test.txt")), Arguments.of("multiple-entries",
                Map.of("filename", "test.txt", "filetype", "DOCUMENT", "author", "user1")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("metadataArguments")
    void put_variousMetadata_returnsValidEtag(final String label, final Map<String, String> metadata)
        throws IOException {
        assertValidEtag(this.client
            .put("metadata/" + label, "text/plain", "data".getBytes(StandardCharsets.UTF_8), metadata)
            .eTag());
    }

    // --- ETag correctness ---

    @Test
    void put_emptyPayload_returnsKnownMd5() throws IOException {
        // MD5 of zero bytes is always d41d8cd98f00b204e9800998ecf8427e
        final S3PutResponse response =
            this.client.put("etag/empty.bin", "application/octet-stream", new byte[0], Map.of());
        Assertions.assertEquals("d41d8cd98f00b204e9800998ecf8427e", stripQuotes(response.eTag()));
    }

    @Test
    void put_knownContent_returnsKnownMd5() throws IOException {
        // MD5("Test") = 0cbc6611f5540bd0809a388dc95a615b
        final byte[] payload = "Test".getBytes(StandardCharsets.UTF_8);
        final S3PutResponse response = this.client.put("etag/known.txt", "text/plain", payload, Map.of());
        Assertions.assertEquals("0cbc6611f5540bd0809a388dc95a615b", stripQuotes(response.eTag()));
    }

    @Test
    void put_sameContent_sameEtag() throws IOException {
        final byte[] payload = "deterministic-content".getBytes(StandardCharsets.UTF_8);
        final S3PutResponse response1 = this.client.put("etag/same-a.txt", "text/plain", payload, Map.of());
        final S3PutResponse response2 = this.client.put("etag/same-b.txt", "text/plain", payload, Map.of());
        Assertions.assertEquals(response1.eTag(), response2.eTag());
    }

    @Test
    void put_differentContent_differentEtag() throws IOException {
        final S3PutResponse response1 =
            this.client.put("etag/diff-a.txt", "text/plain", "content-alpha".getBytes(StandardCharsets.UTF_8),
                Map.of());
        final S3PutResponse response2 =
            this.client.put("etag/diff-b.txt", "text/plain", "content-beta".getBytes(StandardCharsets.UTF_8), Map.of());
        Assertions.assertNotEquals(response1.eTag(), response2.eTag());
    }

    @Test
    void put_uploadedFile_isPubliclyReadable() throws IOException, InterruptedException {
        final String key = "public/put-file.txt";
        final byte[] payload = "public-content".getBytes(StandardCharsets.UTF_8);
        this.client.put(key, "text/plain", payload, Map.of());

        final HttpResponse<byte[]> response = sendUnsignedGet(key);
        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertArrayEquals(payload, response.body());
    }

    // --- Conditional response fields ---

    // --- Overwrite ---

    @Test
    void put_overwriteExistingKey_etagReflectsNewContent() throws IOException {
        final String key = "overwrite/file.txt";
        final S3PutResponse response1 =
            this.client.put(key, "text/plain", "original".getBytes(StandardCharsets.UTF_8), Map.of());
        final S3PutResponse response2 =
            this.client.put(key, "text/plain", "updated".getBytes(StandardCharsets.UTF_8), Map.of());
        assertValidEtag(response2.eTag());
        Assertions.assertNotEquals(response1.eTag(), response2.eTag());
    }

    // --- Error handling ---

    @Test
    void put_nonExistentBucket_throwsIOException() {
        final S3StorageConfigurationProperties props = new S3StorageConfigurationPropertiesImpl(URI.create(
            S3_MOCK.getHttpEndpoint()
            + "/?region=region&bucket=nonexistent&key=key&secret=secret&endpoint=https://site.url"));
        final S3PutClient errorClient = new S3PutClient(HttpClientProvider.get().getHttpClient(), props);
        Assertions.assertThrows(IOException.class,
            () -> errorClient.put("key.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8), Map.of()));
    }

    // --- Helpers ---

    private static String stripQuotes(final String eTag) {
        if (eTag.startsWith("\"") && eTag.endsWith("\"")) {
            return eTag.substring(1, eTag.length() - 1);
        }
        return eTag;
    }

    private static void assertValidEtag(final String eTag) {
        Assertions.assertNotNull(eTag);
        Assertions.assertFalse(eTag.isBlank());
        final String stripped = stripQuotes(eTag);
        Assertions.assertFalse(stripped.isBlank(), "ETag after stripping quotes should not be blank: " + eTag);
    }

    private static HttpResponse<byte[]> sendUnsignedGet(final String key) throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest
            .newBuilder(URI.create(S3_MOCK.getHttpEndpoint() + "/bucket/" + key))
            .GET()
            .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

}

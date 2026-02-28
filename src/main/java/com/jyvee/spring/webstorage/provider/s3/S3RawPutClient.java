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

import com.jyvee.spring.webstorage.configuration.S3StorageConfigurationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static com.jyvee.spring.webstorage.provider.s3.S3RawHttpUtil.applyHeadersExceptHost;
import static com.jyvee.spring.webstorage.provider.s3.S3RawSigV4Util.ALGORITHM;
import static com.jyvee.spring.webstorage.provider.s3.S3RawSigV4Util.SERVICE;
import static com.jyvee.spring.webstorage.provider.s3.S3RawSigV4Util.TERMINATOR;
import static com.jyvee.spring.webstorage.provider.s3.S3RawSigV4Util.buildCanonicalHeaders;
import static com.jyvee.spring.webstorage.provider.s3.S3RawSigV4Util.buildHost;
import static com.jyvee.spring.webstorage.provider.s3.S3RawSigV4Util.deriveSigningKey;
import static com.jyvee.spring.webstorage.provider.s3.S3RawSigV4Util.formatDate;
import static com.jyvee.spring.webstorage.provider.s3.S3RawSigV4Util.formatDatetime;
import static com.jyvee.spring.webstorage.provider.s3.S3RawSigV4Util.hmacSha256Hex;
import static com.jyvee.spring.webstorage.provider.s3.S3RawSigV4Util.sha256Hex;

/**
 * Uploads objects to S3-compatible storage using raw HTTP with AWS SigV4 request signing.
 * No external dependencies are used - only the Java standard library.
 */
@RequiredArgsConstructor
public class S3RawPutClient {

    private final HttpClient httpClient;

    private final S3StorageConfigurationProperties configuration;

    /**
     * Uploads bytes to S3 using a raw HTTP PUT with AWS SigV4 signing.
     *
     * @param key                the S3 object key (already sanitized)
     * @param contentType        MIME type of the payload
     * @param payload            raw bytes to upload
     * @param urlEncodedMetadata metadata with URL-encoded values; stored as {@code x-amz-meta-} headers
     * @return the response containing the raw ETag from the HTTP response header (may include surrounding quotes)
     * @throws IOException if the upload fails or the HTTP response status is not 200
     */
    public S3RawPutResponse putObject(final String key, final String contentType, final byte[] payload,
                                      final Map<String, String> urlEncodedMetadata) throws IOException {
        final Instant now = Instant.now();
        final String datetime = formatDatetime(now);
        final String date = formatDate(now);
        final String payloadHash = sha256Hex(payload);

        final URI serviceEndpoint = this.configuration.getServiceEndpoint();
        final String host = buildHost(serviceEndpoint);
        final String bucket = this.configuration.getBucket();

        // TreeMap gives lexicographic ordering, required for canonical headers
        final SortedMap<String, String> signingHeaders = new TreeMap<>();
        signingHeaders.put("content-type", contentType);
        signingHeaders.put("host", host);
        signingHeaders.put("x-amz-acl", "public-read");
        signingHeaders.put("x-amz-content-sha256", payloadHash);
        signingHeaders.put("x-amz-date", datetime);
        for (final Map.Entry<String, String> entry : urlEncodedMetadata.entrySet()) {
            signingHeaders.put("x-amz-meta-" + entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }

        final String signedHeaders = String.join(";", signingHeaders.keySet());
        final String canonicalPath = "/" + bucket + "/" + key;

        // Each canonical header line ends with \n; the last \n separates headers from signedHeaders
        final String canonicalRequest =
            "PUT\n" + canonicalPath + "\n" + "\n"                          // empty canonical query string
            + buildCanonicalHeaders(signingHeaders) + signedHeaders + "\n" + payloadHash;

        final String credentialScope = date + "/" + this.configuration.getRegion() + "/" + SERVICE + "/" + TERMINATOR;
        final String stringToSign = ALGORITHM + "\n" + datetime + "\n" + credentialScope + "\n" + sha256Hex(
            canonicalRequest.getBytes(StandardCharsets.UTF_8));

        final byte[] signingKey =
            deriveSigningKey(this.configuration.getSecret(), date, this.configuration.getRegion());
        final String signature = hmacSha256Hex(signingKey, stringToSign);

        final String authorization =
            ALGORITHM + " Credential=" + this.configuration.getKey() + "/" + credentialScope + ", SignedHeaders="
            + signedHeaders + ", Signature=" + signature;

        // Path-style URL: {serviceEndpoint}/{bucket}/{key}
        final URI requestUri = URI.create(serviceEndpoint + "/" + bucket + "/" + key);
        final HttpRequest.Builder builder =
            HttpRequest.newBuilder(requestUri).PUT(HttpRequest.BodyPublishers.ofByteArray(payload));

        // 'host' is a restricted header managed automatically by HttpClient from the URI
        applyHeadersExceptHost(builder, signingHeaders);
        builder.header("authorization", authorization);

        try {
            final HttpResponse<Void> response =
                this.httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != HttpStatus.OK.value()) {
                throw new IOException("S3 PUT failed with HTTP " + response.statusCode() + " for key: " + key);
            }
            final HttpHeaders headers = response.headers();
            return new S3RawPutResponse(headers.firstValue("ETag").orElse(""),
                headers.firstValue("x-amz-expiration").orElse(null),
                headers.firstValue("x-amz-version-id").orElse(null),
                headers.firstValue("x-amz-checksum-crc32").orElse(null),
                headers.firstValue("x-amz-checksum-crc32c").orElse(null),
                headers.firstValue("x-amz-checksum-sha1").orElse(null),
                headers.firstValue("x-amz-checksum-sha256").orElse(null));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("S3 PUT interrupted for key: " + key, e);
        }
    }

}

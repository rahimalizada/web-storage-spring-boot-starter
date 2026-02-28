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
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.SortedMap;
import java.util.TreeMap;

import static com.jyvee.spring.webstorage.provider.s3.S3RawEncodingUtil.encodePath;
import static com.jyvee.spring.webstorage.provider.s3.S3RawHttpUtil.applyHeadersExceptHost;
import static com.jyvee.spring.webstorage.provider.s3.S3RawSigV4Util.ALGORITHM;
import static com.jyvee.spring.webstorage.provider.s3.S3RawSigV4Util.EMPTY_PAYLOAD_HASH;
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
 * Copies objects in S3-compatible storage using raw HTTP with AWS SigV4 request signing.
 * No external dependencies are used - only the Java standard library.
 */
@RequiredArgsConstructor
public class S3RawCopyClient {

    private final HttpClient httpClient;

    private final S3StorageConfigurationProperties configuration;

    /**
     * Copies one object to another key in the same S3 bucket using a raw HTTP PUT + x-amz-copy-source.
     *
     * @param fromKey source S3 key (already sanitized)
     * @param toKey   destination S3 key (already sanitized)
     * @throws IOException if the copy fails or the HTTP response status is not 200
     */
    public void copyObject(final String fromKey, final String toKey) throws IOException {
        final Instant now = Instant.now();
        final String datetime = formatDatetime(now);
        final String date = formatDate(now);

        final URI serviceEndpoint = this.configuration.getServiceEndpoint();
        final String host = buildHost(serviceEndpoint);
        final String bucket = this.configuration.getBucket();
        final String copySource = "/" + bucket + "/" + encodePath(fromKey);

        // TreeMap gives lexicographic ordering, required for canonical headers.
        final SortedMap<String, String> signingHeaders = new TreeMap<>();
        signingHeaders.put("host", host);
        signingHeaders.put("x-amz-acl", "public-read");
        signingHeaders.put("x-amz-content-sha256", EMPTY_PAYLOAD_HASH);
        signingHeaders.put("x-amz-copy-source", copySource);
        signingHeaders.put("x-amz-date", datetime);

        final String signedHeaders = String.join(";", signingHeaders.keySet());
        final String canonicalPath = "/" + bucket + "/" + toKey;

        final String canonicalRequest =
            "PUT\n" + canonicalPath + "\n" + "\n" + buildCanonicalHeaders(signingHeaders) + signedHeaders + "\n"
            + EMPTY_PAYLOAD_HASH;

        final String credentialScope = date + "/" + this.configuration.getRegion() + "/" + SERVICE + "/" + TERMINATOR;
        final String stringToSign = ALGORITHM + "\n" + datetime + "\n" + credentialScope + "\n" + sha256Hex(
            canonicalRequest.getBytes(StandardCharsets.UTF_8));

        final byte[] signingKey =
            deriveSigningKey(this.configuration.getSecret(), date, this.configuration.getRegion());
        final String signature = hmacSha256Hex(signingKey, stringToSign);

        final String authorization =
            ALGORITHM + " Credential=" + this.configuration.getKey() + "/" + credentialScope + ", SignedHeaders="
            + signedHeaders + ", Signature=" + signature;

        final URI requestUri = URI.create(serviceEndpoint + "/" + bucket + "/" + toKey);
        final HttpRequest.Builder builder = HttpRequest.newBuilder(requestUri).PUT(HttpRequest.BodyPublishers.noBody());

        // 'host' is a restricted header managed automatically by HttpClient from the URI.
        applyHeadersExceptHost(builder, signingHeaders);
        builder.header("authorization", authorization);

        try {
            final HttpResponse<Void> response =
                this.httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != HttpStatus.OK.value()) {
                throw new IOException(
                    "S3 COPY failed with HTTP " + response.statusCode() + " from key: " + fromKey + " to key: "
                    + toKey);
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("S3 COPY interrupted from key: " + fromKey + " to key: " + toKey, e);
        }
    }

}

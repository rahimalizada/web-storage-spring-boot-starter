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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

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
 * Fetches object metadata from S3-compatible storage using raw HTTP with AWS SigV4 request signing.
 * No external dependencies are used - only the Java standard library.
 */
@RequiredArgsConstructor
public class S3RawGetClient {

    private final HttpClient httpClient;

    private final S3StorageConfigurationProperties configuration;

    /**
     * Fetches object metadata from S3 using a raw HTTP GET with AWS SigV4 signing.
     *
     * @param key the S3 object key (already sanitized)
     * @return metadata response extracted from HTTP response headers
     * @throws IOException if the request fails or the HTTP response status is not 200
     */
    public S3RawGetResponse getObject(final String key) throws IOException {
        final Instant now = Instant.now();
        final String datetime = formatDatetime(now);
        final String date = formatDate(now);

        final URI serviceEndpoint = this.configuration.getServiceEndpoint();
        final String host = buildHost(serviceEndpoint);
        final String bucket = this.configuration.getBucket();

        // Signing headers: host, x-amz-content-sha256, x-amz-date (sorted)
        final SortedMap<String, String> signingHeaders = new TreeMap<>();
        signingHeaders.put("host", host);
        signingHeaders.put("x-amz-content-sha256", EMPTY_PAYLOAD_HASH);
        signingHeaders.put("x-amz-date", datetime);

        final String signedHeaders = String.join(";", signingHeaders.keySet());
        final String canonicalPath = "/" + bucket + "/" + key;

        final String canonicalRequest =
            "GET\n" + canonicalPath + "\n" + "\n" + buildCanonicalHeaders(signingHeaders) + signedHeaders + "\n"
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

        final URI requestUri = URI.create(serviceEndpoint + "/" + bucket + "/" + key);
        final HttpRequest.Builder builder = HttpRequest.newBuilder(requestUri).GET();

        // 'host' is a restricted header managed automatically by HttpClient from the URI
        applyHeadersExceptHost(builder, signingHeaders);
        builder.header("authorization", authorization);

        try {
            final HttpResponse<Void> response =
                this.httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != HttpStatus.OK.value()) {
                throw new IOException("S3 GET failed with HTTP " + response.statusCode() + " for key: " + key);
            }
            final HttpHeaders headers = response.headers();
            return new S3RawGetResponse(headers.firstValue("ETag").orElse(""),
                headers.firstValue("content-type").orElse("application/octet-stream"), parseContentLength(headers),
                parseMetadata(headers), parseLastModified(headers));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("S3 GET interrupted for key: " + key, e);
        }
    }

    private static Map<String, String> parseMetadata(final HttpHeaders headers) {
        final Map<String, String> metadata = new HashMap<>();
        for (final Map.Entry<String, List<String>> header : headers.map().entrySet()) {
            if (header.getValue().isEmpty()) {
                continue;
            }
            final String lowerCaseName = header.getKey().toLowerCase(Locale.ROOT);
            if (lowerCaseName.startsWith("x-amz-meta-")) {
                metadata.put(lowerCaseName.substring("x-amz-meta-".length()), header.getValue().get(0));
            }
        }
        return Map.copyOf(metadata);
    }

    private static long parseContentLength(final HttpHeaders headers) throws IOException {
        final String header = headers.firstValue("content-length").orElse("0");
        try {
            return Long.parseLong(header);
        } catch (final NumberFormatException e) {
            throw new IOException("Invalid content-length header: " + header, e);
        }
    }

    private static Instant parseLastModified(final HttpHeaders headers) throws IOException {
        final String header = headers.firstValue("last-modified").orElse(null);
        if (header == null || header.isBlank()) {
            return Instant.now();
        }
        try {
            return DateTimeFormatter.RFC_1123_DATE_TIME.parse(header, Instant::from);
        } catch (final DateTimeParseException e) {
            throw new IOException("Invalid last-modified header: " + header, e);
        }
    }

}

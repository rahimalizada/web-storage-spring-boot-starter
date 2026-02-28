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
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fetches object metadata from S3-compatible storage using raw HTTP request.
 * No external dependencies are used - only the Java standard library.
 */
@RequiredArgsConstructor
public class S3GetClient {

    private static final DateTimeFormatter RFC_1123 = DateTimeFormatter.RFC_1123_DATE_TIME;

    private final HttpClient httpClient;

    private final S3StorageConfigurationProperties configuration;

    /**
     * Fetches object metadata from S3 using a raw HTTP GET.
     *
     * @param path the S3 object path (already sanitized)
     * @return S3GetResponse (includes metadata used in put)
     * @throws IOException if the request fails or the HTTP response status is not 2xx
     */
    @SuppressWarnings("UastIncorrectHttpHeaderInspection")
    public S3GetResponse get(final String path) throws IOException {
        final Instant now = Instant.now();
        final String amzDate = S3ClientUtils.amzDate(now);
        final String dateStamp = S3ClientUtils.dateStamp(now);
        final URI endpoint = this.configuration.getServiceEndpoint();
        final String host = endpoint.getHost();
        final String canonicalUri =
            "/" + S3ClientUtils.encodePath(this.configuration.getBucket()) + "/" + S3ClientUtils.encodePath(
                S3ClientUtils.stripLeadingSlash(path));
        final String payloadHash = S3ClientUtils.sha256Hex(new byte[0]);
        final String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
        final String canonicalHeaders =
            "host:" + host + "\n" + "x-amz-content-sha256:" + payloadHash + "\n" + "x-amz-date:" + amzDate + "\n";
        final String canonicalRequest =
            "GET\n" + canonicalUri + "\n" + "\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;

        final String authorization =
            S3ClientUtils.buildAuthorizationHeader(this.configuration, amzDate, dateStamp, signedHeaders,
                canonicalRequest);

        final HttpRequest request = HttpRequest
            .newBuilder(URI.create(
                S3ClientUtils.stripTrailingSlash(this.configuration.getServiceEndpoint().toString()) + canonicalUri))
            .header("x-amz-content-sha256", payloadHash)
            .header("x-amz-date", amzDate)
            .header("Authorization", authorization)
            .GET()
            .build();

        try {
            final HttpResponse<String> response =
                this.httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < HttpStatus.OK.value()
                || response.statusCode() >= HttpStatus.MULTIPLE_CHOICES.value()) {
                throw new IOException("S3 get failed with status " + response.statusCode() + ": " + response.body());
            }

            final HttpHeaders headers = response.headers();
            return new S3GetResponse(headers.firstValue("ETag").orElse(""),
                headers.firstValue("Content-Type").orElse("application/octet-stream"),
                headers.firstValueAsLong("Content-Length").orElse(0L), extractMetadata(headers),
                parseLastModified(headers.firstValue("Last-Modified").orElseThrow()));
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("S3 GET interrupted for path: " + path, ex);
        }
    }

    private static Instant parseLastModified(final String value) {
        try {
            return ZonedDateTime.parse(value, RFC_1123).toInstant();
        } catch (final DateTimeParseException ex) {
            return Instant.now();
        }
    }

    private static Map<String, String> extractMetadata(final HttpHeaders headers) {
        final Map<String, String> metadata = new LinkedHashMap<>();
        for (final Map.Entry<String, List<String>> entry : headers.map().entrySet()) {
            final String name = entry.getKey().toLowerCase(Locale.ENGLISH);
            if (name.startsWith("x-amz-meta-") && !entry.getValue().isEmpty()) {
                final String key = entry.getKey().substring("x-amz-meta-".length());
                final String decoded = URLDecoder.decode(entry.getValue().getFirst(), StandardCharsets.UTF_8);
                metadata.put(key, decoded);
            }
        }
        return metadata;
    }

}

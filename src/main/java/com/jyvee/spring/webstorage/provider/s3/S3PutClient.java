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

/**
 * Uploads objects to S3-compatible storage using raw HTTP with AWS SigV4 request signing.
 * No external dependencies are used - only the Java standard library.
 */
@RequiredArgsConstructor
public class S3PutClient {

    private final HttpClient httpClient;

    private final S3StorageConfigurationProperties configuration;

    /**
     * Uploads bytes to S3 using a raw HTTP PUT with AWS SigV4 signing.
     *
     * @param path               the S3 object path (already sanitized)
     * @param contentType        MIME type of the payload
     * @param payload            raw bytes to upload
     * @param urlEncodedMetadata metadata with URL-encoded values; stored as {@code x-amz-meta-} headers
     * @return S3PutResponse
     * @throws IOException if the upload fails or the HTTP response status is not 200
     */
    @SuppressWarnings("UastIncorrectHttpHeaderInspection")
    public S3PutResponse put(final String path, final String contentType, final byte[] payload,
                             final Map<String, String> urlEncodedMetadata) throws IOException {
        final Instant now = Instant.now();
        final String amzDate = S3ClientUtils.amzDate(now);
        final String dateStamp = S3ClientUtils.dateStamp(now);
        final URI endpoint = this.configuration.getServiceEndpoint();
        final String host = endpoint.getHost();
        final String canonicalUri =
            "/" + S3ClientUtils.encodePath(this.configuration.getBucket()) + "/" + S3ClientUtils.encodePath(
                S3ClientUtils.stripLeadingSlash(path));
        final String payloadHash = S3ClientUtils.sha256Hex(payload);
        final String signedHeaders = "content-type;host;x-amz-content-sha256;x-amz-date";
        final String canonicalHeaders =
            "content-type:" + contentType + "\n" + "host:" + host + "\n" + "x-amz-content-sha256:" + payloadHash + "\n"
            + "x-amz-date:" + amzDate + "\n";
        final String canonicalRequest =
            "PUT\n" + canonicalUri + "\n" + "\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;

        final String authorization =
            S3ClientUtils.buildAuthorizationHeader(this.configuration, amzDate, dateStamp, signedHeaders,
                canonicalRequest);

        final HttpRequest.Builder requestBuilder = HttpRequest
            .newBuilder(URI.create(
                S3ClientUtils.stripTrailingSlash(this.configuration.getServiceEndpoint().toString()) + canonicalUri))
            .header("Content-Type", contentType)
            .header("x-amz-content-sha256", payloadHash)
            .header("x-amz-date", amzDate)
            .header("Authorization", authorization);

        for (final Map.Entry<String, String> entry : urlEncodedMetadata.entrySet()) {
            requestBuilder.header("x-amz-meta-" + entry.getKey().toLowerCase(Locale.ENGLISH), entry.getValue());
        }

        final HttpRequest httpRequest = requestBuilder.PUT(HttpRequest.BodyPublishers.ofByteArray(payload)).build();

        try {
            final HttpResponse<String> response =
                this.httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < HttpStatus.OK.value()
                || response.statusCode() >= HttpStatus.MULTIPLE_CHOICES.value()) {
                throw new IOException("S3 put failed with status " + response.statusCode() + ": " + response.body());
            }

            final HttpHeaders headers = response.headers();
            return new S3PutResponse(headers.firstValue("ETag").orElse(""));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("S3 PUT interrupted for path: " + path, e);
        }
    }

}

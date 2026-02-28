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
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Copies objects in S3-compatible storage using raw HTTP request.
 * No external dependencies are used - only the Java standard library.
 */
@RequiredArgsConstructor
public class S3CopyClient {

    private final HttpClient httpClient;

    private final S3StorageConfigurationProperties configuration;

    /**
     * Copies one object to another key in the same S3 bucket using a raw HTTP PUT + x-amz-copy-source.
     *
     * @param fromKey source S3 key (already sanitized)
     * @param toKey   destination S3 key (already sanitized)
     * @throws IOException if the copy fails or the HTTP response status is not 2xx
     */
    @SuppressWarnings("UastIncorrectHttpHeaderInspection")
    public void copy(final String fromKey, final String toKey) throws IOException {
        final Instant now = Instant.now();
        final String amzDate = S3ClientUtils.amzDate(now);
        final String dateStamp = S3ClientUtils.dateStamp(now);
        final String canonicalUri =
            "/" + S3ClientUtils.encodePath(this.configuration.getBucket()) + "/" + S3ClientUtils.encodePath(
                S3ClientUtils.stripLeadingSlash(toKey));
        final String payloadHash = S3ClientUtils.sha256Hex(new byte[0]);
        final String copySource =
            "/" + S3ClientUtils.encodePath(this.configuration.getBucket()) + "/" + S3ClientUtils.encodePath(
                S3ClientUtils.stripLeadingSlash(fromKey));
        final String host = this.configuration.getServiceEndpoint().getHost();
        final String signedHeaders = "host;x-amz-content-sha256;x-amz-copy-source;x-amz-date";
        final String canonicalHeaders =
            "host:" + host + "\n" + "x-amz-content-sha256:" + payloadHash + "\n" + "x-amz-copy-source:" + copySource
            + "\n" + "x-amz-date:" + amzDate + "\n";
        final String canonicalRequest =
            "PUT\n" + canonicalUri + "\n" + "\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;

        final String authorization =
            S3ClientUtils.buildAuthorizationHeader(this.configuration, amzDate, dateStamp, signedHeaders,
                canonicalRequest);

        final HttpRequest request = HttpRequest
            .newBuilder(URI.create(
                S3ClientUtils.stripTrailingSlash(this.configuration.getServiceEndpoint().toString()) + canonicalUri))
            .header("x-amz-content-sha256", payloadHash)
            .header("x-amz-copy-source", copySource)
            .header("x-amz-date", amzDate)
            .header("Authorization", authorization)
            .PUT(HttpRequest.BodyPublishers.noBody())
            .build();

        try {
            final HttpResponse<String> response =
                this.httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < HttpStatus.OK.value()
                || response.statusCode() >= HttpStatus.MULTIPLE_CHOICES.value()) {
                throw new IOException("S3 copy failed with status " + response.statusCode() + ": " + response.body());
            }
            ensureNoCopyError(response.body());
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("S3 COPY interrupted", ex);
        }

    }

    private static void ensureNoCopyError(@Nullable final String body) throws IOException {
        if (body == null || body.isBlank()) {
            return;
        }

        final Document document = S3ClientUtils.parseSecureXml(body, "Failed to parse S3 copy XML response");
        final NodeList errorNodes = document.getElementsByTagNameNS("*", "Error");
        if (errorNodes.getLength() == 0) {
            return;
        }

        final Element errorElement = (Element) errorNodes.item(0);
        final String code = S3ClientUtils.textContent(errorElement, "Code").orElse("UnknownError");
        final String message = S3ClientUtils.textContent(errorElement, "Message").orElse("No message");
        throw new IOException("S3 copy failed: [" + code + "] " + message);
    }

}

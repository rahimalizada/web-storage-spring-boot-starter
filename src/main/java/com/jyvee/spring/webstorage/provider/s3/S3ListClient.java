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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lists objects in S3-compatible storage using raw HTTP request.
 * No external dependencies are used - only the Java standard library.
 * Fetches all pages internally and returns a complete list of matching keys.
 */
@RequiredArgsConstructor
public class S3ListClient {

    private final HttpClient httpClient;

    private final S3StorageConfigurationProperties configuration;

    /**
     * Fetches all object keys from S3 whose keys begin with {@code prefix}, paginating internally
     * with a page size of 1000.
     *
     * @param prefix key prefix filter; empty or null returns all keys
     * @return all matching keys, collected across all pages
     * @throws IOException if the request fails or the HTTP response status is not 2xx
     */
    public List<String> list(@Nullable final String prefix) throws IOException {
        return list(prefix, 1000);
    }

    /**
     * Fetches all object keys from S3 whose keys begin with {@code prefix}, paginating internally.
     *
     * @param prefix  key prefix filter; empty or null returns all keys
     * @param maxKeys page size sent to S3 per request
     * @return all matching keys, collected across all pages
     * @throws IOException if the request fails or the HTTP response status is not 2xx
     */
    List<String> list(@Nullable final String prefix, final int maxKeys) throws IOException {
        if (maxKeys <= 0) {
            throw new IllegalArgumentException("maxKeys must be greater than zero");
        }

        final List<String> keys = new ArrayList<>();
        String continuationToken = null;

        do {
            final ListResponse page = fetchPage(prefix, maxKeys, continuationToken);
            keys.addAll(page.keys());
            continuationToken = page.nextContinuationToken();
        } while (continuationToken != null);

        return keys;
    }

    @SuppressWarnings("UastIncorrectHttpHeaderInspection")
    private ListResponse fetchPage(@Nullable final String prefix, final int maxKeys,
                                   @Nullable final String continuationToken) throws IOException {
        final Instant now = Instant.now();
        final String amzDate = S3ClientUtils.amzDate(now);
        final String dateStamp = S3ClientUtils.dateStamp(now);

        final String canonicalUri = "/" + S3ClientUtils.encodePath(this.configuration.getBucket());
        final Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("list-type", "2");
        queryParams.put("max-keys", Integer.toString(maxKeys));
        if (prefix != null && !prefix.isEmpty()) {
            queryParams.put("prefix", prefix);
        }
        if (continuationToken != null && !continuationToken.isBlank()) {
            queryParams.put("continuation-token", continuationToken);
        }

        final String canonicalQuery = S3ClientUtils.canonicalQuery(queryParams);
        final String payloadHash = S3ClientUtils.sha256Hex(new byte[0]);
        final String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
        final String host = this.configuration.getServiceEndpoint().getHost();
        final String canonicalHeaders =
            "host:" + host + "\n" + "x-amz-content-sha256:" + payloadHash + "\n" + "x-amz-date:" + amzDate + "\n";
        final String canonicalRequest =
            "GET\n" + canonicalUri + "\n" + canonicalQuery + "\n" + canonicalHeaders + "\n" + signedHeaders + "\n"
            + payloadHash;

        final String authorization =
            S3ClientUtils.buildAuthorizationHeader(this.configuration, amzDate, dateStamp, signedHeaders,
                canonicalRequest);

        final String requestUri =
            S3ClientUtils.stripTrailingSlash(this.configuration.getServiceEndpoint().toString()) + canonicalUri + (
                canonicalQuery.isEmpty() ? "" : "?" + canonicalQuery);
        final HttpRequest request = HttpRequest
            .newBuilder(URI.create(requestUri))
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
                throw new IOException("S3 list failed with status " + response.statusCode() + ": " + response.body());
            }
            return parseListResponse(response.body());
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("S3 LIST interrupted", ex);
        }
    }

    private static ListResponse parseListResponse(final String body) throws IOException {
        final Document document = S3ClientUtils.parseSecureXml(body, "Failed to parse S3 list XML response");
        final List<String> keys = new ArrayList<>();

        final NodeList contentsNodes = document.getElementsByTagNameNS("*", "Contents");
        for (int idx = 0; idx < contentsNodes.getLength(); idx++) {
            final Node contentsNode = contentsNodes.item(idx);
            if (contentsNode instanceof final Element contentsElement) {
                S3ClientUtils.textContent(contentsElement, "Key").ifPresent(keys::add);
            }
        }

        final boolean truncated = S3ClientUtils
            .textContent(document.getDocumentElement(), "IsTruncated")
            .map(Boolean::parseBoolean)
            .orElse(false);
        final String nextToken =
            S3ClientUtils.textContent(document.getDocumentElement(), "NextContinuationToken").orElse(null);

        if (truncated && (nextToken == null || nextToken.isBlank())) {
            throw new IOException("S3 list response is truncated but NextContinuationToken is missing");
        }

        return new ListResponse(keys, truncated ? nextToken : null);
    }

    private record ListResponse(List<String> keys, @Nullable String nextContinuationToken) {}

}

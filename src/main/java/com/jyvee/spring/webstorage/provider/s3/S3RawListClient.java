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
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import static com.jyvee.spring.webstorage.provider.s3.S3RawEncodingUtil.buildCanonicalQueryString;
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
 * Lists objects in S3-compatible storage using raw HTTP with AWS SigV4 request signing.
 * No external dependencies are used - only the Java standard library.
 * Fetches all pages internally and returns a complete list of matching keys.
 */
@RequiredArgsConstructor
public class S3RawListClient {

    private final HttpClient httpClient;

    private final S3StorageConfigurationProperties configuration;

    /**
     * Fetches all object keys from S3 whose keys begin with {@code prefix}, paginating internally
     * with a page size of 1000.
     *
     * @param prefix key prefix filter; empty or null returns all keys
     * @return all matching keys, collected across all pages
     * @throws IOException if the request fails or the HTTP response status is not 200
     */
    public List<String> listObjects(@Nullable final String prefix) throws IOException {
        return listObjects(prefix, 1000);
    }

    /**
     * Fetches all object keys from S3 whose keys begin with {@code prefix}, paginating internally.
     *
     * @param prefix  key prefix filter; empty or null returns all keys
     * @param maxKeys page size sent to S3 per request (1-1000)
     * @return all matching keys, collected across all pages
     * @throws IOException if the request fails or the HTTP response status is not 200
     */
    List<String> listObjects(@Nullable final String prefix, final int maxKeys) throws IOException {
        final List<String> allKeys = new ArrayList<>();
        String continuationToken = null;
        do {
            final S3RawListResponse page = fetchPage(prefix, continuationToken, maxKeys);
            allKeys.addAll(page.keys());
            continuationToken = page.isTruncated() ? page.nextContinuationToken() : null;
        } while (continuationToken != null);
        return allKeys;
    }

    private S3RawListResponse fetchPage(@Nullable final String prefix, @Nullable final String continuationToken,
                                        final int maxKeys) throws IOException {
        final Instant now = Instant.now();
        final String datetime = formatDatetime(now);
        final String date = formatDate(now);

        final URI serviceEndpoint = this.configuration.getServiceEndpoint();
        final String host = buildHost(serviceEndpoint);
        final String bucket = this.configuration.getBucket();

        // Build sorted query parameters (TreeMap guarantees lexicographic order for canonical query string)
        final SortedMap<String, String> queryParams = new TreeMap<>();
        if (continuationToken != null) {
            queryParams.put("continuation-token", continuationToken);
        }
        queryParams.put("list-type", "2");
        queryParams.put("max-keys", String.valueOf(maxKeys));
        if (prefix != null && !prefix.isEmpty()) {
            queryParams.put("prefix", prefix);
        }

        final String canonicalQueryString = buildCanonicalQueryString(queryParams);

        // Signing headers: host, x-amz-content-sha256, x-amz-date (sorted; no content-type, no acl)
        final SortedMap<String, String> signingHeaders = new TreeMap<>();
        signingHeaders.put("host", host);
        signingHeaders.put("x-amz-content-sha256", EMPTY_PAYLOAD_HASH);
        signingHeaders.put("x-amz-date", datetime);

        final String signedHeaders = String.join(";", signingHeaders.keySet());
        final String canonicalPath = "/" + bucket;

        final String canonicalRequest =
            "GET\n" + canonicalPath + "\n" + canonicalQueryString + "\n" + buildCanonicalHeaders(signingHeaders)
            + signedHeaders + "\n" + EMPTY_PAYLOAD_HASH;

        final String credentialScope = date + "/" + this.configuration.getRegion() + "/" + SERVICE + "/" + TERMINATOR;
        final String stringToSign = ALGORITHM + "\n" + datetime + "\n" + credentialScope + "\n" + sha256Hex(
            canonicalRequest.getBytes(StandardCharsets.UTF_8));

        final byte[] signingKey =
            deriveSigningKey(this.configuration.getSecret(), date, this.configuration.getRegion());
        final String signature = hmacSha256Hex(signingKey, stringToSign);

        final String authorization =
            ALGORITHM + " Credential=" + this.configuration.getKey() + "/" + credentialScope + ", SignedHeaders="
            + signedHeaders + ", Signature=" + signature;

        final URI requestUri = URI.create(serviceEndpoint + "/" + bucket + "?" + canonicalQueryString);
        final HttpRequest.Builder builder = HttpRequest.newBuilder(requestUri).GET();

        // 'host' is a restricted header managed automatically by HttpClient from the URI
        applyHeadersExceptHost(builder, signingHeaders);
        builder.header("authorization", authorization);

        try {
            final HttpResponse<byte[]> response =
                this.httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != HttpStatus.OK.value()) {
                throw new IOException("S3 LIST failed with HTTP " + response.statusCode() + " for bucket: " + bucket);
            }
            return parseListResponse(response.body());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("S3 LIST interrupted for bucket: " + bucket, e);
        }
    }

    private static S3RawListResponse parseListResponse(final byte[] body) throws IOException {
        final Document document;
        try {
            final DocumentBuilder builder = S3RawXmlUtil.newSecureDocumentBuilder();
            document = builder.parse(new ByteArrayInputStream(body));
        } catch (final ParserConfigurationException | SAXException e) {
            throw new IOException("Failed to parse S3 list response XML", e);
        }

        final NodeList keyNodes = document.getElementsByTagName("Key");
        final List<String> keys = new ArrayList<>(keyNodes.getLength());
        for (int i = 0; i < keyNodes.getLength(); i++) {
            keys.add(keyNodes.item(i).getTextContent());
        }

        final NodeList truncatedNodes = document.getElementsByTagName("IsTruncated");
        final boolean isTruncated =
            truncatedNodes.getLength() > 0 && "true".equalsIgnoreCase(truncatedNodes.item(0).getTextContent());

        String nextContinuationToken = null;
        if (isTruncated) {
            final NodeList tokenNodes = document.getElementsByTagName("NextContinuationToken");
            if (tokenNodes.getLength() > 0) {
                nextContinuationToken = tokenNodes.item(0).getTextContent();
            }
        }

        return new S3RawListResponse(keys, isTruncated, nextContinuationToken);
    }

}

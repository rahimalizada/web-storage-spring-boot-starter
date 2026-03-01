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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Deletes objects from S3-compatible storage using raw HTTP request.
 * No external dependencies are used - only the Java standard library.
 */
@RequiredArgsConstructor
public class S3DeleteClient {

    private final HttpClient httpClient;

    private final S3StorageConfigurationProperties configuration;

    /**
     * Deletes objects from S3 using Multi-Object Delete ({@code POST ?delete}).
     *
     * @param keys the S3 keys to delete
     * @throws IOException if the request fails, the HTTP status is not 2xx, or S3 reports delete errors
     */
    @SuppressWarnings("UastIncorrectHttpHeaderInspection")
    public void delete(final Collection<String> keys) throws IOException {
        if (keys.isEmpty()) {
            return;
        }

        final String body = buildDeleteBody(keys);
        final byte[] payload = body.getBytes(StandardCharsets.UTF_8);

        final Instant now = Instant.now();
        final String amzDate = S3ClientUtils.amzDate(now);
        final String dateStamp = S3ClientUtils.dateStamp(now);
        final String canonicalUri = "/" + S3ClientUtils.encodePath(this.configuration.getBucket());
        final String canonicalQuery = "delete=";
        final String contentType = "application/xml";
        final String contentMd5 = S3ClientUtils.md5Base64(payload);
        final String payloadHash = S3ClientUtils.sha256Hex(payload);
        final String host = this.configuration.getServiceEndpoint().getHost();
        final String signedHeaders = "content-md5;content-type;host;x-amz-content-sha256;x-amz-date";
        final String canonicalHeaders =
            "content-md5:" + contentMd5 + "\n" + "content-type:" + contentType + "\n" + "host:" + host + "\n"
            + "x-amz-content-sha256:" + payloadHash + "\n" + "x-amz-date:" + amzDate + "\n";
        final String canonicalRequest =
            "POST\n" + canonicalUri + "\n" + canonicalQuery + "\n" + canonicalHeaders + "\n" + signedHeaders + "\n"
            + payloadHash;

        final String authorization =
            S3ClientUtils.buildAuthorizationHeader(this.configuration, amzDate, dateStamp, signedHeaders,
                canonicalRequest);

        final HttpRequest request = HttpRequest
            .newBuilder(URI.create(
                S3ClientUtils.stripTrailingSlash(this.configuration.getServiceEndpoint().toString()) + canonicalUri
                + "?delete="))
            .header("Content-Type", contentType)
            .header("Content-MD5", contentMd5)
            .header("x-amz-content-sha256", payloadHash)
            .header("x-amz-date", amzDate)
            .header("Authorization", authorization)
            .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
            .build();

        try {
            final HttpResponse<String> response =
                this.httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < HttpStatus.OK.value()
                || response.statusCode() >= HttpStatus.MULTIPLE_CHOICES.value()) {
                throw new IOException("S3 delete failed with status " + response.statusCode() + ": " + response.body());
            }
            ensureNoDeleteErrors(response.body());
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("S3 DELETE interrupted", ex);
        }
    }

    @SuppressWarnings("HttpUrlsUsage")
    private static String buildDeleteBody(final Collection<String> keys) {
        final StringBuilder builder = new StringBuilder();
        builder.append("<Delete xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">");
        for (final String key : keys) {
            builder.append("<Object><Key>").append(xmlEscape(key)).append("</Key></Object>");
        }
        builder.append("</Delete>");
        return builder.toString();
    }

    private static void ensureNoDeleteErrors(final String body) throws IOException {
        final Document document = S3ClientUtils.parseSecureXml(body, "Failed to parse S3 delete XML response");
        final NodeList errorNodes = document.getElementsByTagNameNS("*", "Error");
        if (errorNodes.getLength() == 0) {
            return;
        }

        final List<String> errors = new ArrayList<>();
        for (int idx = 0; idx < errorNodes.getLength(); idx++) {
            final Element errorElement = (Element) errorNodes.item(idx);
            final String key = S3ClientUtils.textContent(errorElement, "Key").orElse("(unknown-key)");
            final String code = S3ClientUtils.textContent(errorElement, "Code").orElse("UnknownError");
            final String message = S3ClientUtils.textContent(errorElement, "Message").orElse("No message");
            errors.add(key + " [" + code + "]: " + message);
        }
        throw new IOException("S3 delete completed with errors: " + String.join("; ", errors));
    }

    private static String xmlEscape(final String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

}

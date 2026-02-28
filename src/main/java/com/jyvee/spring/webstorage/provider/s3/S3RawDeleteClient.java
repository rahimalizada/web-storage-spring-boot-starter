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
import java.util.Collection;
import java.util.List;
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
 * Deletes objects from S3-compatible storage using raw HTTP with AWS SigV4 request signing.
 * No external dependencies are used - only the Java standard library.
 */
@RequiredArgsConstructor
public class S3RawDeleteClient {

    private static final String CONTENT_TYPE = "application/xml";

    private final HttpClient httpClient;

    private final S3StorageConfigurationProperties configuration;

    /**
     * Deletes objects from S3 using Multi-Object Delete ({@code POST ?delete}) with AWS SigV4 signing.
     *
     * @param keys the S3 keys to delete
     * @throws IOException if the request fails, the HTTP status is not 200, or S3 reports delete errors
     */
    public void deleteObjects(final Collection<String> keys) throws IOException {
        if (keys.isEmpty()) {
            return;
        }

        final byte[] payload = buildDeleteXml(keys).getBytes(StandardCharsets.UTF_8);
        final String payloadHash = sha256Hex(payload);

        final Instant now = Instant.now();
        final String datetime = formatDatetime(now);
        final String date = formatDate(now);

        final URI serviceEndpoint = this.configuration.getServiceEndpoint();
        final String host = buildHost(serviceEndpoint);
        final String bucket = this.configuration.getBucket();

        final SortedMap<String, String> signingHeaders = new TreeMap<>();
        signingHeaders.put("content-type", CONTENT_TYPE);
        signingHeaders.put("host", host);
        signingHeaders.put("x-amz-content-sha256", payloadHash);
        signingHeaders.put("x-amz-date", datetime);

        final String signedHeaders = String.join(";", signingHeaders.keySet());
        final String canonicalPath = "/" + bucket;
        final String canonicalQueryString = "delete=";

        final String canonicalRequest =
            "POST\n" + canonicalPath + "\n" + canonicalQueryString + "\n" + buildCanonicalHeaders(signingHeaders)
            + signedHeaders + "\n" + payloadHash;

        final String credentialScope = date + "/" + this.configuration.getRegion() + "/" + SERVICE + "/" + TERMINATOR;
        final String stringToSign = ALGORITHM + "\n" + datetime + "\n" + credentialScope + "\n" + sha256Hex(
            canonicalRequest.getBytes(StandardCharsets.UTF_8));

        final byte[] signingKey =
            deriveSigningKey(this.configuration.getSecret(), date, this.configuration.getRegion());
        final String signature = hmacSha256Hex(signingKey, stringToSign);

        final String authorization =
            ALGORITHM + " Credential=" + this.configuration.getKey() + "/" + credentialScope + ", SignedHeaders="
            + signedHeaders + ", Signature=" + signature;

        final URI requestUri = URI.create(serviceEndpoint + "/" + bucket + "?delete");
        final HttpRequest.Builder builder =
            HttpRequest.newBuilder(requestUri).POST(HttpRequest.BodyPublishers.ofByteArray(payload));

        // 'host' is a restricted header managed automatically by HttpClient from the URI.
        applyHeadersExceptHost(builder, signingHeaders);
        builder.header("authorization", authorization);

        try {
            final HttpResponse<byte[]> response =
                this.httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != HttpStatus.OK.value()) {
                final String responseBody = new String(response.body(), StandardCharsets.UTF_8);
                throw new IOException(
                    "S3 DELETE failed with HTTP " + response.statusCode() + " for bucket: " + bucket + ". "
                    + responseBody);
            }

            final List<String> failedKeys = parseFailedKeys(response.body());
            if (!failedKeys.isEmpty()) {
                throw new IOException("S3 DELETE completed with errors for keys: " + String.join(", ", failedKeys));
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("S3 DELETE interrupted for bucket: " + bucket, e);
        }
    }

    private static List<String> parseFailedKeys(final byte[] responseBody) throws IOException {
        if (responseBody.length == 0) {
            return List.of();
        }

        final Document document;
        try {
            final DocumentBuilder builder = S3RawXmlUtil.newSecureDocumentBuilder();
            document = builder.parse(new ByteArrayInputStream(responseBody));
        } catch (final ParserConfigurationException | SAXException e) {
            throw new IOException("Failed to parse S3 delete response XML", e);
        }

        final NodeList errorNodes = document.getElementsByTagName("Error");
        final List<String> failedKeys = new ArrayList<>(errorNodes.getLength());
        for (int i = 0; i < errorNodes.getLength(); i++) {
            final Element errorNode = (Element) errorNodes.item(i);
            final String key = childText(errorNode);
            if (key.isBlank()) {
                failedKeys.add("<unknown>");
                continue;
            }
            failedKeys.add(key);
        }
        return failedKeys;
    }

    private static String childText(final Element element) {
        final NodeList nodes = element.getElementsByTagName("Key");
        if (nodes.getLength() == 0) {
            return "";
        }
        return nodes.item(0).getTextContent();
    }

    private static String buildDeleteXml(final Collection<String> keys) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<Delete><Quiet>true</Quiet>");
        for (final String key : keys) {
            sb.append("<Object><Key>").append(escapeXml(key)).append("</Key></Object>");
        }
        sb.append("</Delete>");
        return sb.toString();
    }

    private static String escapeXml(final String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

}

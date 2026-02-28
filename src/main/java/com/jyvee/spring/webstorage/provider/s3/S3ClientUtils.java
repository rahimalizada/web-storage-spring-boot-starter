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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

final class S3ClientUtils {

    private static final DateTimeFormatter AMZ_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter DATE_STAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private static final char[] HEX_LOWER = "0123456789abcdef".toCharArray();

    private static final char[] HEX_UPPER = "0123456789ABCDEF".toCharArray();

    private static final int LOW_NIBBLE_MASK = 0x0f;

    private static final String SERVICE = "s3";

    private static final String ALGORITHM = "AWS4-HMAC-SHA256";

    private S3ClientUtils() {}

    static String amzDate(final Instant now) {
        return AMZ_DATE_FORMAT.format(now);
    }

    static String dateStamp(final Instant now) {
        return DATE_STAMP_FORMAT.format(now);
    }

    static String buildAuthorizationHeader(final S3StorageConfigurationProperties configuration, final String amzDate,
                                           final String dateStamp, final String signedHeaders,
                                           final String canonicalRequest) {
        final String credentialScope = dateStamp + "/" + configuration.getRegion() + "/" + SERVICE + "/aws4_request";
        final String stringToSign = ALGORITHM + "\n" + amzDate + "\n" + credentialScope + "\n" + sha256Hex(
            canonicalRequest.getBytes(StandardCharsets.UTF_8));
        final byte[] signature = hmacSha256(signingKey(configuration.getSecret(), dateStamp, configuration.getRegion()),
            stringToSign.getBytes(StandardCharsets.UTF_8));
        return ALGORITHM + " Credential=" + configuration.getKey() + "/" + credentialScope + ", SignedHeaders="
               + signedHeaders + ", Signature=" + toHexLower(signature);
    }

    static String canonicalQuery(final Map<String, String> queryParams) {
        if (queryParams.isEmpty()) {
            return "";
        }
        return queryParams
            .entrySet()
            .stream()
            .map(entry -> Map.entry(encodeQueryComponent(entry.getKey()), encodeQueryComponent(entry.getValue())))
            .sorted(Map.Entry.<String, String>comparingByKey().thenComparing(Map.Entry::getValue))
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .reduce((first, second) -> first + "&" + second)
            .orElse("");
    }

    private static String encodeQueryComponent(final String value) {
        final StringBuilder encoded = new StringBuilder(value.length());
        int index = 0;
        while (index < value.length()) {
            final int codePoint = value.codePointAt(index);
            if (isUnreserved(codePoint)) {
                encoded.appendCodePoint(codePoint);
            } else {
                final byte[] bytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
                for (final byte singleByte : bytes) {
                    final int unsigned = singleByte & 0xff;
                    encoded.append('%').append(HEX_UPPER[unsigned >>> 4]).append(HEX_UPPER[unsigned & LOW_NIBBLE_MASK]);
                }
            }
            index += Character.charCount(codePoint);
        }
        return encoded.toString();
    }

    static String stripTrailingSlash(final String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    static String stripLeadingSlash(final String value) {
        if (value.startsWith("/")) {
            return value.substring(1);
        }
        return value;
    }

    static String encodePath(final String path) {
        final StringBuilder encoded = new StringBuilder(path.length());
        int index = 0;
        while (index < path.length()) {
            final int codePoint = path.codePointAt(index);
            if (isUnreserved(codePoint) || codePoint == '/') {
                encoded.appendCodePoint(codePoint);
            } else {
                final byte[] bytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
                for (final byte singleByte : bytes) {
                    final int unsigned = singleByte & 0xff;
                    encoded.append('%').append(HEX_UPPER[unsigned >>> 4]).append(HEX_UPPER[unsigned & LOW_NIBBLE_MASK]);
                }
            }
            index += Character.charCount(codePoint);
        }
        return encoded.toString();
    }

    static String sha256Hex(final byte[] bytes) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHexLower(digest.digest(bytes));
        } catch (@SuppressWarnings("OverlyBroadCatchBlock") final GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    static String md5Base64(final byte[] payload) {
        try {
            final MessageDigest md5 = MessageDigest.getInstance("MD5");
            return Base64.getEncoder().encodeToString(md5.digest(payload));
        } catch (@SuppressWarnings("OverlyBroadCatchBlock") final GeneralSecurityException ex) {
            throw new IllegalStateException("MD5 is not available", ex);
        }
    }

    @SuppressWarnings("HttpUrlsUsage")
    static Document parseSecureXml(final String body, final String parseErrorMessage) throws IOException {
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        } catch (final ParserConfigurationException | SAXException ex) {
            throw new IOException(parseErrorMessage, ex);
        }
    }

    static Optional<String> textContent(final Element element, final String localName) {
        final NodeList nodes = element.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            return Optional.empty();
        }
        final String value = nodes.item(0).getTextContent();
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private static boolean isUnreserved(final int codePoint) {
        return (codePoint >= 'A' && codePoint <= 'Z') || (codePoint >= 'a' && codePoint <= 'z') || (codePoint >= '0'
                                                                                                    && codePoint <= '9')
               || codePoint == '-' || codePoint == '_' || codePoint == '.' || codePoint == '~';
    }

    private static byte[] hmacSha256(final byte[] key, final byte[] value) {
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value);
        } catch (@SuppressWarnings("OverlyBroadCatchBlock") final GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is not available", ex);
        }
    }

    private static byte[] signingKey(final String secret, final String dateStamp, final String region) {
        final byte[] kSecret = ("AWS4" + secret).getBytes(StandardCharsets.UTF_8);
        final byte[] kDate = hmacSha256(kSecret, dateStamp.getBytes(StandardCharsets.UTF_8));
        final byte[] kRegion = hmacSha256(kDate, region.getBytes(StandardCharsets.UTF_8));
        final byte[] kService = hmacSha256(kRegion, SERVICE.getBytes(StandardCharsets.UTF_8));
        return hmacSha256(kService, "aws4_request".getBytes(StandardCharsets.UTF_8));
    }

    private static String toHexLower(final byte[] bytes) {
        final char[] chars = new char[bytes.length * 2];
        for (int idx = 0; idx < bytes.length; idx++) {
            final int value = bytes[idx] & 0xff;
            chars[idx * 2] = HEX_LOWER[value >>> 4];
            chars[idx * 2 + 1] = HEX_LOWER[value & LOW_NIBBLE_MASK];
        }
        return new String(chars);
    }

}

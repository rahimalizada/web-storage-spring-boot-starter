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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.SortedMap;

final class S3RawSigV4Util {

    static final String ALGORITHM = "AWS4-HMAC-SHA256";

    static final String SERVICE = "s3";

    static final String TERMINATOR = "aws4_request";

    static final String EMPTY_PAYLOAD_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private static final String HMAC_SHA256 = "HmacSHA256";

    private static final DateTimeFormatter DATETIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private S3RawSigV4Util() {}

    static String formatDatetime(final Instant instant) {
        return DATETIME_FORMATTER.format(instant);
    }

    static String formatDate(final Instant instant) {
        return DATE_FORMATTER.format(instant);
    }

    static String buildHost(final URI serviceEndpoint) {
        final int port = serviceEndpoint.getPort();
        return port == -1 ? serviceEndpoint.getHost() : serviceEndpoint.getHost() + ":" + port;
    }

    static String buildCanonicalHeaders(final SortedMap<String, String> headers) {
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, String> entry : headers.entrySet()) {
            sb.append(entry.getKey()).append(':').append(entry.getValue().strip()).append('\n');
        }
        return sb.toString();
    }

    static byte[] deriveSigningKey(final String secret, final String date, final String region) throws IOException {
        final byte[] kDate = hmacSha256(("AWS4" + secret).getBytes(StandardCharsets.UTF_8), date);
        final byte[] kRegion = hmacSha256(kDate, region);
        final byte[] kService = hmacSha256(kRegion, SERVICE);
        return hmacSha256(kService, TERMINATOR);
    }

    static String hmacSha256Hex(final byte[] key, final String data) throws IOException {
        return HexFormat.of().formatHex(hmacSha256(key, data));
    }

    static String sha256Hex(final byte[] data) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (final NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 not available", e);
        }
    }

    private static byte[] hmacSha256(final byte[] key, final String data) throws IOException {
        try {
            final Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key, HMAC_SHA256));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (final NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IOException("Failed to compute HMAC-SHA256", e);
        }
    }

}

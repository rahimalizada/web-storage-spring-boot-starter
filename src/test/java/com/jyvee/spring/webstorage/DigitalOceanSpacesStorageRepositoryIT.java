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

package com.jyvee.spring.webstorage;

import com.jyvee.spring.webstorage.configuration.S3StorageConfigurationProperties;
import com.jyvee.spring.webstorage.configuration.S3StorageConfigurationPropertiesImpl;
import com.jyvee.spring.webstorage.provider.s3.S3DeleteClient;
import com.jyvee.spring.webstorage.provider.s3.S3GetClient;
import com.jyvee.spring.webstorage.provider.s3.S3GetResponse;
import com.jyvee.spring.webstorage.provider.s3.S3PutClient;
import com.jyvee.spring.webstorage.provider.s3.S3PutResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

// @Disabled("Manual real DigitalOcean Spaces integration test. Provide env vars and enable to run.")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DigitalOceanSpacesStorageRepositoryIT {

    private static final String DO_SPACES_URI_TEMPLATE =
        "https://fra1.digitaloceanspaces.com/?region=fra1&bucket=greencactus&key=${TRADING_GC_DEV_S3_KEY}&secret=$"
        + "{TRADING_GC_DEV_S3_SECRET}&endpoint=https://greencactus.fra1.digitaloceanspaces.com";

    private static final String TEST_KEY = "it_do_spaces/raw_client_put_get_delete.txt";

    private static final byte[] PAYLOAD =
        "DigitalOcean Spaces raw client integration test".getBytes(StandardCharsets.UTF_8);

    private static final Map<String, String> METADATA = Map.of("source", "it");

    private S3PutClient putClient;

    private S3GetClient getClient;

    private S3DeleteClient deleteClient;

    @BeforeAll
    void beforeAll() {
        final String resolvedUri = resolveUriTemplate();
        final S3StorageConfigurationProperties configuration =
            new S3StorageConfigurationPropertiesImpl(URI.create(resolvedUri));
        final HttpClient httpClient = HttpClient.newHttpClient();
        this.putClient = new S3PutClient(httpClient, configuration);
        this.getClient = new S3GetClient(httpClient, configuration);
        this.deleteClient = new S3DeleteClient(httpClient, configuration);
    }

    @Test
    void deletePutGetDelete_validFlow_ok() throws IOException {
        // First delete if exists.
        this.deleteClient.delete(List.of(TEST_KEY));

        final S3PutResponse putResponse = this.putClient.put(TEST_KEY, "text/plain", PAYLOAD, METADATA);
        System.err.println(putResponse);
        Assertions.assertFalse(stripQuotes(putResponse.eTag()).isBlank(), "PUT ETag should not be blank");

        final S3GetResponse getResponse = this.getClient.get(TEST_KEY);
        System.err.println(getResponse);
        Assertions.assertEquals("text/plain", getResponse.contentType());
        Assertions.assertEquals(PAYLOAD.length, getResponse.contentLength());
        Assertions.assertEquals("it", getResponse.metadata().get("source"));
        Assertions.assertEquals(stripQuotes(putResponse.eTag()), stripQuotes(getResponse.eTag()));

        this.deleteClient.delete(List.of(TEST_KEY));
        Assertions.assertThrows(IOException.class, () -> this.getClient.get(TEST_KEY));
    }

    private static String resolveUriTemplate() {
        final String key = requiredSecret("TRADING_GC_DEV_S3_KEY");
        final String secret = requiredSecret("TRADING_GC_DEV_S3_SECRET");

        return DO_SPACES_URI_TEMPLATE
            .replace("${TRADING_GC_DEV_S3_KEY}", key)
            .replace("${TRADING_GC_DEV_S3_SECRET}", secret);
    }

    private static String requiredSecret(final String name) {
        final String envValue = System.getenv(name);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }

        final String systemPropertyValue = System.getProperty(name);
        if (systemPropertyValue != null && !systemPropertyValue.isBlank()) {
            return systemPropertyValue;
        }

        throw new IllegalStateException("Missing secret: " + name + ". Set it as environment variable or -D" + name);
    }

    private static String stripQuotes(final String eTag) {
        if (eTag.startsWith("\"") && eTag.endsWith("\"")) {
            return eTag.substring(1, eTag.length() - 1);
        }
        return eTag;
    }

}

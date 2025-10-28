/*
 * Copyright (c) 2023-2025 Rahim Alizada
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

package com.jyvee.spring.webstorage.configuration;

import com.jyvee.spring.test.webstorage.TestApplication;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@SpringBootTest(classes = TestApplication.class,
    properties = "web-storage.s3.uri=https://s3.url/?region=REGION&bucket=BUCKET&key=KEY&secret=SECRET&endpoint=https"
                 + "://site.url")
class S3StorageConfigurationPropertiesTest {

    @Autowired
    private S3StorageConfigurationProperties config;

    @Test
    void testMethod() {
        Assertions.assertEquals("https://s3.url", this.config.getServiceEndpoint().toString());
        Assertions.assertEquals("REGION", this.config.getRegion());
        Assertions.assertEquals("BUCKET", this.config.getBucket());
        Assertions.assertEquals("KEY", this.config.getKey());
        Assertions.assertEquals("SECRET", this.config.getSecret());
        Assertions.assertEquals("https://site.url", this.config.getEndpoint().toString());
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void parser_InvalidURI_Exception() {

        Assertions.assertThrows(NullPointerException.class, () -> new S3StorageConfigurationPropertiesImpl(null));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new S3StorageConfigurationPropertiesImpl(buildUri(null, null, null, null, null)));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new S3StorageConfigurationPropertiesImpl(buildUri(null,
                "BUCKET",
                "KEY",
                "SECRET",
                "https://site.url")));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new S3StorageConfigurationPropertiesImpl(buildUri(" ",
                "BUCKET",
                "KEY",
                "SECRET",
                "https://site.url")));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new S3StorageConfigurationPropertiesImpl(buildUri("REGION",
                null,
                "KEY",
                "SECRET",
                "https://site.url")));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new S3StorageConfigurationPropertiesImpl(buildUri("REGION",
                " ",
                "KEY",
                "SECRET",
                "https://site.url")));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new S3StorageConfigurationPropertiesImpl(buildUri("REGION",
                "BUCKET",
                null,
                "SECRET",
                "https://site.url")));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new S3StorageConfigurationPropertiesImpl(buildUri("REGION",
                "BUCKET",
                " ",
                "SECRET",
                "https://site.url")));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new S3StorageConfigurationPropertiesImpl(buildUri("REGION",
                "BUCKET",
                "KEY",
                null,
                "https://site.url")));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new S3StorageConfigurationPropertiesImpl(buildUri("REGION",
                "BUCKET",
                "KEY",
                " ",
                "https://site.url")));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new S3StorageConfigurationPropertiesImpl(buildUri("REGION", "BUCKET", "KEY", "SECRET", null)));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new S3StorageConfigurationPropertiesImpl(buildUri("REGION", "BUCKET", "KEY", "SECRET", " ")));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new S3StorageConfigurationPropertiesImpl(buildUri("REGION", "BUCKET", "KEY", "SECRET", "%")));

    }

    private static URI buildUri(@Nullable final String region, @Nullable final String bucket,
                                @Nullable final String key, @Nullable final String secret, @Nullable final String url) {
        final UriComponentsBuilder builder = UriComponentsBuilder.newInstance().scheme("https").host("s3.url");
        if (region != null) {
            builder.queryParam("region", region);
        }
        if (bucket != null) {
            builder.queryParam("bucket", bucket);
        }
        if (key != null) {
            builder.queryParam("key", key);
        }
        if (secret != null) {
            builder.queryParam("secret", secret);
        }
        if (url != null) {
            builder.queryParam("endpoint", url);
        }
        return builder.build().toUri();
    }

}

/*
 * Copyright (c) 2023-2026 Rahim Alizada
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

package com.jyvee.spring.webstorage.provider;

import com.adobe.testing.s3mock.testcontainers.S3MockContainer;
import com.jyvee.spring.test.webstorage.S3StorageProvider;
import com.jyvee.spring.webstorage.configuration.S3StorageConfigurationProperties;
import com.jyvee.spring.webstorage.configuration.S3StorageConfigurationPropertiesImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class S3StorageProviderTest extends AbstractStorageProviderTest {

    static {
        System.setProperty("software.amazon.awssdk.http.service.impl",
            "software.amazon.awssdk.http.apache.ApacheSdkHttpService");
    }

    @Container
    private static final S3MockContainer S3_MOCK = new S3MockContainer("latest").withInitialBuckets("bucket");

    @BeforeAll
    void beforeAll() {
        final S3StorageConfigurationProperties configurationProperties = new S3StorageConfigurationPropertiesImpl(
            URI.create(S3_MOCK.getHttpEndpoint() + "/?region=region&bucket=bucket&key=key&secret"
                       + "=secret&endpoint=https://site.url/base"));
        setProvider(new S3StorageProvider(configurationProperties));

    }

}

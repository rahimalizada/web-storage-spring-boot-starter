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

package com.jyvee.spring.webstorage;

import com.adobe.testing.s3mock.testcontainers.S3MockContainer;
import com.jyvee.spring.test.webstorage.S3StorageRepository;
import com.jyvee.spring.test.webstorage.TestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Slf4j
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = TestApplication.class)
class AbstractS3StorageRepositoryTest extends StorageRepositoryTest {

    static {
        System.setProperty("software.amazon.awssdk.http.service.impl",
            "software.amazon.awssdk.http.apache.ApacheSdkHttpService");
    }

    @Container
    private static final S3MockContainer S3_MOCK = new S3MockContainer("latest").withInitialBuckets("bucket");

    @DynamicPropertySource
    static void setProperties(final DynamicPropertyRegistry registry) {
        S3_MOCK.start();
        log.info("S3Mock test container uri: {}", S3_MOCK.getHttpEndpoint());
        registry.add("web-storage.s3.uri",
            () -> S3_MOCK.getHttpEndpoint() + "/?region=region&bucket=bucket&key=key&secret=secret&endpoint"
                  + "=https://site.url/base");
    }

    AbstractS3StorageRepositoryTest(@Autowired final S3StorageRepository service) {
        super(service);
    }

}

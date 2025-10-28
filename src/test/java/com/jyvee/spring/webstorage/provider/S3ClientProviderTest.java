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

package com.jyvee.spring.webstorage.provider;

import com.jyvee.spring.webstorage.configuration.S3StorageConfigurationProperties;
import com.jyvee.spring.webstorage.configuration.S3StorageConfigurationPropertiesImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

class S3ClientProviderTest {

    @Test
    void getS3Client_validArgs_Ok() {
        final S3StorageConfigurationProperties configurationProperties =
            new S3StorageConfigurationPropertiesImpl(URI.create(
                "https://s3.url/?region=region&bucket=bucket&key=key&secret=secret&endpoint=https://site.url/base"));
        final S3Client s3Client = new S3ClientProvider().getS3Client(configurationProperties);
        Assertions.assertNotNull(s3Client);
    }

}

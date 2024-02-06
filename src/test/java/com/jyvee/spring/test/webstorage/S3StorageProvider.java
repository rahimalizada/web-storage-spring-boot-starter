/*
 * Copyright (c) 2023-2024 Rahim Alizada
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

package com.jyvee.spring.test.webstorage;

import com.jyvee.spring.webstorage.configuration.S3StorageConfigurationProperties;
import com.jyvee.spring.webstorage.provider.S3ClientProvider;
import lombok.Getter;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

@Getter
public class S3StorageProvider implements com.jyvee.spring.webstorage.provider.S3StorageProvider<WebFile> {

    private final S3StorageConfigurationProperties configuration;

    private final S3Client s3Client;

    public S3StorageProvider(final S3StorageConfigurationProperties configuration) {
        this.configuration = configuration;
        this.s3Client = new S3ClientProvider().getS3Client(configuration);
    }

    @Override
    public WebFile newInstance(final URI uri, final String storageId, final String path, final String contentType,
                               final long size, final String checksum, final Map<String, String> metadata,
                               final Instant timestamp) {
        return new WebFile(uri, storageId, path, contentType, size, checksum, metadata, timestamp);
    }

}

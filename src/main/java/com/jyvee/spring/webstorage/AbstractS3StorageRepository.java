/*
 * Copyright (c) 2023 Rahim Alizada
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
import com.jyvee.spring.webstorage.provider.DefaultStoragePathProvider;
import com.jyvee.spring.webstorage.provider.S3StorageProvider;
import com.jyvee.spring.webstorage.provider.StoragePathProvider;
import com.jyvee.spring.webstorage.validator.StorageValidator;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.List;

@Getter
public abstract class AbstractS3StorageRepository<T> implements S3StorageProvider<T>, StorageRepository<T, S3StorageConfigurationProperties> {

    @Autowired
    private S3Client s3Client;

    @Autowired
    private S3StorageConfigurationProperties configuration;

    @Autowired(required = false)
    private List<StorageValidator> validators = List.of();

    @Autowired(required = false)
    private StoragePathProvider storagePathProvider = new DefaultStoragePathProvider();

}

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

package com.jyvee.spring.test.webstorage;

import com.jyvee.spring.webstorage.configuration.LocalStorageConfigurationProperties;
import lombok.Getter;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

@Getter
@SuppressWarnings("ClassCanBeRecord")
public class LocalStorageProvider implements com.jyvee.spring.webstorage.provider.LocalStorageProvider<WebFile> {

    private final LocalStorageConfigurationProperties configuration;

    public LocalStorageProvider(final LocalStorageConfigurationProperties configuration) {
        this.configuration = configuration;
    }

    @Override
    public WebFile newInstance(final URI uri, final String storageId, final String path, final String contentType, final long size,
        final String checksum, final Map<String, String> metadata, final Instant timestamp) {
        return new WebFile(uri, storageId, path, contentType, size, checksum, metadata, timestamp);
    }

}

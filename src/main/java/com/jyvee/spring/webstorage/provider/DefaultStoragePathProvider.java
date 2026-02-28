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

import org.springframework.web.util.UriComponentsBuilder;

import java.nio.file.Path;

public class DefaultStoragePathProvider implements StoragePathProvider {

    @Override
    public String getStoragePath(final Path basePath, final Path relativePath, final byte[] payload) {
        final String checksum = StorageProviderUtil.md5(payload);
        final UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        basePath.iterator().forEachRemaining(path -> builder.path(path.toString()).path("/"));
        if (relativePath.getParent() != null) {
            relativePath.getParent().iterator().forEachRemaining(path -> builder.path(path.toString()).path("/"));
        }
        return builder
            .pathSegment(checksum.substring(0, 1), checksum.substring(1, 2), checksum,
                relativePath.getFileName().toString())
            .build()
            .toUriString();
    }

}

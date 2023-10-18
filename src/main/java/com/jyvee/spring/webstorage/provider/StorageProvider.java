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

package com.jyvee.spring.webstorage.provider;

import com.jyvee.spring.webstorage.configuration.StorageConfigurationProperties;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface StorageProvider<T, S extends StorageConfigurationProperties> {

    S getConfiguration();

    @SuppressWarnings("checkstyle:ParameterNumber")
    T newInstance(URI uri, String storageId, String path, String contentType, long size, String checksum, Map<String, String> metadata,
        Instant timestamp);

    List<String> list(String path) throws IOException;

    T save(String path, String contentType, byte[] payload, Map<String, String> metadata) throws IOException;

    T load(String path) throws IOException;

    void delete(Collection<String> paths) throws IOException;

    void delete(String path) throws IOException;

    void copy(String fromPath, String toPath) throws IOException;

    void move(String fromPath, String toPath) throws IOException;

}

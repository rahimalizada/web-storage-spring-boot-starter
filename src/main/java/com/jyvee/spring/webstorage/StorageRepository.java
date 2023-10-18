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

import com.jyvee.spring.webstorage.configuration.FileType;
import com.jyvee.spring.webstorage.configuration.StorageConfigurationProperties;
import com.jyvee.spring.webstorage.provider.StoragePathProvider;
import com.jyvee.spring.webstorage.provider.StorageProvider;
import com.jyvee.spring.webstorage.validator.StorageValidator;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface StorageRepository<T, S extends StorageConfigurationProperties> extends StorageProvider<T, S> {

    default List<T> save(final FileType fileType, final Collection<? extends MultipartFile> multipartFiles) throws IOException {
        if (multipartFiles.isEmpty()) {
            throw new IllegalArgumentException("Files are missing");
        }

        final List<T> httpFiles = new ArrayList<>();
        for (final MultipartFile multipartFile : multipartFiles) {
            final String contentType =
                Optional.ofNullable(multipartFile.getContentType()).orElseThrow(() -> new IllegalArgumentException("ContentType is missing"));
            final String fileName =
                Optional.ofNullable(multipartFile.getOriginalFilename()).orElseThrow(() -> new IllegalArgumentException("File name is missing"));
            final T httpFile = save(fileType, fileName, contentType, multipartFile.getBytes(), new LinkedHashMap<>());
            httpFiles.add(httpFile);
        }
        return httpFiles;
    }

    default T save(final FileType fileType, final String path, final String contentType, final byte[] payload,
        final Map<String, String> metadata) throws IOException {

        final Path relativePath = Paths.get(path);

        final String storagePath = getStoragePathProvider().getStoragePath(fileType.getPath(), relativePath, payload);

        final Map<String, String> updatedMetadata = new LinkedHashMap<>(metadata);
        updatedMetadata.put("filename", relativePath.getFileName().toString());
        updatedMetadata.put("fileType", fileType.name());
        getValidators().stream().map(validator -> validator.validate(fileType, contentType, payload)).forEach(updatedMetadata::putAll);

        return this.save(storagePath, contentType, payload, updatedMetadata);
    }

    List<StorageValidator> getValidators();

    StoragePathProvider getStoragePathProvider();

}

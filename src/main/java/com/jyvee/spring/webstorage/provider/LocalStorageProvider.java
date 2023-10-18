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

import com.jyvee.spring.webstorage.configuration.LocalStorageConfigurationProperties;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public interface LocalStorageProvider<T> extends StorageProvider<T, LocalStorageConfigurationProperties> {

    String MD5_ATTRIBUTE = "md5";

    String CONTENT_TYPE_ATTRIBUTE = "contentType";

    String METADATA_ATTRIBUTE_PREFIX = "metadata.";

    @Override
    default List<String> list(final String path) throws IOException {
        final Path dirPath = getConfiguration().getPath().resolve(StorageProviderUtil.sanitizePath(path));
        return StorageProviderUtil.listFiles(dirPath).stream().map(getConfiguration().getPath()::relativize).map(Path::toString)
            .map(pathStr -> pathStr.replace('\\', '/'))
            .toList();
    }

    @Override
    @SuppressWarnings("SpellCheckingInspection")
    default T save(final String path, final String contentType, final byte[] payload, final Map<String, String> metadata) throws IOException {
        final String sanitizedPath = StorageProviderUtil.sanitizePath(path);
        final Path filePath = getConfiguration().getPath().resolve(sanitizedPath);

        final String md5 = StorageProviderUtil.md5(payload);

        StorageProviderUtil.createMissingDirectories(filePath.getParent(), "www-data", "www-data", "rwxr-xr-x");
        Files.write(filePath, payload, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        StorageProviderUtil.setPermissions(filePath, "www-data", "www-data", "rwxr-xr-x");
        final UserDefinedFileAttributeView view = Files.getFileAttributeView(filePath, UserDefinedFileAttributeView.class);
        view.write(CONTENT_TYPE_ATTRIBUTE, Charset.defaultCharset().encode(contentType));
        view.write(MD5_ATTRIBUTE, Charset.defaultCharset().encode(md5));
        for (final Entry<String, String> entry : metadata.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue();
            view.write(METADATA_ATTRIBUTE_PREFIX + key, Charset.defaultCharset().encode(value));
        }
        final URI uri = UriComponentsBuilder.fromUri(getConfiguration().getEndpoint()).pathSegment(sanitizedPath).build().toUri();
        return newInstance(uri, getConfiguration().getStorageId(), sanitizedPath, contentType, payload.length, md5, metadata,
            Files.getLastModifiedTime(filePath).toInstant());
    }

    @Override
    default T load(final String path) throws IOException {
        final String sanitizedPath = StorageProviderUtil.sanitizePath(path);
        final Path filePath = getConfiguration().getPath().resolve(sanitizedPath);

        final UserDefinedFileAttributeView view = Files.getFileAttributeView(filePath, UserDefinedFileAttributeView.class);
        final String contentType = readAttribute(view, CONTENT_TYPE_ATTRIBUTE);
        final String md5 = readAttribute(view, MD5_ATTRIBUTE);
        final Map<String, String> metadata = new LinkedHashMap<>();
        for (final String attribute : view.list()) {
            if (!attribute.startsWith(METADATA_ATTRIBUTE_PREFIX)) {
                continue;
            }
            metadata.put(attribute.substring(METADATA_ATTRIBUTE_PREFIX.length()), readAttribute(view, attribute));
        }

        final URI uri = UriComponentsBuilder.fromUri(getConfiguration().getEndpoint()).pathSegment(sanitizedPath).build().toUri();
        return newInstance(uri, getConfiguration().getStorageId(), sanitizedPath, contentType, Files.size(filePath), md5, metadata,
            Files.getLastModifiedTime(filePath).toInstant());
    }

    @Override
    default void delete(final Collection<String> paths) throws IOException {
        for (final String path : paths) {
            delete(path);
        }
    }

    @Override
    default void delete(final String path) throws IOException {
        final Path filePath = getConfiguration().getPath().resolve(StorageProviderUtil.sanitizePath(path));
        // Should not delete any directories
        if (Files.isDirectory(filePath)) {
            return;
        }
        Files.deleteIfExists(filePath);
    }

    @Override
    default void copy(final String fromPath, final String toPath) throws IOException {
        final Path fromFilePath = getConfiguration().getPath().resolve(StorageProviderUtil.sanitizePath(fromPath));
        final Path toFilePath = getConfiguration().getPath().resolve(StorageProviderUtil.sanitizePath(toPath));
        Files.copy(fromFilePath, toFilePath, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    default void move(final String fromPath, final String toPath) throws IOException {
        final Path fromFilePath = getConfiguration().getPath().resolve(StorageProviderUtil.sanitizePath(fromPath));
        final Path toFilePath = getConfiguration().getPath().resolve(StorageProviderUtil.sanitizePath(toPath));
        Files.move(fromFilePath, toFilePath, StandardCopyOption.REPLACE_EXISTING);
    }

    private String readAttribute(final UserDefinedFileAttributeView view, final String attribute) throws IOException {
        final ByteBuffer byteBuffer = ByteBuffer.allocate(view.size(attribute));
        view.read(attribute, byteBuffer);
        byteBuffer.flip();
        return Charset.defaultCharset().decode(byteBuffer).toString();
    }

}

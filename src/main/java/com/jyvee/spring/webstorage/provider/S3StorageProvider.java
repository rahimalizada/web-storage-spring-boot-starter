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

import com.jyvee.spring.webstorage.configuration.S3StorageConfigurationProperties;
import com.jyvee.spring.webstorage.provider.s3.S3CopyClient;
import com.jyvee.spring.webstorage.provider.s3.S3DeleteClient;
import com.jyvee.spring.webstorage.provider.s3.S3GetClient;
import com.jyvee.spring.webstorage.provider.s3.S3GetResponse;
import com.jyvee.spring.webstorage.provider.s3.S3ListClient;
import com.jyvee.spring.webstorage.provider.s3.S3PutClient;
import com.jyvee.spring.webstorage.provider.s3.S3PutResponse;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface S3StorageProvider<T> extends StorageProvider<T, S3StorageConfigurationProperties> {

    @Override
    default T save(final String path, final String contentType, final byte[] payload,
                   final Map<String, String> metadata) throws IOException {
        final String sanitizedPath = StorageProviderUtil.sanitizePath(path);

        final Map<String, String> sanitizedMetadata = metadata
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey,
                entry -> URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8), (_, second) -> second,
                LinkedHashMap::new));

        final S3PutResponse putResponse =
            new S3PutClient(HttpClientProvider.get().getHttpClient(), getConfiguration()).put(sanitizedPath,
                contentType, payload, sanitizedMetadata);

        final URI uri = UriComponentsBuilder
            .fromUri(getConfiguration().getEndpoint())
            .path("/")
            .path(sanitizedPath)
            .build()
            .toUri();

        return newInstance(uri, getConfiguration().getStorageId(), sanitizedPath, contentType, payload.length,
            stripEtag(putResponse.eTag()), metadata, Instant.now());
    }

    @Override
    default List<String> list(final String path) throws IOException {
        final String prefix = StorageProviderUtil.sanitizePath(path);
        return new S3ListClient(HttpClientProvider.get().getHttpClient(), getConfiguration()).list(prefix);
    }

    @Override
    default T load(final String path) throws IOException {
        final String sanitizedPath = StorageProviderUtil.sanitizePath(path);
        final S3GetResponse getResponse =
            new S3GetClient(HttpClientProvider.get().getHttpClient(), getConfiguration()).get(sanitizedPath);

        final URI uri = UriComponentsBuilder
            .fromUri(getConfiguration().getEndpoint())
            .path("/")
            .path(sanitizedPath)
            .build()
            .toUri();
        return newInstance(uri, getConfiguration().getStorageId(), sanitizedPath, getResponse.contentType(),
            getResponse.contentLength(), stripEtag(getResponse.eTag()), getResponse.metadata(),
            getResponse.lastModified());
    }

    @Override
    default void delete(final Collection<String> paths) throws IOException {
        if (paths.isEmpty()) {
            return;
        }
        final List<String> sanitizedPaths = paths.stream().map(StorageProviderUtil::sanitizePath).toList();

        new S3DeleteClient(HttpClientProvider.get().getHttpClient(), getConfiguration()).delete(sanitizedPaths);
    }

    @Override
    default void delete(final String path) throws IOException {
        delete(List.of(path));
    }

    @Override
    default void move(final String fromPath, final String toPath) throws IOException {
        copy(fromPath, toPath);
        delete(fromPath);
    }

    @Override
    default void copy(final String fromPath, final String toPath) throws IOException {
        final String sanitizedFromPath = StorageProviderUtil.sanitizePath(fromPath);
        final String sanitizedToPath = StorageProviderUtil.sanitizePath(toPath);
        new S3CopyClient(HttpClientProvider.get().getHttpClient(), getConfiguration()).copy(sanitizedFromPath,
            sanitizedToPath);
    }

    private static String stripEtag(final String eTag) {
        if (eTag.startsWith("\"") && eTag.endsWith("\"")) {
            return eTag.substring(1, eTag.length() - 1);
        }
        return eTag;
    }

}

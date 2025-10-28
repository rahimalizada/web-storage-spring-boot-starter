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
import org.springframework.web.util.UriComponentsBuilder;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

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

    S3Client getS3Client();

    @Override
    default T save(final String path, final String contentType, final byte[] payload,
                   final Map<String, String> metadata) {
        final String sanitizedPath = StorageProviderUtil.sanitizePath(path);

        final Map<String, String> sanitizedMetadata = metadata
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey,
                entry -> URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8),
                (first, second) -> second,
                LinkedHashMap::new));

        final PutObjectRequest request = PutObjectRequest
            .builder()
            .bucket(getConfiguration().getBucket())
            .contentType(contentType)
            .key(sanitizedPath)
            .acl(ObjectCannedACL.PUBLIC_READ)
            .metadata(sanitizedMetadata)
            .build();
        final RequestBody requestBody = RequestBody.fromBytes(payload);

        final PutObjectResponse putObject = getS3Client().putObject(request, requestBody);
        final URI uri = UriComponentsBuilder
            .fromUri(getConfiguration().getEndpoint())
            .path("/")
            .path(sanitizedPath)
            .build()
            .toUri();
        return newInstance(uri,
            getConfiguration().getStorageId(),
            sanitizedPath,
            contentType,
            payload.length,
            stripEtag(putObject.eTag()),
            metadata,
            Instant.now());
    }

    @Override
    default List<String> list(final String path) {
        final ListObjectsV2Request request = ListObjectsV2Request
            .builder()
            .bucket(getConfiguration().getBucket())
            .prefix(StorageProviderUtil.sanitizePath(path))
            .maxKeys(Integer.MAX_VALUE)
            .build();

        final ListObjectsV2Iterable listObjectsV2Paginator = getS3Client().listObjectsV2Paginator(request);
        return listObjectsV2Paginator
            .stream()
            .map(ListObjectsV2Response::contents)
            .flatMap(Collection::stream)
            .map(S3Object::key)
            .toList();
    }

    @Override
    default T load(final String path) throws IOException {
        final String sanitizedPath = StorageProviderUtil.sanitizePath(path);
        final GetObjectRequest getObjectRequest =
            GetObjectRequest.builder().bucket(getConfiguration().getBucket()).key(sanitizedPath).build();
        try (final ResponseInputStream<GetObjectResponse> response = getS3Client().getObject(getObjectRequest)) {
            final URI uri = UriComponentsBuilder
                .fromUri(getConfiguration().getEndpoint())
                .path("/")
                .path(sanitizedPath)
                .build()
                .toUri();
            return newInstance(uri,
                getConfiguration().getStorageId(),
                sanitizedPath,
                response.response().contentType(),
                response.response().contentLength(),
                stripEtag(response.response().eTag()),
                response.response().metadata(),
                response.response().lastModified());
        } catch (@SuppressWarnings("OverlyBroadCatchBlock") final SdkException e) {
            throw new IOException(e);
        }
    }

    @Override
    default void delete(final Collection<String> paths) {
        if (paths.isEmpty()) {
            return;
        }
        final List<ObjectIdentifier> objectIdentifiers = paths
            .stream()
            .map(StorageProviderUtil::sanitizePath)
            .map(path -> ObjectIdentifier.builder().key(path).build())
            .toList();
        final Delete delete = Delete.builder().objects(objectIdentifiers).quiet(true).build();
        final DeleteObjectsRequest request =
            DeleteObjectsRequest.builder().bucket(getConfiguration().getBucket()).delete(delete).build();

        getS3Client().deleteObjects(request);
    }

    @Override
    default void delete(final String path) {
        delete(List.of(path));
    }

    @Override
    default void move(final String fromPath, final String toPath) {
        copy(fromPath, toPath);
        delete(fromPath);
    }

    @Override
    default void copy(final String fromPath, final String toPath) {
        final CopyObjectRequest request = CopyObjectRequest
            .builder()
            .destinationBucket(getConfiguration().getBucket())
            .sourceBucket(getConfiguration().getBucket())
            .sourceKey(StorageProviderUtil.sanitizePath(fromPath))
            .destinationKey(StorageProviderUtil.sanitizePath(toPath))
            .acl(ObjectCannedACL.PUBLIC_READ)
            .build();

        getS3Client().copyObject(request);
    }

    private static String stripEtag(final String eTag) {
        if (eTag.startsWith("\"") && eTag.endsWith("\"")) {
            return eTag.substring(1, eTag.length() - 1);
        }
        return eTag;
    }

}

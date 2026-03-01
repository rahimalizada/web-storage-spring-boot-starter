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

package com.jyvee.spring.webstorage.configuration;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.ToString;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Getter
@ToString
@Validated
@ConfigurationProperties(prefix = "web-storage.s3")
@ConditionalOnProperty(prefix = "web-storage.s3", name = "uri")
public class S3StorageConfigurationPropertiesImpl implements S3StorageConfigurationProperties {

    /**
     * S3 service URI in the form of
     * https://serviceEndpoint.url/?region=region&bucket=bucket&key=key&secret=secret&endpoint=https://endpoint.url
     */
    @SuppressWarnings("JavadocLinkAsPlainText")
    private final URI uri;

    /** S3 service endpoint URI */
    private final URI serviceEndpoint;

    /** S3 region name */
    private final String region;

    /** S3 bucket name */
    private final String bucket;

    /** S3 access key */
    private final String key;

    /** S3 secret key */
    private final String secret;

    /** Base endpoint URI for uploaded files */
    private final URI endpoint;

    /** Storage ID */
    private final String storageId;

    public S3StorageConfigurationPropertiesImpl(@NotNull final URI uri) {

        this.uri = uri;
        this.serviceEndpoint = UriComponentsBuilder
            .newInstance()
            .scheme(uri.getScheme())
            .host(uri.getHost())
            .port(uri.getPort())
            .build()
            .toUri();
        final Map<String, String> queryParamsMap =
            UriComponentsBuilder.fromUri(uri).build().getQueryParams().toSingleValueMap();

        this.region = getParameter(queryParamsMap, "region");
        this.bucket = getParameter(queryParamsMap, "bucket");
        this.key = getParameter(queryParamsMap, "key");
        this.secret = getParameter(queryParamsMap, "secret");
        final String httpEndpointStr = getParameter(queryParamsMap, "endpoint").strip();
        this.endpoint = URI.create(httpEndpointStr);

        this.storageId = UriComponentsBuilder
            .fromUri(this.serviceEndpoint)
            .path(this.region)
            .path("/")
            .path(this.bucket)
            .toUriString();
    }

    private static String getParameter(final Map<String, String> queryParamsMap, final String parameterName) {
        final String value = queryParamsMap.get(parameterName);
        if (value == null) {
            throw new IllegalArgumentException("Endpoint URI should contain '" + parameterName + "' parameter");
        }
        final String decodedValue = URLDecoder.decode(value, StandardCharsets.UTF_8);
        if (decodedValue.isBlank()) {
            throw new IllegalArgumentException("Endpoint URI parameter '" + parameterName + "' should not be blank");
        }
        return decodedValue;
    }

}

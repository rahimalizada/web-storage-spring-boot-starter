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

package com.jyvee.spring.webstorage.configuration;

import jakarta.validation.constraints.NotNull;
import lombok.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.nio.file.Path;

@Value
@Validated
@ConfigurationProperties(prefix = "web-storage.local")
@ConditionalOnProperty(prefix = "web-storage.local", name = {"path", "endpoint"})
public class LocalStorageConfigurationPropertiesImpl implements LocalStorageConfigurationProperties {

    /** Base endpoint URI for uploaded files */
    URI endpoint;

    /** Base path for uploaded files */
    Path path;

    /** Storage ID */
    String storageId;

    public LocalStorageConfigurationPropertiesImpl(@NotNull final URI endpoint, @NotNull final Path path) {
        this.endpoint = endpoint;
        this.path = path;
        this.storageId = path.toString();
    }

}

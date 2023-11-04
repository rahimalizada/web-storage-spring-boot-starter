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

package com.jyvee.spring.webstorage.validator;

import com.jyvee.spring.webstorage.configuration.StorageConfigurationProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Lazy
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnBean(StorageConfigurationProperties.class)
class StorageContentTypeValidator implements StorageValidator {

    @Override
    public Map<String, String> validate(@NotNull final Object configuration, @NotBlank final String contentType,
        @NotNull final byte[] payload) {
        return configuration instanceof final StorageContentTypeValidatorConfiguration validatorConfiguration ?
            validateInternal(validatorConfiguration, contentType) : Map.of();
    }

    private Map<String, String> validateInternal(final StorageContentTypeValidatorConfiguration validatorConfiguration,
        final String contentType) {
        if (validatorConfiguration.getContentTypes() != null && !validatorConfiguration.getContentTypes()
            .contains(contentType)) {
            throw new IllegalArgumentException("Content type is not allowed");
        }
        return Map.of();
    }

}

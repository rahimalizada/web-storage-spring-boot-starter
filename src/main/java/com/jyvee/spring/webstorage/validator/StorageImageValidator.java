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

package com.jyvee.spring.webstorage.validator;

import com.jyvee.spring.webstorage.configuration.StorageConfigurationProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;

@Lazy
@Component
@Order
@ConditionalOnBean(StorageConfigurationProperties.class)
class StorageImageValidator implements StorageValidator {

    @Override
    public Map<String, String> validate(@NotNull final Object configuration, @NotBlank final String contentType,
                                        @NotNull final byte[] payload) {
        return configuration instanceof final StorageImageValidatorConfiguration validatorConfiguration
            ? validateInternal(validatorConfiguration, contentType, payload) : Map.of();
    }

    private static Map<String, String> validateInternal(final StorageImageValidatorConfiguration validatorConfiguration,
                                                        final String contentType, @NotNull final byte[] payload) {
        if (validatorConfiguration.getMinWidth() == null && validatorConfiguration.getMinHeight() == null
            && validatorConfiguration.getMaxWidth() == null && validatorConfiguration.getMaxHeight() == null) {
            return Map.of();
        }
        try {
            return checkImage(contentType, payload, validatorConfiguration.getMinWidth(),
                validatorConfiguration.getMinHeight(), validatorConfiguration.getMaxWidth(),
                validatorConfiguration.getMaxHeight());
        } catch (final IOException e) {
            throw new IllegalArgumentException("Invalid image file", e);
        }
    }

    private static Map<String, String> checkImage(final String contentType, final byte[] payload,
                                                  @Nullable final Integer minWidth, @Nullable final Integer minHeight,
                                                  @Nullable final Integer maxWidth, @Nullable final Integer maxHeight)
        throws IOException {

        final BufferedImage bufferedImage = ImageUtil.toBufferedImage(payload, contentType);
        if (minWidth != null && bufferedImage.getWidth() < minWidth) {
            throw new IllegalArgumentException("Image width is too small");
        }
        if (minHeight != null && bufferedImage.getHeight() < minHeight) {
            throw new IllegalArgumentException("Image height is too small");
        }

        if (maxWidth != null && bufferedImage.getWidth() > maxWidth) {
            throw new IllegalArgumentException("Image width is too big");
        }
        if (maxHeight != null && bufferedImage.getHeight() > maxHeight) {
            throw new IllegalArgumentException("Image height is too big");
        }

        return Map.of("width", String.valueOf(bufferedImage.getWidth()), "height",
            String.valueOf(bufferedImage.getHeight()));

        // return Map.of();
    }

}

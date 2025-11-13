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

package com.jyvee.spring.test.webstorage;

import com.jyvee.spring.webstorage.configuration.FileType;
import com.jyvee.spring.webstorage.validator.StorageContentTypeValidatorConfiguration;
import com.jyvee.spring.webstorage.validator.StorageImageValidatorConfiguration;
import com.jyvee.spring.webstorage.validator.StorageSizeValidatorConfiguration;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Set;

@Getter
public enum WebFileType
    implements FileType, StorageSizeValidatorConfiguration, StorageContentTypeValidatorConfiguration,
    StorageImageValidatorConfiguration {

    NO_CHECK("No check", "/test/files", null, null, null, null, null, null, null, null, null),

    VALID_TEXT("Valid", "/test/files", Set.of("text/plain"), null, null, null, null, null, null, null, null),

    VALID("Valid", "/test/files", Set.of("image/jpeg", "image/png"), 1024L, 20 * 1024L, 100, 100, 700, 700, null, null),
    VALID_PARAM("Valid", "/test/files", Set.of("audio/webm"), 1024L, 20 * 1024L, 100, 100, 700, 700, null, null),

    INVALID_MIN_SIZE("Invalid minimum size", "/test/files", Set.of("image/jpeg", "image/png"), 20 * 1024L, 10 * 1024L,
        100, 100, 700, 700, null, null),

    INVALID_MAX_SIZE("Invalid maximum size", "/test/files", Set.of("image/jpeg", "image/png"), 1024L, 1024L, 100, 100,
        700, 700, null, null),

    INVALID_MIN_WIDTH("TInvalid minimum width", "/test/files", Set.of("image/jpeg", "image/png"), 1024L, 10 * 1024L,
        1000, 100, 700, 700, null, null),

    INVALID_MIN_HEIGHT("Invalid minimum height", "/test/files", Set.of("image/jpeg", "image/png"), 1024L, 10 * 1024L,
        100, 1000, 700, 700, null, null),

    INVALID_MAX_WIDTH("Invalid maximum width", "/test/files", Set.of("image/jpeg", "image/png"), 1024L, 10 * 1024L, 100,
        100, 100, 700, null, null),

    INVALID_MAX_HEIGHT("Invalid maximum height", "/test/files", Set.of("image/jpeg", "image/png"), 1024L, 10 * 1024L,
        100, 100, 700, 100, null, null);

    private final String title;

    private final Path path;

    @Nullable
    private final Set<String> contentTypes;

    @Nullable
    private final Long minSize;

    @Nullable
    private final Long maxSize;

    @Nullable
    private final Integer minWidth;

    @Nullable
    private final Integer minHeight;

    @Nullable
    private final Integer maxWidth;

    @Nullable
    private final Integer maxHeight;

    @Nullable
    private final Integer scaleToWidth;

    @Nullable
    private final Integer scaleToHeight;

    @SuppressWarnings("ParameterNumber")
    WebFileType(final String title, final String path, @Nullable final Set<String> contentTypes,
                @Nullable final Long minSize, @Nullable final Long maxSize, @Nullable final Integer minWidth,
                @Nullable final Integer minHeight, @Nullable final Integer maxWidth, @Nullable final Integer maxHeight,
                @Nullable final Integer scaleToWidth, @Nullable final Integer scaleToHeight) {
        this.title = title;
        this.path = Path.of(path);
        this.contentTypes = contentTypes;
        this.minSize = minSize;
        this.maxSize = maxSize;
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.scaleToWidth = scaleToWidth;
        this.scaleToHeight = scaleToHeight;
    }

}

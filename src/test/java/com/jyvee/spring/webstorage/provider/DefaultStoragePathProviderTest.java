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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

class DefaultStoragePathProviderTest {

    @SuppressWarnings("SpellCheckingInspection")
    private static final String RESULT_PATH = "/3/2/321c3cf486ed509164edec1e1981fec8/filename.ext";

    private final DefaultStoragePathProvider provider = new DefaultStoragePathProvider();

    @SuppressWarnings("DuplicateExpressions")
    @Test
    void getStoragePath_validArgs_validPath() {

        Assertions.assertEquals(RESULT_PATH,
            this.provider.getStoragePath(Path.of(""),
                Path.of("filename.ext"),
                "payload".getBytes(StandardCharsets.UTF_8)));

        Assertions.assertEquals("files" + RESULT_PATH,
            this.provider.getStoragePath(Path.of("files"),
                Path.of("filename.ext"),
                "payload".getBytes(StandardCharsets.UTF_8)));

        Assertions.assertEquals("files" + RESULT_PATH,
            this.provider.getStoragePath(Path.of("files/"),
                Path.of("filename.ext"),
                "payload".getBytes(StandardCharsets.UTF_8)));

        Assertions.assertEquals("files" + RESULT_PATH,
            this.provider.getStoragePath(Path.of("/files"),
                Path.of("filename.ext"),
                "payload".getBytes(StandardCharsets.UTF_8)));

        Assertions.assertEquals("files" + RESULT_PATH,
            this.provider.getStoragePath(Path.of("files/"),
                Path.of("/filename.ext"),
                "payload".getBytes(StandardCharsets.UTF_8)));

        Assertions.assertEquals("files" + RESULT_PATH,
            this.provider.getStoragePath(Path.of("/files/"),
                Path.of("/filename.ext"),
                "payload".getBytes(StandardCharsets.UTF_8)));

        Assertions.assertEquals("files/sub_path" + RESULT_PATH,
            this.provider.getStoragePath(Path.of("files/"),
                Path.of("sub_path/filename.ext"),
                "payload".getBytes(StandardCharsets.UTF_8)));

        Assertions.assertEquals("files/sub_path" + RESULT_PATH,
            this.provider.getStoragePath(Path.of("files/"),
                Path.of("/sub_path/filename.ext"),
                "payload".getBytes(StandardCharsets.UTF_8)));

        Assertions.assertEquals("files/sub_path" + RESULT_PATH,
            this.provider.getStoragePath(Path.of("/files/"),
                Path.of("/sub_path/filename.ext"),
                "payload".getBytes(StandardCharsets.UTF_8)));

        Assertions.assertEquals("files/sub_path" + RESULT_PATH,
            this.provider.getStoragePath(Path.of("/files"),
                Path.of("/sub_path/filename.ext"),
                "payload".getBytes(StandardCharsets.UTF_8)));

    }

}

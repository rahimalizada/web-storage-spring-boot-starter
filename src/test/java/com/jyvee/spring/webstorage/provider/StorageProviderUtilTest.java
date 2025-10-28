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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

class StorageProviderUtilTest {

    @SuppressWarnings("SpellCheckingInspection")
    private static final String PERMISSION_STR = "rwxr-xr-x";

    @Test
    void sanitizePath_validArgs_validResult() {
        Assertions.assertEquals("path/filename.ext", StorageProviderUtil.sanitizePath("/path/filename.ext"));
        Assertions.assertEquals("path/filename.ext", StorageProviderUtil.sanitizePath("path/filename.ext"));
        Assertions.assertEquals("path/______________________________/______________________________filename.ext",
            StorageProviderUtil.sanitizePath(
                "/path/ФотографияƏğ ?!!~*&<>$# {}-+()/ФотографияƏğ ?!!~*&<>$# {}-+()filename.ext"));

        Assertions.assertEquals("_path/filename.ext", StorageProviderUtil.sanitizePath("/ path/filename.ext"));
        Assertions.assertEquals("_path/filename.ext", StorageProviderUtil.sanitizePath(" path/filename.ext"));
        Assertions.assertEquals("_/path/filename.ext", StorageProviderUtil.sanitizePath(" /path/filename.ext"));
        Assertions.assertEquals("path/filename.ext", StorageProviderUtil.sanitizePath("path/filename.ext/"));
        Assertions.assertEquals("path/filename.ext/_", StorageProviderUtil.sanitizePath("path/filename.ext/ "));
    }

    @Test
    void listFiles_validArgs_validResult() throws IOException {
        final Path tempDirectory = Files.createTempDirectory("");
        final Path file1 = tempDirectory.resolve("file1.txt");
        final Path otherDirectory = tempDirectory.resolve("otherDirectory");
        final Path file2 = otherDirectory.resolve("file2.txt");

        Files.createFile(file1);
        Files.createDirectory(otherDirectory);
        Files.createFile(file2);
        Assertions.assertTrue(StorageProviderUtil.listFiles(tempDirectory).containsAll(Set.of(file1, file2)));

        try (final Stream<Path> pathStream = Files.walk(tempDirectory)) {
            pathStream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        Assertions.assertFalse(Files.exists(tempDirectory));
    }

    @Test
    void setPermissions_validArgs_ok() throws IOException {
        final boolean isWindows =
            System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH).startsWith("windows");
        final String principalName = isWindows ? System.getProperty("user.name") : "www-data";

        final Path tempFile = Files.createTempFile("", "");

        StorageProviderUtil.setPermissions(tempFile, principalName, principalName, PERMISSION_STR);
        // StorageProviderUtilTest.checkPathPermissions(tempFile, principalName);

        Files.deleteIfExists(tempFile);
    }

    @Test
    void getMissingDirectories_validArgs_ok() throws IOException {
        final Path tempDirectory = Files.createTempDirectory("");
        final Path directory = tempDirectory.resolve("a/b/c/d");
        Assertions.assertTrue(StorageProviderUtil.getMissingDirectories(tempDirectory).isEmpty());
        Assertions.assertTrue(StorageProviderUtil
            .getMissingDirectories(directory)
            .containsAll(List.of(tempDirectory.resolve("a"),
                tempDirectory.resolve("a/b"),
                tempDirectory.resolve("a/b/c"),
                tempDirectory.resolve("a/b/c/d"))));
    }

    @Test
    void createMissingDirectories_validArgs_ok() throws IOException {
        final boolean isWindows =
            System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH).startsWith("windows");
        final String principalName = isWindows ? System.getProperty("user.name") : "www-data";

        final Path tempDirectory = Files.createTempDirectory("");
        final Path directory = tempDirectory.resolve("a/b/c/d");
        StorageProviderUtil.createMissingDirectories(directory, principalName, principalName, PERMISSION_STR);

        final List<Path> createdDirectories = List.of(tempDirectory.resolve("a"),
            tempDirectory.resolve("a/b"),
            tempDirectory.resolve("a/b/c"),
            tempDirectory.resolve("a/b/c/d"));

        Assertions.assertTrue(Files.exists(tempDirectory));
        for (final Path createdDirectory : createdDirectories) {
            Assertions.assertTrue(Files.exists(createdDirectory));

            // StorageProviderUtilTest.checkPathPermissions(createdDirectory, principalName);

        }

    }

    @Test
    void md5_validArgs_ok() {
        Assertions.assertEquals("d41d8cd98f00b204e9800998ecf8427e", StorageProviderUtil.md5(new byte[]{}));
        Assertions.assertEquals("098f6bcd4621d373cade4e832627b4f6",
            StorageProviderUtil.md5("test".getBytes(StandardCharsets.UTF_8)));
    }

    // On linux, the owner of the created directory is the current user, not the www-data user
    @SuppressWarnings("unused")
    private static void checkPathPermissions(final Path path, final String principalName) throws IOException {
        final boolean isWindows =
            System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH).startsWith("windows");

        Assertions.assertTrue(isWindows ? Files.getOwner(path).getName().endsWith("\\" + principalName)
            : Files.getOwner(path).getName().endsWith(principalName));

        if (!isWindows) {
            final Set<PosixFilePermission> posixFilePermissions = Files.getPosixFilePermissions(path);
            Assertions.assertEquals(PERMISSION_STR, PosixFilePermissions.toString(posixFilePermissions));

            final PosixFileAttributeView fileAttributeView =
                Files.getFileAttributeView(path, PosixFileAttributeView.class);
            Assertions.assertEquals("www-data", fileAttributeView.readAttributes().group().getName());
        }
    }

}

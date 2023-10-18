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

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class StorageProviderUtil {

    private static final Pattern HTTP_SAFE_PATH_PATTERN = Pattern.compile("[^a-zA-Z0-9\\\\._/]");

    private StorageProviderUtil() {}

    static String sanitizePath(final String path) {
        String stripped = path;
        while (stripped.startsWith("/")) {
            stripped = stripped.substring(1);
        }

        while (stripped.endsWith("/")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        return StorageProviderUtil.httpSafePath(stripped);
    }

    private static String httpSafePath(final String path) {
        return HTTP_SAFE_PATH_PATTERN.matcher(path).replaceAll("_");
    }

    static List<Path> listFiles(final Path path) throws IOException {
        try (Stream<Path> stream = Files.find(path, Integer.MAX_VALUE, (filePath, fileAttr) -> fileAttr.isRegularFile())) {
            return stream.toList();
        } catch (final NoSuchFileException e) {
            return List.of();
        }
    }

    static void setPermissions(final Path path, final String ownerName, final String groupName, final String permissionString) throws IOException {
        try (FileSystem fileSystem = FileSystems.getDefault()) {
            final UserPrincipal userPrincipal = fileSystem.getUserPrincipalLookupService().lookupPrincipalByName(ownerName);
            final GroupPrincipal group = fileSystem.getUserPrincipalLookupService().lookupPrincipalByGroupName(groupName);
            Files.setOwner(path, userPrincipal);
            Files.getFileAttributeView(path, PosixFileAttributeView.class).setGroup(group);
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissionString));
        } catch (UserPrincipalNotFoundException | UnsupportedOperationException | FileSystemException ignored) {
        }
    }

    static List<Path> getMissingDirectories(final Path directoryPath) {
        final List<Path> directories = new ArrayList<>();
        Path root = directoryPath.getRoot();
        for (final Path path : directoryPath) {
            root = root.resolve(path);
            directories.add(0, root);
        }
        final List<Path> missingDirectories = directories.stream().takeWhile(path -> !Files.exists(path)).toList();
        return missingDirectories.reversed();
    }

    static void createMissingDirectories(final Path directoryPath, final String owner, final String group, final String permissionString)
        throws IOException {
        for (final Path directory : StorageProviderUtil.getMissingDirectories(directoryPath)) {
            Files.createDirectory(directory);
            StorageProviderUtil.setPermissions(directory, owner, group, permissionString);
        }
    }

    @SuppressWarnings({"checkstyle:MagicNumber", "MagicNumber"})
    static String md5(final byte[] payload) {
        final byte[] bytes;
        try {
            bytes = MessageDigest.getInstance("MD5").digest(payload);
        } catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        final BigInteger bigInt = new BigInteger(1, bytes);
        String hexString = bigInt.toString(16);
        // Now we need to zero pad it if you actually want the full 32 chars.
        while (hexString.length() < 32) {
            // noinspection StringConcatenationInLoop
            hexString = "0" + hexString;
        }
        return hexString;
    }

}

/*
 * Copyright (c) 2026 Rahim Alizada
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

package com.jyvee.spring.webstorage.provider.s3;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.SortedMap;

final class S3RawEncodingUtil {

    private S3RawEncodingUtil() {}

    static String encodePath(final String path) {
        return URLEncoder.encode(path, StandardCharsets.UTF_8).replace("+", "%20").replace("%2F", "/");
    }

    static String buildCanonicalQueryString(final SortedMap<String, String> params) {
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, String> entry : params.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8).replace("+", "%20"));
            sb.append('=');
            sb.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return sb.toString();
    }

}

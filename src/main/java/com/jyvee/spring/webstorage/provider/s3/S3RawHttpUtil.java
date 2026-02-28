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

import java.net.http.HttpRequest;
import java.util.Map;

final class S3RawHttpUtil {

    private S3RawHttpUtil() {}

    static void applyHeadersExceptHost(final HttpRequest.Builder builder, final Map<String, String> headers) {
        for (final Map.Entry<String, String> header : headers.entrySet()) {
            if (!"host".equals(header.getKey())) {
                builder.header(header.getKey(), header.getValue());
            }
        }
    }

}

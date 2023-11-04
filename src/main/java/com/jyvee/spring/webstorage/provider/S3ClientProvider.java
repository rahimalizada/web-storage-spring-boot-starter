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

import com.jyvee.spring.webstorage.configuration.S3StorageConfigurationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Slf4j
@Configuration
public class S3ClientProvider {

    @Lazy
    @Bean
    @ConditionalOnBean(S3StorageConfigurationProperties.class)
    public S3Client getS3Client(final S3StorageConfigurationProperties configuration) {
        log.debug("Instantiating S3Client bean");
        final StaticCredentialsProvider credentialsProvider =
            StaticCredentialsProvider.create(AwsBasicCredentials.create(configuration.getKey(),
                configuration.getSecret()));
        return S3Client.builder()
            // Required for mocking on localhost
            .forcePathStyle(true)
            .endpointOverride(configuration.getServiceEndpoint())
            .region(Region.of(configuration.getRegion()))
            .credentialsProvider(credentialsProvider)
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .build();
    }

}

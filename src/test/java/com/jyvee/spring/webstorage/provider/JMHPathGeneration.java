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

package com.jyvee.spring.webstorage.provider;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * <pre>
 * JMHPathGenerationTest.sanitizedStringConcat  thrpt    3  2872985.567 ± 372638.748  ops/s
 * JMHPathGenerationTest.uriComponentsBuilder   thrpt    3  2414971.275 ±  96622.188  ops/s
 * </pre>
 */
@SuppressWarnings("WeakerAccess")
public class JMHPathGeneration {

    @Test
    @Disabled
    void benchmarkLauncher() throws RunnerException {
        final Options options = new OptionsBuilder().include(this.getClass().getName() + "\\..*") //
                                                    .warmupTime(TimeValue.seconds(10))
                                                    .warmupIterations(2)//
                                                    .measurementTime(TimeValue.seconds(20))
                                                    .measurementIterations(3)//
                                                    .mode(Mode.Throughput)
                                                    .timeUnit(TimeUnit.SECONDS)
                                                    .forks(1)
                                                    .shouldFailOnError(true)
                                                    .shouldDoGC(true)
                                                    .timeout(TimeValue.minutes(3))
                                                    .build();
        new Runner(options).run();
    }

    @State(Scope.Thread)
    public static class BenchmarkState {

        private final String basePath = "example/dir/";

        private final String checksum = "321c3cf486ed509164edec1e1981fec8";

        private final String filename = "filename.ext";

    }

    @Benchmark
    public static void uriComponentsBuilder(final BenchmarkState benchmarkState, final Blackhole blackhole) {
        blackhole.consume(UriComponentsBuilder
            .fromPath(benchmarkState.basePath)
            .pathSegment(benchmarkState.checksum.substring(0, 1), benchmarkState.checksum.substring(1, 2),
                benchmarkState.checksum, benchmarkState.filename)
            .build()
            .toUriString());
    }

    @Benchmark
    public static void sanitizedStringConcat(final BenchmarkState benchmarkState, final Blackhole blackhole) {
        blackhole.consume(
            StorageProviderUtil.sanitizePath(benchmarkState.basePath) + "/" + benchmarkState.checksum.charAt(0) + "/"
            + benchmarkState.checksum.charAt(1) + "/" + benchmarkState.checksum + "/"
            + StorageProviderUtil.sanitizePath(benchmarkState.filename));
    }

}

/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.proxy;

import static com.google.common.base.Preconditions.checkNotNull;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;
import com.palantir.tritium.api.event.InstrumentationFilter;
import com.palantir.tritium.event.InstrumentationFilters;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
import com.palantir.tritium.event.log.LoggingInvocationEventHandler;
import com.palantir.tritium.event.log.LoggingLevel;
import com.palantir.tritium.event.metrics.MetricsInvocationEventHandler;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Instrument arbitrary service interfaces with optional metrics and invocation logging.
 */
public final class Instrumentation {

    private Instrumentation() {
        throw new UnsupportedOperationException();
    }

    static <T, U extends T> T wrap(
            Class<T> interfaceClass,
            final U delegate,
            final InstrumentationFilter instrumentationFilter,
            final List<InvocationEventHandler<InvocationContext>> handlers) {

        checkNotNull(interfaceClass, "interfaceClass");
        checkNotNull(delegate, "delegate");
        checkNotNull(instrumentationFilter, "instrumentationFilter");
        checkNotNull(handlers, "handlers");

        if (handlers.isEmpty() || instrumentationFilter == InstrumentationFilters.INSTRUMENT_NONE) {
            return delegate;
        }

        return Proxies.newProxy(interfaceClass, delegate,
                new InstrumentationProxy<>(instrumentationFilter, handlers, delegate));
    }

    /**
     * Wraps delegate with instrumentation.
     *
     * @deprecated Use {@link #wrap(Class, Object, InstrumentationFilter, List)}
     */
    @Deprecated
    static <T, U extends T> T wrap(
            Class<T> interfaceClass,
            final U delegate,
            final List<InvocationEventHandler<InvocationContext>> handlers) {
        return wrap(interfaceClass, delegate, InstrumentationFilters.INSTRUMENT_ALL, handlers);
    }

    /**
     * Return an instrumented proxy of the specified service interface and delegate that records aggregated invocation
     * metrics and performance trace logging.
     *
     * @param serviceInterface service interface
     * @param delegate delegate to instrument
     * @param metricRegistry metric registry
     * @return instrumented proxy implementing specified service interface
     * @deprecated use {@link com.palantir.tritium.Tritium#instrument(Class, Object, MetricRegistry)}
     */
    @Deprecated
    public static <T, U extends T> T instrument(Class<T> serviceInterface, U delegate, MetricRegistry metricRegistry) {
        return builder(serviceInterface, delegate)
                .withFilter(InstrumentationFilters.INSTRUMENT_ALL)
                .withMetrics(metricRegistry)
                .withPerformanceTraceLogging()
                .build();
    }

    public static <T> Logger getPerformanceLoggerForInterface(Class<T> serviceInterface) {
        return LoggerFactory.getLogger("performance." + serviceInterface.getName());
    }

    public static <T, U extends T> Builder<T, U> builder(Class<T> interfaceClass, U delegate) {
        return new Builder<>(interfaceClass, delegate);
    }

    @SuppressWarnings("WeakerAccess") // intended for public consumption
    public static final class Builder<T, U extends T> {

        private final Class<T> interfaceClass;
        private final U delegate;
        private final ImmutableList.Builder<InvocationEventHandler<InvocationContext>> handlers = ImmutableList
                .builder();
        private InstrumentationFilter filter = InstrumentationFilters.INSTRUMENT_ALL;

        private Builder(Class<T> interfaceClass, U delegate) {
            this.interfaceClass = checkNotNull(interfaceClass, "class");
            this.delegate = checkNotNull(delegate, "delegate");
        }

        /**
         * Supply additional metrics name prefix to be used across interfaces that use MetricGroup annotations.
         *
         * Example:
         *
         * Given a prefix com.business.service and instrumented interfaces below, a single metric
         * "com.business.service.fastcall" will share recordings across classes.  Functionality assumes a shared
         * MetricsRegistry.
         *
         * interface WidgetService {
         *     @MetricGroup("fastcall")
         *     getWidgets();
         * }
         *
         * interface UserService {
         *     @MetricGroup("fastcall")
         *     getUsers();
         * }
         *
         * @param metricRegistry - MetricsRegistry used for this application
         * @param globalPrefix - Metrics name prefix to be used
         * @return - InstrumentationBuilder
         */
        public Builder<T, U> withMetrics(MetricRegistry metricRegistry, String globalPrefix) {
            checkNotNull(metricRegistry, "metricRegistry");
            this.handlers.add(new MetricsInvocationEventHandler(
                    metricRegistry,
                    delegate.getClass(),
                    MetricRegistry.name(interfaceClass.getName()),
                    globalPrefix));
            return this;
        }

        public Builder<T, U> withMetrics(MetricRegistry metricRegistry) {
            return withMetrics(metricRegistry, null);
        }

        public Builder<T, U> withPerformanceTraceLogging() {
            return withLogging(
                    getPerformanceLoggerForInterface(interfaceClass),
                    LoggingLevel.TRACE,
                    LoggingInvocationEventHandler.LOG_DURATIONS_GREATER_THAN_1_MICROSECOND);
        }

        /**
         * Bridge for backward compatibility.
         */
        public Builder<T, U> withLogging(Logger logger, LoggingLevel loggingLevel,
                com.palantir.tritium.api.functions.LongPredicate durationPredicate) {
            return withLogging(logger, loggingLevel, (java.util.function.LongPredicate) durationPredicate);
        }

        public Builder<T, U> withLogging(Logger logger, LoggingLevel loggingLevel,
                java.util.function.LongPredicate durationPredicate) {
            this.handlers.add(new LoggingInvocationEventHandler(logger, loggingLevel, durationPredicate));
            return this;
        }

        public Builder<T, U> withHandler(InvocationEventHandler<InvocationContext> handler) {
            checkNotNull(handler, "handler");
            return withHandlers(Collections.singleton(handler));
        }

        public Builder<T, U> withHandlers(Iterable<InvocationEventHandler<InvocationContext>> additionalHandlers) {
            checkNotNull(additionalHandlers, "additionalHandlers");
            this.handlers.addAll(additionalHandlers);
            return this;
        }

        public Builder<T, U> withFilter(InstrumentationFilter instrumentationFilter) {
            this.filter = checkNotNull(instrumentationFilter, "instrumentationFilter");
            return this;
        }

        public T build() {
            return wrap(interfaceClass, delegate, filter, handlers.build());
        }
    }

}

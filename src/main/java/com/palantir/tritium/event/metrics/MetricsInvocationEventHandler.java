/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.tritium.event.metrics;

import static com.google.common.base.Preconditions.checkNotNull;

import com.codahale.metrics.MetricRegistry;
import com.palantir.tritium.event.AbstractInvocationEventHandler;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InvocationContext;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link com.palantir.tritium.event.InvocationEventHandler} that records method timing and failures using Dropwizard
 * metrics.
 */
public final class MetricsInvocationEventHandler extends AbstractInvocationEventHandler<InvocationContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsInvocationEventHandler.class);

    public static final String FAILURES_METRIC_NAME = "failures";

    private final MetricRegistry metricRegistry;
    private final String serviceName;

    public MetricsInvocationEventHandler(MetricRegistry metricRegistry, String serviceName) {
        this.metricRegistry = checkNotNull(metricRegistry, "metricRegistry");
        this.serviceName = checkNotNull(serviceName, "serviceName");
    }

    @Override
    public InvocationContext preInvocation(Object instance, Method method, Object[] args) {
        return DefaultInvocationContext.of(instance, method, args);
    }

    @Override
    public void onSuccess(@Nullable InvocationContext context, @Nullable Object result) {
        if (context == null) {
            LOGGER.debug("Encountered null metric context likely due to exception in preInvocation");
            return;
        }
        metricRegistry.timer(getBaseMetricName(context))
                .update(System.nanoTime() - context.getStartTimeNanos(), TimeUnit.NANOSECONDS);
    }

    @Override
    public void onFailure(@Nullable InvocationContext context, Throwable cause) {
        if (context == null) {
            markGlobalFailure();
            LOGGER.debug("Encountered null metric context likely due to exception in preInvocation: {}", cause, cause);
            return;
        }

        markGlobalFailure();
        String failuresMetricName = MetricRegistry.name(getBaseMetricName(context), FAILURES_METRIC_NAME);
        metricRegistry.meter(failuresMetricName).mark();
        metricRegistry.meter(MetricRegistry.name(failuresMetricName, cause.getClass().getName())).mark();
    }

    private String getBaseMetricName(InvocationContext context) {
        return MetricRegistry.name(serviceName, context.getMethod().getName());
    }

    private void markGlobalFailure() {
        metricRegistry.meter(FAILURES_METRIC_NAME).mark();
    }

}

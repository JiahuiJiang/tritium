/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.tritium.test;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("checkstyle:designforextension")
public class TestImplementation implements TestInterface, Runnable {

    private final AtomicInteger invocationCount = new AtomicInteger();

    @Override
    public void run() {
        test();
    }

    @Override
    public String test() {
        invocationCount.incrementAndGet();
        return "hello";
    }

    @Override
    public void multiArgumentMethod(String string, int count, Collection<String> foo) {
        test();
    }

    @SuppressWarnings("unused")
    @Override
    public void bulk(Set<?> set) {
        for (Object object : set) {
            test();
        }
    }

    @Override
    public int throwsCheckedException() throws Exception {
        throw new TestException("Testing checked Exception handling");
    }

    @Override
    public int throwsThrowable() throws Throwable {
        throw new AssertionError("Testing Error handling");
    }

    public int invocationCount() {
        return invocationCount.get();
    }

    public static class TestException extends Exception {
        public TestException(String message) {
            super(message);
        }
    }
}

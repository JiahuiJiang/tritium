/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.tritium.proxy;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.palantir.tritium.test.TestImplementation;
import com.palantir.tritium.test.TestInterface;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.Test;

public final class ProxiesTest {

    @Test
    public void testNewProxy() {
        final TestInterface delegate = new TestImplementation();

        TestInterface proxy = Proxies.newProxy(TestInterface.class, delegate, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                Object original = method.invoke(delegate, args);
                return original + ", world";
            }
        });

        assertEquals("hello, world", proxy.test());
    }

    @Test
    public void testInterfacesAdditionalInterfaces() {
        Class<?>[] interfaces = Proxies.interfaces(TestInterface.class,
                ImmutableList.of(List.class), Runnable.class);
        assertEquals(3, interfaces.length);
        assertEquals(ImmutableSet.of(Runnable.class, TestInterface.class, List.class),
                ImmutableSet.copyOf(interfaces));
    }

    @Test
    public void testInterfacesClassOfQClassOfQ() {
        Class<?>[] interfaces = Proxies.interfaces(TestInterface.class, TestImplementation.class);
        assertEquals(2, interfaces.length);
        assertEquals(ImmutableSet.of(Runnable.class, TestInterface.class),
                ImmutableSet.copyOf(interfaces));
    }

    @Test
    public void testCheckIsInterface() {
        Proxies.checkIsInterface(Runnable.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCheckIsInterfaceOnClass() {
        Proxies.checkIsInterface(String.class);
    }

    @Test
    public void testCheckAreAllInterfaces() {
        Proxies.checkAreAllInterfaces(ImmutableSet.of(TestInterface.class,
                Runnable.class,
                Callable.class,
                List.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCheckAreAllInterfacesWithClass() {
        Proxies.checkAreAllInterfaces(ImmutableSet.of(TestInterface.class,
                String.class,
                Runnable.class,
                Callable.class,
                List.class));
    }


    @Test(expected = UnsupportedOperationException.class)
    public void testInaccessibleConstructor() throws Throwable {
        Constructor<Proxies> constructor = Proxies.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        try {
            constructor.newInstance();
        } catch (InvocationTargetException expected) {
            throw expected.getCause();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            constructor.setAccessible(false);
        }
    }

}

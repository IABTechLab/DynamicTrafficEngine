// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.vertx;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wraps a {@link Vertx} to record which timers are live: set and neither cancelled nor, for a one-shot
 * timer, fired. Everything else is delegated as is.
 */
final class TimerTracker {

    private final Set<Long> liveTimers = ConcurrentHashMap.newKeySet();
    private final Set<Long> firedBeforeRecorded = ConcurrentHashMap.newKeySet();
    private final Vertx vertx;

    TimerTracker(Vertx delegate) {
        vertx = (Vertx) Proxy.newProxyInstance(Vertx.class.getClassLoader(), new Class<?>[] {Vertx.class},
                (proxy, method, args) -> invoke(delegate, method, args));
    }

    Vertx vertx() {
        return vertx;
    }

    Set<Long> liveTimers() {
        return Set.copyOf(liveTimers);
    }

    @SuppressWarnings("unchecked")
    private Object invoke(Vertx delegate, Method method, Object[] args) throws Throwable {
        boolean oneShot = "setTimer".equals(method.getName());
        if (oneShot) {
            Handler<Long> handler = (Handler<Long>) args[1];
            args[1] = (Handler<Long>) id -> {
                if (!liveTimers.remove(id)) {
                    firedBeforeRecorded.add(id);
                }
                handler.handle(id);
            };
        }
        Object result;
        try {
            result = method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
        if ((oneShot && !firedBeforeRecorded.remove((Long) result)) || "setPeriodic".equals(method.getName())) {
            liveTimers.add((Long) result);
        } else if ("cancelTimer".equals(method.getName())) {
            liveTimers.remove((Long) args[0]);
        }
        return result;
    }
}

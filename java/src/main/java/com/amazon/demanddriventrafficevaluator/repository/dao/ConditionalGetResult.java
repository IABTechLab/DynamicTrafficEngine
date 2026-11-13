// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.dao;

import java.util.Objects;
import java.util.Optional;

/**
 * The result of {@link Dao#getIfChanged}: the value, no value, or "not modified" when the stored
 * version still matches the version the caller already has.
 * <p>
 * "Not modified" is kept separate from "not found" so that a caller can skip the read of an
 * unchanged value without treating it as missing.
 * </p>
 *
 * @param <R> The type of the value.
 */
public final class ConditionalGetResult<R> {

    private static final ConditionalGetResult<?> NOT_FOUND = new ConditionalGetResult<>(null, false);
    private static final ConditionalGetResult<?> NOT_MODIFIED = new ConditionalGetResult<>(null, true);

    private final R value;
    private final boolean notModified;

    private ConditionalGetResult(R value, boolean notModified) {
        this.value = value;
        this.notModified = notModified;
    }

    /**
     * @param value The value that was read.
     * @param <R>   The type of the value.
     * @return A result holding the value.
     * @throws NullPointerException if {@code value} is null.
     */
    public static <R> ConditionalGetResult<R> found(R value) {
        return new ConditionalGetResult<>(Objects.requireNonNull(value, "value"), false);
    }

    /**
     * @param <R> The type of the value.
     * @return A result holding no value.
     */
    @SuppressWarnings("unchecked")
    public static <R> ConditionalGetResult<R> notFound() {
        return (ConditionalGetResult<R>) NOT_FOUND;
    }

    /**
     * @param <R> The type of the value.
     * @return A result saying the value has not changed since the version the caller has.
     */
    @SuppressWarnings("unchecked")
    public static <R> ConditionalGetResult<R> notModified() {
        return (ConditionalGetResult<R>) NOT_MODIFIED;
    }

    /**
     * @param value The value of an unconditional read.
     * @param <R>   The type of the value.
     * @return {@link #found} if the value is present, otherwise {@link #notFound}.
     */
    public static <R> ConditionalGetResult<R> fromOptional(Optional<R> value) {
        return value.map(ConditionalGetResult::found).orElseGet(ConditionalGetResult::notFound);
    }

    /**
     * @return true if the value has not changed since the version the caller has.
     */
    public boolean isNotModified() {
        return notModified;
    }

    /**
     * @return The value, or an empty Optional when it was not found or not modified.
     */
    public Optional<R> getValue() {
        return Optional.ofNullable(value);
    }
}

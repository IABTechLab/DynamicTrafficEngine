// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.dao;

import java.util.Optional;

/**
 * A generic Data Access Object (DAO) interface for retrieving and storing data.
 * <p>
 * This interface defines methods for accessing and manipulating data in a storage system.
 * It uses generic types to allow flexibility in the types of keys and values used.
 * Implementations of this interface can work with various storage systems such as
 * databases, caches, file systems, or remote services.
 * </p>
 *
 * @param <T> The type of the key used to identify data.
 * @param <R> The type of the value stored and retrieved.
 */
public interface Dao<T, R> {

    /**
     * Retrieves a value from the storage system.
     * <p>
     * This method attempts to fetch a value associated with the given key from the
     * storage system identified by the provided identifier. If the value is found,
     * it is returned wrapped in an Optional. If not found, an empty Optional is returned.
     * </p>
     *
     * @param identifier A string identifying the specific storage system to query.
     * @param key        The key to look up in the storage system.
     * @return An Optional containing the value if found, or an empty Optional if not found.
     */
    Optional<R> get(String identifier, T key);

    /**
     * Retrieves a value unless its stored version matches the version the caller already has.
     * <p>
     * Implementations that can check the version without reading the value (for example from
     * object metadata) override this to skip the read. The default ignores {@code knownVersion}
     * and delegates to {@link #get}, so it never returns {@link ConditionalGetResult#notModified}.
     * </p>
     *
     * @param identifier   A string identifying the specific storage system to query.
     * @param key          The key to look up in the storage system.
     * @param knownVersion The version the caller already has (such as an S3 ETag), or null if none.
     * @return The value, not found, or not modified.
     */
    default ConditionalGetResult<R> getIfChanged(String identifier, T key, String knownVersion) {
        return ConditionalGetResult.fromOptional(get(identifier, key));
    }

    /**
     * Stores a value in the storage system.
     * <p>
     * This method attempts to put the given value into the storage system identified
     * by the provided identifier, associating it with the specified key. If a value
     * already exists for the given key, it may be overwritten, depending on the
     * implementation.
     * </p>
     *
     * @param identifier A string identifying the specific storage system to use.
     * @param key        The key to associate with the value.
     * @param value      The value to store.
     */
    void put(String identifier, T key, R value);

    /**
     * Clears the entire storage system.
     * <p>
     * This method attempts to delete/expire all items in the storage system identified
     * by the provided identifier.
     * </p>
     *
     * @param identifier A string identifying the specific storage system to use.
     */
    void clear(String identifier);
}

// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * An abstract class representing a registry for storing and retrieving objects of type T.
 * <p>
 * This class provides a generic implementation for registering, retrieving, and managing
 * objects of a specific type. It uses a Map to store objects, with string keys representing
 * the names of the registered objects.
 * </p>
 * <p>
 * Thread safety: the registry is filled by a setup task on a scheduler thread while request
 * threads read it. The records are held as an immutable snapshot in a volatile field. Each
 * {@code register} call is synchronized, copies the current snapshot, adds the new entry and
 * publishes the copy, so a reader always sees a complete map and never a map being changed.
 * Reads take no lock: one volatile read and one lookup in the snapshot (plus a second lookup
 * only when the name is missing). Registration is expected to happen a handful of times at
 * startup, so the copy on each write is cheap.
 * </p>
 *
 * @param <T> The type of objects stored in this registry.
 */
public abstract class Registry<T> {

    private volatile Map<String, T> records;

    /**
     * Creates a registry holding a snapshot of the given records.
     * <p>
     * The map is copied; later changes to it are not seen by the registry.
     * </p>
     *
     * @param records The initial records.
     * @throws NullPointerException if {@code records} is null.
     */
    protected Registry(Map<String, T> records) {
        this.records = snapshot(new HashMap<>(records));
    }

    /**
     * Returns the type of the registry as a string.
     * This method should be implemented by subclasses to provide a descriptive name for the registry type.
     *
     * @return A string representing the type of this registry.
     */
    protected abstract String getRegistryType();

    /**
     * Registers a new object of type T by its class.
     * <p>
     * This method creates a new instance of the given class and registers it with the provided name.
     * It throws an exception if an object with the same name is already registered or if instantiation fails.
     * </p>
     *
     * @param name   The name to register the object under.
     * @param object The class of the object to register.
     * @throws IllegalArgumentException if an object with the same name is already registered
     *                                  or if the object cannot be instantiated.
     */
    public synchronized void register(String name, Class<? extends T> object) {
        if (records.containsKey(name)) {
            throw new IllegalArgumentException(getRegistryType() + " with name " + name + " already registered");
        }
        T instance;
        try {
            instance = object.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to register " + getRegistryType() + " with " + name, e);
        }
        publish(name, instance);
    }

    /**
     * Registers a new object of type T directly.
     * <p>
     * This method registers the provided object with the given name.
     * It throws an exception if an object with the same name is already registered.
     * </p>
     *
     * @param name   The name to register the object under.
     * @param object The object to register.
     * @throws IllegalArgumentException if an object with the same name is already registered.
     */
    public synchronized void register(String name, T object) {
        if (records.containsKey(name)) {
            throw new IllegalArgumentException(getRegistryType() + " with name " + name + " already registered");
        }
        publish(name, object);
    }

    /**
     * Retrieves an object from the registry by its name.
     *
     * @param name The name of the object to retrieve.
     * @return The object associated with the given name.
     * @throws IllegalArgumentException if no object is registered with the given name.
     */
    public T get(String name) {
        Map<String, T> current = records;
        T object = current.get(name);
        // containsKey only on a miss, so a name registered with a null value still returns null
        if (object == null && !current.containsKey(name)) {
            throw new IllegalArgumentException(getRegistryType() + " with name " + name + " not registered");
        }
        return object;
    }

    /**
     * Returns a map of all registered objects.
     * <p>
     * The map is an unmodifiable snapshot of the registry at the time of the call; objects
     * registered later are not added to it, so call this method again to see them. The
     * registered objects themselves are not copied.
     * </p>
     *
     * @return An unmodifiable map of all objects in the registry, where keys are object names
     * and values are the registered objects.
     */
    public Map<String, T> getRecords() {
        return records;
    }

    // Callers hold the lock: copy, add, then publish the copy through the volatile field.
    private void publish(String name, T object) {
        Map<String, T> next = new HashMap<>(records);
        next.put(name, object);
        records = snapshot(next);
    }

    // Collections.unmodifiableMap over a private HashMap, not Map.copyOf: Map.copyOf rejects null
    // keys and values (HashMap and this class accepted them) and randomizes iteration order per JVM.
    private static <T> Map<String, T> snapshot(Map<String, T> map) {
        return Collections.unmodifiableMap(map);
    }
}

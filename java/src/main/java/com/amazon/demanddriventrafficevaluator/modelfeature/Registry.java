// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature;

import java.util.Map;

/**
 * An abstract class representing a registry for storing and retrieving objects of type T.
 * <p>
 * This class provides a generic implementation for registering, retrieving, and managing
 * objects of a specific type. It uses a Map to store objects, with string keys representing
 * the names of the registered objects.
 * </p>
 *
 * @param <T> The type of objects stored in this registry.
 */
public abstract class Registry<T> {

    private final Map<String, T> records;

    protected Registry(Map<String, T> records) {
        this.records = records;
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
    public void register(String name, Class<? extends T> object) {
        if (records.containsKey(name)) {
            throw new IllegalArgumentException(getRegistryType() + " with name " + name + " already registered");
        } else {
            try {
                records.put(name, object.getDeclaredConstructor().newInstance());
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to register " + getRegistryType() + " with " + name, e);
            }
        }
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
    public void register(String name, T object) {
        if (records.containsKey(name)) {
            throw new IllegalArgumentException(getRegistryType() + " with name " + name + " already registered");
        } else {
            records.put(name, object);
        }
    }

    /**
     * Retrieves an object from the registry by its name.
     *
     * @param name The name of the object to retrieve.
     * @return The object associated with the given name.
     * @throws IllegalArgumentException if no object is registered with the given name.
     */
    public T get(String name) {
        if (records.containsKey(name)) {
            return records.get(name);
        } else {
            throw new IllegalArgumentException(getRegistryType() + " with name " + name + " not registered");
        }
    }

    /**
     * Returns a map of all registered objects.
     *
     * @return An unmodifiable map of all objects in the registry, where keys are object names
     * and values are the registered objects.
     */
    public Map<String, T> getRecords() {
        return records;
    }
}

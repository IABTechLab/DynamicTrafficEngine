// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.loader;

/**
 * An interface defining the contract for loader classes.
 * <p>
 * Loaders are responsible for loading data or configurations based on a given input.
 * This interface uses a generic type parameter to allow for different types of loader inputs,
 * providing flexibility in implementation while ensuring type safety.
 * </p>
 *
 * @param <T> The type of input used by this loader. Must extend LoaderInput to ensure
 *            that all inputs have the basic properties defined in LoaderInput.
 */
public interface Loader<T extends LoaderInput> {

    /**
     * Loads data or configurations based on the provided input.
     * <p>
     * This method is responsible for performing the actual loading operation.
     * The specific behavior of this method will depend on the implementing class,
     * but generally, it should:
     * <ul>
     *   <li>Retrieve data from a source (e.g., file system, database, network)</li>
     *   <li>Process the data as necessary</li>
     *   <li>Store or make the data available for use in the application</li>
     * </ul>
     * </p>
     *
     * @param input An object of type T containing the necessary information for the loading process.
     *              This could include details such as file paths, database queries, or API endpoints,
     *              depending on the specific implementation.
     * @return true if the loading operation was successful, false otherwise.
     */
    boolean load(T input);
}

// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.provider.configuration;

import com.amazon.demanddriventrafficevaluator.repository.entity.Configuration;

/**
 * A generic interface for providing configuration objects.
 * <p>
 * This interface defines a contract for classes that are responsible for
 * retrieving or generating configuration objects. It uses a generic type
 * parameter to allow for different types of configurations while ensuring
 * they all extend the base Configuration interface.
 * </p>
 *
 * @param <R> The type of Configuration this provider handles. Must extend Configuration.
 */
public interface ConfigurationProvider<R extends Configuration> {
    /**
     * Provides a configuration object.
     * <p>
     * This method is responsible for retrieving or generating a configuration
     * object of type R. The exact behavior of this method depends on the
     * implementing class. It may retrieve the configuration from a cache,
     * load it from a file, construct it dynamically, or obtain it through
     * any other means appropriate to the specific use case.
     * </p>
     *
     * @return A configuration object of type R.
     */
    R provide();
}

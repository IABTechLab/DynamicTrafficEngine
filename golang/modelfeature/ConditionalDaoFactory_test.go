// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package modelfeature

import (
	mockInterfaces "golang.a2z.com/demanddriventrafficevaluator/mocks/interfaces"
)

// conditionalDaoFactory is a DAO that also implements interfaces.ConditionalS3ObjectFetcher, as
// repository.DaoFactory does.
type conditionalDaoFactory struct {
	*mockInterfaces.DaoFactoryInterface
	*mockInterfaces.ConditionalS3ObjectFetcher
}

/*
 * Copyright (c) 2026 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.azure_data_lake

import io.airbyte.cdk.load.data.icerberg.parquet.IcebergDestinationCleaner
import io.airbyte.cdk.load.test.util.DestinationCleaner

/**
 * Cleaner for Azure Data Lake integration tests.
 *
 * Removes old test namespaces and tables from the catalog to prevent test pollution from failed or
 * interrupted tests. The catalog connection is created lazily so this object can be constructed
 * before the test containers are started.
 */
object AzureDataLakeCleaner : DestinationCleaner {
    private val actualCleaner by lazy {
        IcebergDestinationCleaner(
            AzureDataLakeTestUtil.getCatalog(
                AzureDataLakeTestUtil.parseConfig(
                    AzureDataLakeTestUtil.getConfigJson(namespace = "default")
                )
            )
        )
    }

    override fun cleanup() {
        actualCleaner.cleanup()
    }
}

/*
 * Copyright (c) 2026 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.azure_data_lake

import io.airbyte.cdk.AirbyteDestinationRunner

object AzureDataLakeDestination {
    @JvmStatic
    fun main(args: Array<String>) {
        AirbyteDestinationRunner.run(*args)
    }
}

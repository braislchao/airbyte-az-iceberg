/*
 * Copyright (c) 2026 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.azure_data_lake.check

import io.airbyte.cdk.load.check.CheckIntegrationTest
import io.airbyte.cdk.load.check.CheckTestConfig
import io.airbyte.integrations.destination.azure_data_lake.AdlsRestTestContainers
import io.airbyte.integrations.destination.azure_data_lake.AzureDataLakeTestUtil
import io.airbyte.integrations.destination.azure_data_lake.spec.AzureDataLakeSpecification
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

class AzureDataLakeCheckTest :
    CheckIntegrationTest<AzureDataLakeSpecification>(
        successConfigFilenames =
            listOf(
                CheckTestConfig(
                    AzureDataLakeTestUtil.getConfigJson(namespace = "check_test_namespace")
                ),
            ),
        // TODO: Add configs that are expected to fail `check` for validation testing
        failConfigFilenamesAndFailureReasons = mapOf(),
    ) {
    @Test
    @Timeout(5, unit = TimeUnit.MINUTES)
    override fun testSuccessConfigs() {
        super.testSuccessConfigs()
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            // Start the testcontainers environment once before any tests run
            AdlsRestTestContainers.start()
        }
    }
}

/*
 * Copyright (c) 2026 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.azure_data_lake.spec

import io.airbyte.cdk.load.command.DestinationConfiguration
import io.airbyte.cdk.load.command.DestinationConfigurationFactory
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

const val DEFAULT_CATALOG_NAME = "airbyte"
const val DEFAULT_STAGING_BRANCH = "airbyte_staging"
const val TEST_TABLE = "airbyte_test_table"

data class AzureDataLakeConfiguration(
    val azureStorageAccountName: String,
    val azureStorageAccountKey: String?,
    val azureSasToken: String?,
    val azureEndpoint: String?,
    val namespace: String,
    val azureCatalogConfiguration: AzureCatalogConfiguration,
) : DestinationConfiguration()

@Singleton
class AzureDataLakeConfigurationFactory :
    DestinationConfigurationFactory<AzureDataLakeSpecification, AzureDataLakeConfiguration> {

    override fun makeWithoutExceptionHandling(
        pojo: AzureDataLakeSpecification
    ): AzureDataLakeConfiguration {
        return AzureDataLakeConfiguration(
            azureStorageAccountName = pojo.azureStorageAccountName,
            azureStorageAccountKey = pojo.azureStorageAccountKey?.takeIf { it.isNotBlank() },
            azureSasToken = pojo.azureSasToken?.takeIf { it.isNotBlank() },
            azureEndpoint = pojo.azureEndpoint?.takeIf { it.isNotBlank() },
            namespace = pojo.namespace,
            azureCatalogConfiguration = pojo.toAzureCatalogConfiguration(),
        )
    }
}

@Factory
class AzureDataLakeConfigurationProvider(private val config: DestinationConfiguration) {
    @Singleton
    fun get(): AzureDataLakeConfiguration {
        return config as AzureDataLakeConfiguration
    }
}

/*
 * Copyright (c) 2026 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.azure_data_lake

import io.airbyte.cdk.command.ConfigurationSpecification
import io.airbyte.cdk.command.ValidatedJsonUtils
import io.airbyte.cdk.load.data.AirbyteValueCoercer
import io.airbyte.cdk.load.toolkits.iceberg.parquet.SimpleTableIdGenerator
import io.airbyte.cdk.load.toolkits.iceberg.parquet.TableIdGenerator
import io.airbyte.cdk.load.toolkits.iceberg.parquet.io.IcebergUtil
import io.airbyte.integrations.destination.azure_data_lake.catalog.AzureDataLakeCatalogUtil
import io.airbyte.integrations.destination.azure_data_lake.spec.AzureDataLakeConfiguration
import io.airbyte.integrations.destination.azure_data_lake.spec.AzureDataLakeConfigurationFactory
import io.airbyte.integrations.destination.azure_data_lake.spec.AzureDataLakeSpecification
import io.airbyte.integrations.destination.azure_data_lake.spec.DEFAULT_CATALOG_NAME
import org.apache.iceberg.catalog.Catalog

object AzureDataLakeTestUtil {

    /**
     * Builds a connector config pointing at the local Azurite + Iceberg REST catalog test
     * containers (see [AdlsRestTestContainers]).
     */
    fun getConfigJson(namespace: String = "<DEFAULT_NAMESPACE_PLACEHOLDER>"): String =
        """
        {
            "azure_storage_account_name": "${AdlsRestTestContainers.AZURITE_ACCOUNT}",
            "azure_storage_account_key": "${AdlsRestTestContainers.AZURITE_ACCOUNT_KEY}",
            "azure_endpoint": "${AdlsRestTestContainers.azuriteEndpoint()}",
            "warehouse_location": "${AdlsRestTestContainers.warehouseLocation()}",
            "main_branch_name": "main",
            "namespace": "$namespace",
            "catalog_type": {
                "catalog_type": "REST",
                "server_uri": "${AdlsRestTestContainers.restEndpoint()}"
            }
        }
        """.trimIndent()

    fun parseConfig(configJson: String) =
        getConfig(
            ValidatedJsonUtils.parseOne(AzureDataLakeSpecification::class.java, configJson)
        )

    fun getConfig(spec: ConfigurationSpecification) =
        AzureDataLakeConfigurationFactory()
            .makeWithoutExceptionHandling(spec as AzureDataLakeSpecification)

    fun getCatalog(
        config: AzureDataLakeConfiguration,
        tableIdGenerator: TableIdGenerator = SimpleTableIdGenerator(config.namespace),
    ): Catalog {
        // Create utility instances for test
        val icebergUtil = IcebergUtil(tableIdGenerator, AirbyteValueCoercer())
        val azureDataLakeCatalogUtil = AzureDataLakeCatalogUtil(icebergUtil)

        val properties = azureDataLakeCatalogUtil.toCatalogProperties(config)
        return icebergUtil.createCatalog(DEFAULT_CATALOG_NAME, properties)
    }
}

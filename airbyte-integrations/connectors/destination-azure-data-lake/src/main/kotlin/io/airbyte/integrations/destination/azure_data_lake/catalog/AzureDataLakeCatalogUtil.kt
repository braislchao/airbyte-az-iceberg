/*
 * Copyright (c) 2026 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.azure_data_lake.catalog

import io.airbyte.cdk.load.command.DestinationStream
import io.airbyte.cdk.load.toolkits.iceberg.parquet.io.IcebergUtil
import io.airbyte.integrations.destination.azure_data_lake.spec.AzureDataLakeConfiguration
import io.airbyte.integrations.destination.azure_data_lake.spec.RestCatalogConfiguration
import jakarta.inject.Singleton
import org.apache.iceberg.CatalogProperties
import org.apache.iceberg.CatalogUtil
import org.apache.iceberg.azure.AzureProperties
import org.apache.iceberg.catalog.Catalog
import org.apache.iceberg.rest.auth.OAuth2Properties

/**
 * Utility for configuring Apache Iceberg with a REST catalog on Azure ADLS Gen2.
 *
 * Storage I/O goes through Iceberg's [org.apache.iceberg.azure.adlsv2.ADLSFileIO], which supports
 * three authentication modes (in order of precedence):
 * - SAS token (`adls.sas-token.<account host>`)
 * - Storage account shared key (`adls.auth.shared-key.account.name`/`.key`)
 * - Default Azure credential chain (environment variables, AKS workload identity, managed
 * identity) when no explicit credentials are configured
 */
@Singleton
class AzureDataLakeCatalogUtil(
    private val icebergUtil: IcebergUtil,
) {
    companion object {
        const val ADLS_FILE_IO_IMPL = "org.apache.iceberg.azure.adlsv2.ADLSFileIO"

        // Matches the URI scheme accepted by org.apache.iceberg.azure.adlsv2.ADLSLocation:
        // abfs[s]://[<container>@]<storage account host>/<path>
        private val ABFS_URI_PATTERN = Regex("^abfss?://(?:([^/@]+)@)?([^/?#]+).*$")
    }

    fun createNamespace(streamDescriptor: DestinationStream.Descriptor, catalog: Catalog) {
        icebergUtil.createNamespace(streamDescriptor, catalog)
    }

    /**
     * Creates the Iceberg [Catalog] configuration properties from the destination's configuration.
     *
     * @param config The destination's configuration
     * @return The Iceberg [Catalog] configuration properties.
     */
    fun toCatalogProperties(config: AzureDataLakeConfiguration): Map<String, String> {
        val catalogConfig = config.azureCatalogConfiguration

        val adlsProperties = buildAdlsProperties(config)

        return when (val catalogConfiguration = catalogConfig.catalogConfiguration) {
            is RestCatalogConfiguration ->
                // REST properties are applied last so an explicit "warehouse" identifier can
                // override the default warehouse location.
                adlsProperties + buildRestProperties(catalogConfiguration)
        }
    }

    /**
     * Extracts the storage account host (e.g. `myaccount.dfs.core.windows.net`) from the warehouse
     * location. This must match what [org.apache.iceberg.azure.adlsv2.ADLSLocation] parses out of
     * data file URIs, because the per-account SAS token and endpoint ("connection string")
     * properties are keyed by it.
     */
    internal fun storageAccountHost(config: AzureDataLakeConfiguration): String {
        val match = ABFS_URI_PATTERN.matchEntire(config.azureCatalogConfiguration.warehouseLocation)
        return match?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }
            ?: "${config.azureStorageAccountName}.dfs.core.windows.net"
    }

    private fun buildAdlsProperties(config: AzureDataLakeConfiguration): Map<String, String> {
        val accountHost = storageAccountHost(config)

        return buildMap {
            put(CatalogProperties.FILE_IO_IMPL, ADLS_FILE_IO_IMPL)
            put(
                CatalogProperties.WAREHOUSE_LOCATION,
                config.azureCatalogConfiguration.warehouseLocation
            )

            // Authentication. If neither a SAS token nor an account key is configured, ADLSFileIO
            // falls back to the default Azure credential chain (env vars, AKS workload identity,
            // managed identity).
            config.azureSasToken?.let { sasToken ->
                put("${AzureProperties.ADLS_SAS_TOKEN_PREFIX}$accountHost", sasToken)
            }
            config.azureStorageAccountKey?.let { accountKey ->
                put(AzureProperties.ADLS_SHARED_KEY_ACCOUNT_NAME, config.azureStorageAccountName)
                put(AzureProperties.ADLS_SHARED_KEY_ACCOUNT_KEY, accountKey)
            }

            // Optional endpoint override (e.g. local Azurite emulator). Iceberg 1.7 uses the
            // "connection string" property value as the client endpoint URL.
            config.azureEndpoint?.let { endpoint ->
                put("${AzureProperties.ADLS_CONNECTION_STRING_PREFIX}$accountHost", endpoint)
            }
        }
    }

    private fun buildRestProperties(catalogConfig: RestCatalogConfiguration): Map<String, String> {
        return buildMap {
            put(CatalogUtil.ICEBERG_CATALOG_TYPE, CatalogUtil.ICEBERG_CATALOG_TYPE_REST)
            put(CatalogProperties.URI, catalogConfig.serverUri)
            // Some multi-catalog REST servers use the "warehouse" property to select the catalog.
            catalogConfig.warehouse?.let { put(CatalogProperties.WAREHOUSE_LOCATION, it) }
            catalogConfig.authToken?.let { put(OAuth2Properties.TOKEN, it) }
            catalogConfig.credential?.let { put(OAuth2Properties.CREDENTIAL, it) }
            catalogConfig.scope?.let { put(OAuth2Properties.SCOPE, it) }
            catalogConfig.oauth2ServerUri?.let { put(OAuth2Properties.OAUTH2_SERVER_URI, it) }
        }
    }
}

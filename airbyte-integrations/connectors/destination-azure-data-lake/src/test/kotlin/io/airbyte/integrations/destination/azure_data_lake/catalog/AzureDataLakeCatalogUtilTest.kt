/*
 * Copyright (c) 2026 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.azure_data_lake.catalog

import io.airbyte.cdk.load.toolkits.iceberg.parquet.io.IcebergUtil
import io.airbyte.integrations.destination.azure_data_lake.spec.AzureCatalogConfiguration
import io.airbyte.integrations.destination.azure_data_lake.spec.AzureDataLakeConfiguration
import io.airbyte.integrations.destination.azure_data_lake.spec.RestCatalogConfiguration
import io.mockk.mockk
import org.apache.iceberg.CatalogProperties
import org.apache.iceberg.CatalogUtil
import org.apache.iceberg.azure.AzureProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class AzureDataLakeCatalogUtilTest {

    private val catalogUtil = AzureDataLakeCatalogUtil(icebergUtil = mockk<IcebergUtil>())

    private val accountHost = "myaccount.dfs.core.windows.net"

    private fun config(
        accountKey: String? = null,
        sasToken: String? = null,
        endpoint: String? = null,
        warehouseLocation: String = "abfss://warehouse@$accountHost/iceberg",
        restConfig: RestCatalogConfiguration = restConfig(),
    ) =
        AzureDataLakeConfiguration(
            azureStorageAccountName = "myaccount",
            azureStorageAccountKey = accountKey,
            azureSasToken = sasToken,
            azureEndpoint = endpoint,
            namespace = "default",
            azureCatalogConfiguration =
                AzureCatalogConfiguration(
                    warehouseLocation = warehouseLocation,
                    mainBranchName = "main",
                    catalogConfiguration = restConfig,
                ),
        )

    private fun restConfig(
        serverUri: String = "http://localhost:8181",
        warehouse: String? = null,
        authToken: String? = null,
        credential: String? = null,
        scope: String? = null,
        oauth2ServerUri: String? = null,
    ) = RestCatalogConfiguration(serverUri, warehouse, authToken, credential, scope, oauth2ServerUri)

    @Test
    fun `builds base REST and ADLS properties`() {
        val properties = catalogUtil.toCatalogProperties(config())

        assertEquals(
            "org.apache.iceberg.azure.adlsv2.ADLSFileIO",
            properties[CatalogProperties.FILE_IO_IMPL]
        )
        assertEquals(
            "abfss://warehouse@$accountHost/iceberg",
            properties[CatalogProperties.WAREHOUSE_LOCATION]
        )
        assertEquals(
            CatalogUtil.ICEBERG_CATALOG_TYPE_REST,
            properties[CatalogUtil.ICEBERG_CATALOG_TYPE]
        )
        assertEquals("http://localhost:8181", properties[CatalogProperties.URI])
    }

    @Test
    fun `shared key auth sets shared-key account properties`() {
        val properties = catalogUtil.toCatalogProperties(config(accountKey = "s3cret"))

        assertEquals("myaccount", properties[AzureProperties.ADLS_SHARED_KEY_ACCOUNT_NAME])
        assertEquals("s3cret", properties[AzureProperties.ADLS_SHARED_KEY_ACCOUNT_KEY])
        assertFalse(properties.keys.any { it.startsWith(AzureProperties.ADLS_SAS_TOKEN_PREFIX) })
    }

    @Test
    fun `sas token auth sets per-account sas property`() {
        val properties = catalogUtil.toCatalogProperties(config(sasToken = "sig=abc"))

        assertEquals(
            "sig=abc",
            properties["${AzureProperties.ADLS_SAS_TOKEN_PREFIX}$accountHost"]
        )
        assertFalse(properties.containsKey(AzureProperties.ADLS_SHARED_KEY_ACCOUNT_NAME))
        assertFalse(properties.containsKey(AzureProperties.ADLS_SHARED_KEY_ACCOUNT_KEY))
    }

    @Test
    fun `no credentials emits no auth properties for default credential chain`() {
        val properties = catalogUtil.toCatalogProperties(config())

        assertFalse(properties.containsKey(AzureProperties.ADLS_SHARED_KEY_ACCOUNT_NAME))
        assertFalse(properties.containsKey(AzureProperties.ADLS_SHARED_KEY_ACCOUNT_KEY))
        assertFalse(properties.keys.any { it.startsWith(AzureProperties.ADLS_SAS_TOKEN_PREFIX) })
        assertFalse(
            properties.keys.any { it.startsWith(AzureProperties.ADLS_CONNECTION_STRING_PREFIX) }
        )
    }

    @Test
    fun `endpoint override sets per-account connection-string property`() {
        val properties =
            catalogUtil.toCatalogProperties(
                config(accountKey = "key", endpoint = "http://localhost:10000/myaccount")
            )

        assertEquals(
            "http://localhost:10000/myaccount",
            properties["${AzureProperties.ADLS_CONNECTION_STRING_PREFIX}$accountHost"]
        )
    }

    @Test
    fun `rest auth token and oauth properties are propagated`() {
        val properties =
            catalogUtil.toCatalogProperties(
                config(
                    restConfig =
                        restConfig(
                            authToken = "bearer-token",
                            credential = "client:secret",
                            scope = "PRINCIPAL_ROLE:ALL",
                            oauth2ServerUri = "http://auth.example.com/token",
                        )
                )
            )

        assertEquals("bearer-token", properties["token"])
        assertEquals("client:secret", properties["credential"])
        assertEquals("PRINCIPAL_ROLE:ALL", properties["scope"])
        assertEquals("http://auth.example.com/token", properties["oauth2-server-uri"])
    }

    @Test
    fun `explicit warehouse identifier overrides warehouse location`() {
        val properties =
            catalogUtil.toCatalogProperties(config(restConfig = restConfig(warehouse = "mycatalog")))

        assertEquals("mycatalog", properties[CatalogProperties.WAREHOUSE_LOCATION])
    }

    @Test
    fun `storage account host is parsed from warehouse location`() {
        val customHost = "other.dfs.core.chinacloudapi.cn"
        val properties =
            catalogUtil.toCatalogProperties(
                config(
                    sasToken = "sig=abc",
                    warehouseLocation = "abfss://container@$customHost/path",
                )
            )

        assertEquals("sig=abc", properties["${AzureProperties.ADLS_SAS_TOKEN_PREFIX}$customHost"])
    }

    @Test
    fun `storage account host falls back to account name when warehouse location is not abfss`() {
        val cfg = config(warehouseLocation = "not-a-uri")
        assertEquals("myaccount.dfs.core.windows.net", catalogUtil.storageAccountHost(cfg))
    }
}

/*
 * Copyright (c) 2026 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.azure_data_lake.spec

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaDescription
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaInject
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle
import io.airbyte.cdk.command.ConfigurationSpecification
import io.airbyte.cdk.load.spec.DestinationSpecificationExtension
import io.airbyte.protocol.models.v0.DestinationSyncMode
import jakarta.inject.Singleton

@Singleton
@JsonSchemaTitle("Azure Data Lake Destination Specification")
@JsonSchemaDescription(
    "Configuration for Azure Data Lake (ADLS Gen2) destination using Apache Iceberg format"
)
class AzureDataLakeSpecification : ConfigurationSpecification() {

    @get:JsonSchemaTitle("Azure Storage Account Name")
    @get:JsonPropertyDescription(
        "The name of the Azure storage account (ADLS Gen2) that will host the Iceberg data."
    )
    @get:JsonProperty("azure_storage_account_name")
    @get:JsonSchemaInject(json = """{"always_show": true, "order": 0}""")
    val azureStorageAccountName: String = ""

    @get:JsonSchemaTitle("Azure Storage Account Key")
    @get:JsonPropertyDescription(
        """The shared key of the Azure storage account. Leave empty to authenticate with a SAS token, or leave all credentials empty to use the default Azure credential chain (environment variables, workload identity or managed identity — recommended when running in AKS)."""
    )
    @get:JsonProperty("azure_storage_account_key")
    @get:JsonSchemaInject(json = """{"airbyte_secret": true, "always_show": true, "order": 1}""")
    val azureStorageAccountKey: String? = null

    @get:JsonSchemaTitle("Azure SAS Token")
    @get:JsonPropertyDescription(
        "A shared access signature (SAS) token for the storage account. Only used if no account key is provided."
    )
    @get:JsonProperty("azure_sas_token")
    @get:JsonSchemaInject(json = """{"airbyte_secret": true, "order": 2}""")
    val azureSasToken: String? = null

    @get:JsonSchemaTitle("Storage Endpoint Override (Advanced)")
    @get:JsonPropertyDescription(
        """Optional custom endpoint for the storage account, e.g. for a local Azurite emulator: <code>http://localhost:10000/devstoreaccount1</code>. If unset, <code>https://&lt;the storage account host in the warehouse location&gt;</code> is used."""
    )
    @get:JsonProperty("azure_endpoint")
    @get:JsonSchemaInject(json = """{"order": 3}""")
    val azureEndpoint: String? = null

    @get:JsonSchemaTitle("Warehouse Location")
    @get:JsonSchemaDescription(
        """The root location of the data warehouse used by the Iceberg catalog. Must use the "abfss://" scheme: "abfss://<container>@<account>.dfs.core.windows.net/<path>""""
    )
    @get:JsonProperty("warehouse_location")
    @get:JsonSchemaInject(
        json =
            """
            {
                "examples": ["abfss://warehouse@myaccount.dfs.core.windows.net/iceberg"],
                "always_show": true,
                "order": 4
            }
        """
    )
    val warehouseLocation: String = ""

    @get:JsonSchemaTitle("Main Branch Name")
    @get:JsonPropertyDescription(
        """The primary or default branch name in the catalog. Most query engines will use "main" by default. See <a href="https://iceberg.apache.org/docs/latest/branching/">Iceberg documentation</a> for more information."""
    )
    @get:JsonProperty("main_branch_name")
    @get:JsonSchemaInject(json = """{"order": 5}""")
    val mainBranchName: String = "main"

    @get:JsonSchemaTitle("Default Namespace")
    @get:JsonPropertyDescription(
        """The default namespace to use for tables. This will ONLY be used if the `Destination Namespace` setting is set to `Destination-defined` or `Source-defined`"""
    )
    @get:JsonProperty("namespace")
    @get:JsonSchemaInject(json = """{"examples": ["default", "airbyte_data"], "order": 6}""")
    val namespace: String = "default"

    @get:JsonSchemaTitle("Catalog Type")
    @get:JsonPropertyDescription("Specifies the type of Iceberg catalog.")
    @get:JsonProperty("catalog_type")
    @get:JsonSchemaInject(json = """{"always_show": true, "order": 7}""")
    val catalogType: AdlsCatalogType = RestCatalogSpec(serverUri = "")

    fun toAzureCatalogConfiguration(): AzureCatalogConfiguration {
        val catalogConfig =
            when (catalogType) {
                is RestCatalogSpec -> {
                    val spec = catalogType as RestCatalogSpec
                    RestCatalogConfiguration(
                        serverUri = spec.serverUri,
                        warehouse = spec.warehouse,
                        authToken = spec.authToken,
                        credential = spec.credential,
                        scope = spec.scope,
                        oauth2ServerUri = spec.oauth2ServerUri,
                    )
                }
            }

        return AzureCatalogConfiguration(
            warehouseLocation = warehouseLocation,
            mainBranchName = mainBranchName,
            catalogConfiguration = catalogConfig
        )
    }
}

@Singleton
class AzureDataLakeSpecificationExtension : DestinationSpecificationExtension {
    override val supportedSyncModes =
        listOf(
            DestinationSyncMode.OVERWRITE,
            DestinationSyncMode.APPEND,
            DestinationSyncMode.APPEND_DEDUP
        )
    override val supportsIncremental = true
}

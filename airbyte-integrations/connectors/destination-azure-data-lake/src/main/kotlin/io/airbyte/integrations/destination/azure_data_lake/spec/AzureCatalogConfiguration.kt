/*
 * Copyright (c) 2026 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.azure_data_lake.spec

data class AzureCatalogConfiguration(
    val warehouseLocation: String,
    val mainBranchName: String,
    val catalogConfiguration: AzureCatalogConfig
)

sealed interface AzureCatalogConfig

data class RestCatalogConfiguration(
    val serverUri: String,
    val warehouse: String?,
    val authToken: String?,
    val credential: String?,
    val scope: String?,
    val oauth2ServerUri: String?,
) : AzureCatalogConfig

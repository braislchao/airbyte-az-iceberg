/*
 * Copyright (c) 2026 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.azure_data_lake

import com.azure.storage.common.StorageSharedKeyCredential
import com.azure.storage.file.datalake.DataLakeServiceClientBuilder
import com.azure.storage.file.datalake.models.DataLakeStorageException
import java.io.File
import java.time.Duration
import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.wait.strategy.Wait

/**
 * Spins up a local Azurite (Azure storage emulator) and an Iceberg REST catalog backed by it, so
 * that integration tests run fully locally without a real Azure account.
 */
object AdlsRestTestContainers {

    // Azurite's well-known development storage account.
    const val AZURITE_ACCOUNT = "devstoreaccount1"
    const val AZURITE_ACCOUNT_KEY =
        "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw=="
    const val ACCOUNT_HOST = "$AZURITE_ACCOUNT.dfs.core.windows.net"
    const val WAREHOUSE_CONTAINER = "warehouse"

    // Host ports fixed in docker-compose.yml.
    private const val AZURITE_HOST_PORT = 10010
    private const val REST_HOST_PORT = 8191

    private val composeFile = File("src/test-integration/resources/rest/docker-compose.yml")

    /**
     * Define the docker-compose services and their wait strategies, so Testcontainers won't
     * consider them "started" until they're actually listening on those ports.
     */
    val testcontainers: ComposeContainer =
        ComposeContainer(composeFile)
            .withLocalCompose(true)
            .withExposedService(
                "azurite",
                10000,
                Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60))
            )
            .withExposedService(
                "rest",
                8181,
                // The port opens before the catalog server is fully initialized, so wait for the
                // config endpoint to actually respond.
                Wait.forHttp("/v1/config")
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofSeconds(60))
            )

    @Volatile private var started = false

    fun azuriteEndpoint() = "http://localhost:$AZURITE_HOST_PORT/$AZURITE_ACCOUNT"

    fun restEndpoint() = "http://localhost:$REST_HOST_PORT"

    fun warehouseLocation() = "abfss://$WAREHOUSE_CONTAINER@$ACCOUNT_HOST/"

    /**
     * Start the test containers, or skip if they're already started. Synchronized (rather than a
     * compare-and-set skip) so that concurrent callers BLOCK until the containers are actually up.
     */
    @Synchronized
    fun start() {
        if (!started) {
            testcontainers.start()
            createWarehouseFileSystem()
            started = true
        }
        // If it's already started, do nothing; the containers remain up.
    }

    /** ADLSFileIO does not create the storage container ("file system") - pre-create it. */
    private fun createWarehouseFileSystem() {
        val serviceClient =
            DataLakeServiceClientBuilder()
                .endpoint(azuriteEndpoint())
                .credential(StorageSharedKeyCredential(AZURITE_ACCOUNT, AZURITE_ACCOUNT_KEY))
                .buildClient()
        try {
            serviceClient.createFileSystem(WAREHOUSE_CONTAINER)
        } catch (e: DataLakeStorageException) {
            if (e.statusCode != 409) { // 409 = already exists
                throw e
            }
        }
    }
}

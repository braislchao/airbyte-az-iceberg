/*
 * Copyright (c) 2026 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.azure_data_lake.catalog

import com.sun.net.httpserver.HttpServer
import io.airbyte.cdk.load.toolkits.iceberg.parquet.io.IcebergUtil
import io.airbyte.integrations.destination.azure_data_lake.spec.AzureCatalogConfiguration
import io.airbyte.integrations.destination.azure_data_lake.spec.AzureDataLakeConfiguration
import io.airbyte.integrations.destination.azure_data_lake.spec.RestCatalogConfiguration
import io.mockk.mockk
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import org.apache.iceberg.rest.HTTPClient
import org.apache.iceberg.rest.auth.AuthManagers
import org.apache.iceberg.rest.auth.AuthSession
import org.apache.iceberg.rest.auth.OAuth2Properties
import org.apache.iceberg.rest.auth.OAuth2Util
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CatalogTokenRefreshTest {

    private lateinit var server: HttpServer
    private val grantTypes = CopyOnWriteArrayList<String>()

    @BeforeEach
    fun startTokenEndpoint() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/token") { exchange ->
            val form = exchange.requestBody.readAllBytes().decodeToString()
            grantTypes.add(
                form.split("&").first { it.startsWith("grant_type=") }.substringAfter("=")
            )
            val body =
                """{"access_token":"token-${grantTypes.size}","token_type":"bearer","expires_in":3600,"issued_token_type":"urn:ietf:params:oauth:token-type:access_token"}"""
                    .toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
    }

    @AfterEach fun stopTokenEndpoint() = server.stop(0)

    private fun refreshGrantType(properties: Map<String, String>): String {
        val baseUri = "http://127.0.0.1:${server.address.port}"
        HTTPClient.builder(properties).uri(baseUri).build().use { http ->
            val client = http.withAuthSession(AuthSession.EMPTY)
            AuthManagers.loadAuthManager("test", properties).use { manager ->
                manager.initSession(client, properties)
                val session = manager.catalogSession(client, properties) as OAuth2Util.AuthSession
                session.refresh(client)
            }
        }
        assertEquals(2, grantTypes.size, "expected one initial fetch and one refresh")
        assertEquals("client_credentials", grantTypes[0])
        return grantTypes[1]
    }

    private fun connectorProperties(): Map<String, String> {
        val config =
            AzureDataLakeConfiguration(
                azureStorageAccountName = "myaccount",
                azureStorageAccountKey = null,
                azureSasToken = null,
                azureEndpoint = null,
                namespace = "default",
                azureCatalogConfiguration =
                    AzureCatalogConfiguration(
                        warehouseLocation = "abfss://warehouse@myaccount.dfs.core.windows.net/x",
                        mainBranchName = "main",
                        catalogConfiguration =
                            RestCatalogConfiguration(
                                serverUri = "http://127.0.0.1:${server.address.port}",
                                warehouse = null,
                                authToken = null,
                                credential = "client-id:client-secret",
                                scope = "api://lakekeeper/.default",
                                oauth2ServerUri = "http://127.0.0.1:${server.address.port}/token",
                            ),
                    ),
            )
        return AzureDataLakeCatalogUtil(mockk<IcebergUtil>()).toCatalogProperties(config)
    }

    @Test
    fun `connector catalog refreshes its token with client credentials`() {
        assertEquals("client_credentials", refreshGrantType(connectorProperties()))
    }

    @Test
    fun `without the flag Iceberg refreshes with token exchange`() {
        val properties = connectorProperties() - OAuth2Properties.TOKEN_EXCHANGE_ENABLED
        assertTrue(refreshGrantType(properties).contains("token-exchange"))
    }
}

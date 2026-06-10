/*
 * Copyright (c) 2026 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.azure_data_lake.spec

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonValue
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaDescription
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaInject
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "catalog_type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = RestCatalogSpec::class, name = "REST"),
)
@JsonSchemaTitle("Catalog Type")
sealed class AdlsCatalogType(@JsonSchemaTitle("Catalog Type") open val catalogType: Type) {
    enum class Type(@get:JsonValue val typeName: String) {
        REST("REST"),
    }
}

@JsonSchemaTitle("REST Catalog")
@JsonSchemaDescription("Configuration for an Iceberg REST catalog.")
class RestCatalogSpec(
    @JsonSchemaTitle("Catalog Type")
    @JsonProperty("catalog_type")
    @JsonSchemaInject(json = """{"order":0}""")
    override val catalogType: Type = Type.REST,
    @get:JsonSchemaTitle("REST Server URI")
    @get:JsonPropertyDescription(
        "The base URL of the Iceberg REST catalog server. For example: http://localhost:8181 or https://catalog.example.com/api/catalog"
    )
    @get:JsonProperty("server_uri")
    @JsonSchemaInject(json = """{"order":1}""")
    val serverUri: String,
    @get:JsonSchemaTitle("Warehouse")
    @get:JsonPropertyDescription(
        "Optional warehouse identifier to send to the REST catalog (the \"warehouse\" property). Some multi-catalog REST servers (e.g. Polaris, Unity Catalog, Lakekeeper) use this to select the catalog. If unset, the Warehouse Location is sent instead."
    )
    @get:JsonProperty("warehouse")
    @get:JsonSchemaInject(json = """{"order":2}""")
    val warehouse: String? = null,
    @get:JsonSchemaTitle("Bearer Token")
    @get:JsonPropertyDescription(
        "Optional static bearer token for authenticating against the REST catalog server."
    )
    @get:JsonProperty("auth_token")
    @get:JsonSchemaInject(json = """{"airbyte_secret": true, "order":3}""")
    val authToken: String? = null,
    @get:JsonSchemaTitle("OAuth2 Credential")
    @get:JsonPropertyDescription(
        "Optional OAuth2 client credential for the REST catalog, in the form <code>client_id:client_secret</code>."
    )
    @get:JsonProperty("credential")
    @get:JsonSchemaInject(json = """{"airbyte_secret": true, "order":4}""")
    val credential: String? = null,
    @get:JsonSchemaTitle("OAuth2 Scope")
    @get:JsonPropertyDescription("Optional OAuth2 scope to request when using a credential.")
    @get:JsonProperty("scope")
    @get:JsonSchemaInject(json = """{"order":5}""")
    val scope: String? = null,
    @get:JsonSchemaTitle("OAuth2 Server URI")
    @get:JsonPropertyDescription(
        "Optional OAuth2 token endpoint. If unset, the REST catalog server's default token endpoint is used."
    )
    @get:JsonProperty("oauth2_server_uri")
    @get:JsonSchemaInject(json = """{"order":6}""")
    val oauth2ServerUri: String? = null,
) : AdlsCatalogType(catalogType)

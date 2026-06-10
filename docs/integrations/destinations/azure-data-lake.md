# Azure Data Lake

This page guides you through setting up the Azure Data Lake destination connector.

This connector writes the Apache Iceberg table format (Parquet data files) to Azure Data Lake
Storage Gen2 (ADLS) using an Iceberg REST catalog.

## Prerequisites

The Azure Data Lake connector requires two things:

1. An Azure storage account with **hierarchical namespace** enabled (ADLS Gen2)
2. An Iceberg REST catalog. Any spec-compliant REST catalog works, for example:
   - [Lakekeeper](https://docs.lakekeeper.io/)
   - [Apache Polaris](https://polaris.apache.org/)
   - [Unity Catalog](https://www.unitycatalog.io/)
   - Snowflake Open Catalog
   - The [Iceberg REST fixture](https://hub.docker.com/r/apache/iceberg-rest-fixture) (for testing)

## Setup guide

### Azure storage setup

1. In the [Azure Portal](https://portal.azure.com), create (or pick) a storage account with
   **hierarchical namespace** enabled.
2. Create a container that will host the Iceberg warehouse (for example `warehouse`).
3. Decide how the connector authenticates against the storage account (see below).

### Authentication

The connector supports three authentication modes, in order of precedence:

1. **Storage account key** — set `azure_storage_account_key` to one of the account's shared keys.
2. **SAS token** — set `azure_sas_token` to an account-level shared access signature. Only used if
   no account key is provided. The token needs read/write/delete/list permissions on the warehouse
   container.
3. **Default Azure credential chain** — leave both fields empty. The connector then uses the
   [DefaultAzureCredential](https://learn.microsoft.com/en-us/azure/developer/java/sdk/authentication/credential-chains#defaultazurecredential-overview)
   chain: environment variables (`AZURE_CLIENT_ID`/`AZURE_TENANT_ID`/`AZURE_CLIENT_SECRET`),
   **AKS workload identity**, and managed identity. This is the recommended mode when the connector
   runs inside AKS with a workload identity that has the `Storage Blob Data Contributor` role on
   the storage account.

### Connector configuration

| Field                        | Description                                                                                                                                |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `azure_storage_account_name` | Name of the ADLS Gen2 storage account.                                                                                                       |
| `azure_storage_account_key`  | Optional shared key.                                                                                                                         |
| `azure_sas_token`            | Optional SAS token.                                                                                                                          |
| `azure_endpoint`             | Optional endpoint override, e.g. `http://localhost:10000/devstoreaccount1` for a local [Azurite](https://github.com/Azure/Azurite) emulator. |
| `warehouse_location`         | Iceberg warehouse root, e.g. `abfss://warehouse@myaccount.dfs.core.windows.net/iceberg`.                                                     |
| `main_branch_name`           | The Iceberg branch most query engines read from. Usually `main`.                                                                            |
| `namespace`                  | Default namespace for tables, used when the destination namespace is destination-defined or source-defined.                                 |
| `catalog_type`               | REST catalog settings (see below).                                                                                                           |

### REST catalog settings

| Field              | Description                                                                                                            |
| ------------------ | ---------------------------------------------------------------------------------------------------------------------- |
| `server_uri`       | Base URL of the REST catalog server.                                                                                    |
| `warehouse`        | Optional warehouse identifier sent to the catalog. Multi-catalog servers (Polaris, Unity, Lakekeeper) use this to select the catalog. |
| `auth_token`       | Optional static bearer token.                                                                                           |
| `credential`       | Optional OAuth2 client credential in the form `client_id:client_secret`.                                                |
| `scope`            | Optional OAuth2 scope (e.g. `PRINCIPAL_ROLE:ALL` for Polaris).                                                          |
| `oauth2_server_uri`| Optional OAuth2 token endpoint.                                                                                         |

Note that the REST catalog server itself also reads and writes table metadata on ADLS, so it must
be configured with its own ADLS credentials (consult your catalog's documentation).

## Output schema

The connector writes Iceberg v2 tables with Parquet data files. Each stream maps to one table; the
stream namespace maps to the Iceberg namespace (falling back to the configured default namespace).
Table and column names are sanitized to alphanumeric characters and underscores.

Each record contains the Airbyte metadata columns in addition to the source fields:

| Column                 | Type                       |
| ---------------------- | -------------------------- |
| `_airbyte_raw_id`      | string                     |
| `_airbyte_extracted_at`| timestamp without timezone |
| `_airbyte_generation_id`| long                      |
| `_airbyte_meta`        | struct                     |

Sync modes: Full refresh (overwrite), Append, and Append + Dedup (using Iceberg equality deletes;
CDC deletions are soft deletes).

During a sync, data is committed to a staging branch (`airbyte_staging`) and fast-forwarded into
the main branch when the stream completes, so partially-failed syncs never expose half-written
data on `main`.

## Changelog

<details>
  <summary>Expand to review</summary>

| Version | Date       | Pull Request | Subject                                                  |
| :------ | :--------- | :----------- | :------------------------------------------------------- |
| 0.1.0   | 2026-06-05 |              | Initial release: Iceberg on ADLS Gen2 with REST catalog. |

</details>

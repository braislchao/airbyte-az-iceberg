/*
 * Copyright (c) 2026 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.azure_data_lake

import io.airbyte.cdk.load.command.Append
import io.airbyte.cdk.load.command.DestinationCatalog
import io.airbyte.cdk.load.command.DestinationStream
import io.airbyte.cdk.load.command.NamespaceMapper
import io.airbyte.cdk.load.data.FieldType
import io.airbyte.cdk.load.data.ObjectType
import io.airbyte.cdk.load.data.icerberg.parquet.IcebergConfigUpdater
import io.airbyte.cdk.load.data.icerberg.parquet.IcebergDataDumper
import io.airbyte.cdk.load.data.icerberg.parquet.IcebergExpectedRecordMapper
import io.airbyte.cdk.load.message.InputRecord
import io.airbyte.cdk.load.table.DefaultTempTableNameGenerator
import io.airbyte.cdk.load.test.util.OutputRecord
import io.airbyte.cdk.load.write.*
import io.airbyte.integrations.destination.azure_data_lake.catalog.AdlsTableIdGenerator
import io.airbyte.integrations.destination.azure_data_lake.schema.AzureDataLakeTableSchemaMapper
import io.airbyte.integrations.destination.azure_data_lake.spec.AzureCatalogConfiguration
import io.airbyte.integrations.destination.azure_data_lake.spec.AzureDataLakeConfiguration
import io.airbyte.integrations.destination.azure_data_lake.spec.AzureDataLakeSpecification
import io.airbyte.integrations.destination.azure_data_lake.spec.RestCatalogConfiguration
import kotlin.test.assertContains
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Azure Data Lake write test, running against local Azurite + Iceberg REST catalog containers.
 *
 * Column names are sanitized (alphanumeric + underscore) in the Iceberg schema. The AdlsDataDumper
 * reverse-maps column names when reading data for test validation.
 */
class AzureDataLakeWriteTest :
    BasicFunctionalityIntegrationTest(
        configContents = AzureDataLakeTestUtil.getConfigJson(),
        configSpecClass = AzureDataLakeSpecification::class.java,
        dataDumper =
            AdlsDataDumper(
                delegateDataDumper =
                    IcebergDataDumper(
                        tableIdGenerator =
                            AdlsTableIdGenerator(
                                AzureDataLakeTableSchemaMapper(
                                    // STUB: the only thing we actually use is `namespace`
                                    AzureDataLakeConfiguration(
                                        azureStorageAccountName = "",
                                        azureStorageAccountKey = null,
                                        azureSasToken = null,
                                        azureEndpoint = null,
                                        namespace = "test_database",
                                        azureCatalogConfiguration =
                                            AzureCatalogConfiguration(
                                                warehouseLocation = "",
                                                mainBranchName = "",
                                                catalogConfiguration =
                                                    RestCatalogConfiguration(
                                                        "",
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        null
                                                    )
                                            ),
                                    ),
                                    tempTableNameGenerator = DefaultTempTableNameGenerator(),
                                )
                            ),
                        getCatalog = { spec ->
                            AzureDataLakeTestUtil.getCatalog(AzureDataLakeTestUtil.getConfig(spec))
                        }
                    )
            ),
        destinationCleaner = AzureDataLakeCleaner,
        recordMangler = IcebergExpectedRecordMapper,
        isStreamSchemaRetroactive = true,
        isStreamSchemaRetroactiveForUnknownTypeToString = false,
        dedupBehavior = DedupBehavior(DedupBehavior.CdcDeletionMode.SOFT_DELETE),
        stringifySchemalessObjects = true,
        schematizedObjectBehavior = SchematizedNestedValueBehavior.STRINGIFY,
        schematizedArrayBehavior = SchematizedNestedValueBehavior.PASS_THROUGH,
        unionBehavior = UnionBehavior.STRINGIFY,
        commitDataIncrementally = false,
        allTypesBehavior =
            StronglyTyped(integerCanBeLarge = false, nestedFloatLosesPrecision = false),
        unknownTypesBehavior = UnknownTypesBehavior.PASS_THROUGH,
        nullEqualsUnset = true,
        configUpdater = IcebergConfigUpdater,
    ) {

    @Test
    fun testNameConflicts() {
        assumeTrue(verifyDataWriting)
        fun makeStream(
            name: String,
            namespaceSuffix: String,
        ) =
            DestinationStream(
                unmappedNamespace = randomizedNamespace + namespaceSuffix,
                unmappedName = name,
                generationId = 0,
                minimumGenerationId = 0,
                syncId = 42,
                namespaceMapper = NamespaceMapper(),
                tableSchema = makeTableSchema(ObjectType(linkedMapOf("id" to intType)), Append),
            )
        // Names are coerced to alphanumeric+underscore, so these two streams will collide.
        val catalog =
            DestinationCatalog(
                listOf(
                    makeStream("stream_with_spécial_character+", "_FOO"),
                    makeStream("stream_with_spécial_character$", "_FOO"),
                )
            )

        val failure = expectFailure { runSync(updatedConfig, catalog, messages = emptyList()) }
        assertContains(failure.message, "Detected naming conflicts between streams")
    }

    /**
     * This test differs from the base test in two critical aspects:
     *
     * 1. Data Type Conversion:
     * ```
     *    The base test attempts to change a column's data type from INTEGER to STRING,
     *    which Iceberg does not support and will throw an exception.
     * ```
     * 2. Result Ordering:
     * ```
     *    While the data content matches exactly, Iceberg returns results in a different
     *    order than what the base test expects. The base test's ordering assumptions
     *    need to be adjusted accordingly.
     * ```
     */
    @Test
    override fun testAppendSchemaEvolution() {
        assumeTrue(verifyDataWriting)
        fun makeStream(syncId: Long, schema: LinkedHashMap<String, FieldType>) =
            DestinationStream(
                unmappedNamespace = randomizedNamespace,
                unmappedName = "test_stream",
                generationId = 0,
                minimumGenerationId = 0,
                syncId = syncId,
                namespaceMapper = NamespaceMapper(),
                tableSchema = makeTableSchema(ObjectType(schema), Append),
            )
        val firstStream =
            makeStream(
                syncId = 42,
                linkedMapOf("id" to intType, "to_drop" to stringType, "same" to intType)
            )
        runSync(
            updatedConfig,
            firstStream,
            listOf(
                InputRecord(
                    firstStream,
                    """{"id": 42, "to_drop": "val1", "same": 42}""",
                    emittedAtMs = 1234L,
                )
            )
        )
        val finalStream =
            makeStream(
                syncId = 43,
                linkedMapOf("id" to intType, "same" to intType, "to_add" to stringType)
            )
        runSync(
            updatedConfig,
            finalStream,
            listOf(
                InputRecord(
                    finalStream,
                    """{"id": 42, "same": "43", "to_add": "val3"}""",
                    emittedAtMs = 1234,
                )
            )
        )
        dumpAndDiffRecords(
            parsedConfig,
            listOf(
                OutputRecord(
                    extractedAt = 1234,
                    generationId = 0,
                    data = mapOf("id" to 42, "same" to 42),
                    airbyteMeta = OutputRecord.Meta(syncId = 42),
                ),
                OutputRecord(
                    extractedAt = 1234,
                    generationId = 0,
                    data = mapOf("id" to 42, "same" to 43, "to_add" to "val3"),
                    airbyteMeta = OutputRecord.Meta(syncId = 43),
                )
            ),
            finalStream,
            primaryKey = listOf(listOf("id")),
            cursor = listOf("same"),
        )
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            // Start the testcontainers environment once before any tests run
            AdlsRestTestContainers.start()
        }
    }
}

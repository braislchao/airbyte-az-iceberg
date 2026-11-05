/*
 * Copyright (c) 2026 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.azure_data_lake.schema

import io.airbyte.cdk.load.command.DestinationStream
import io.airbyte.cdk.load.component.ColumnType
import io.airbyte.cdk.load.data.*
import io.airbyte.cdk.load.schema.TableSchemaMapper
import io.airbyte.cdk.load.schema.model.TableName
import io.airbyte.cdk.load.table.TempTableNameGenerator
import io.airbyte.integrations.destination.azure_data_lake.spec.AzureDataLakeConfiguration
import jakarta.inject.Singleton

/**
 * Maps stream schemas to catalog-safe table schemas.
 *
 * Sanitizes namespace/table/column names to alphanumeric characters and underscores so the same
 * tables remain queryable across the widest range of Iceberg REST catalog implementations and
 * query engines.
 */
@Singleton
class AzureDataLakeTableSchemaMapper(
    private val config: AzureDataLakeConfiguration,
    private val tempTableNameGenerator: TempTableNameGenerator
) : TableSchemaMapper {

    override fun toFinalTableName(desc: DestinationStream.Descriptor): TableName {
        val namespace =
            Transformations.toAlphanumericAndUnderscore(desc.namespace ?: config.namespace)
        val name = Transformations.toAlphanumericAndUnderscore(desc.name)
        return TableName(namespace, name)
    }

    override fun toTempTableName(tableName: TableName): TableName {
        return tempTableNameGenerator.generate(tableName)
    }

    override fun toColumnName(name: String): String {
        return Transformations.toAlphanumericAndUnderscore(name)
    }

    override fun toColumnType(fieldType: FieldType): ColumnType {
        // Map Airbyte field types to Iceberg/Parquet column types
        val parquetType =
            when (fieldType.type) {
                BooleanType -> "BOOL"
                DateType -> "DATE"
                IntegerType -> "INT64"
                NumberType -> "FLOAT64"
                StringType -> "STRING"
                TimeTypeWithTimezone,
                TimeTypeWithoutTimezone -> "STRING" // Store times as strings
                TimestampTypeWithTimezone,
                TimestampTypeWithoutTimezone -> "TIMESTAMP"
                is ArrayType,
                ArrayTypeWithoutSchema -> "STRING" // Arrays as JSON strings
                is UnionType,
                is UnknownType -> "STRING"
                ObjectTypeWithEmptySchema,
                ObjectTypeWithoutSchema,
                is ObjectType -> "STRING" // Objects as JSON strings
            }

        return ColumnType(parquetType, fieldType.nullable)
    }
}

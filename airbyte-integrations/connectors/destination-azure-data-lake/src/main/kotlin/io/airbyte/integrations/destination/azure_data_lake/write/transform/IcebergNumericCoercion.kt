/*
 * Copyright (c) 2026 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.azure_data_lake.write.transform

import io.airbyte.cdk.load.data.AirbyteValue
import io.airbyte.cdk.load.data.ArrayValue
import io.airbyte.cdk.load.data.IntegerValue
import io.airbyte.cdk.load.data.NumberValue
import io.airbyte.cdk.load.data.ObjectValue
import org.apache.iceberg.types.Type

/**
 * Rewrites [IntegerValue]s that are headed for a floating-point Iceberg column into
 * [NumberValue]s, recursing through lists and structs.
 *
 * `AirbyteValueToIcebergRecord` turns every [IntegerValue] into a `Long` regardless of the target
 * column type, so a whole number landing on a `double` column reaches
 * `ParquetValueWriters$DoubleWriter` as a `Long` and throws `ClassCastException`, failing the sync
 * with zero records committed. Its [NumberValue] branch does consult the target type, so handing it
 * a [NumberValue] takes the path that already works.
 *
 * Top-level scalars never hit this: a field declared `number` goes through `AirbyteValueCoercer`,
 * whose `coerceNumber` already promotes [IntegerValue] to [NumberValue]. Collection elements do,
 * because `coerceArray` only checks that the value is an [ArrayValue] and returns it untouched, so
 * elements keep whatever type JSON parsing inferred. `[3]` from an Airtable Lookup or Rollup over a
 * numeric field is the case this exists for.
 *
 * Only `double` is handled, because that is the only floating-point type
 * `AirbyteTypeToIcebergSchema` produces (`NumberType` -> `DoubleType`). A `float` column cannot be
 * fixed from here anyway: the converter's [NumberValue] branch always emits a `Double`, so it would
 * still hand a `Double` to `ParquetValueWriters$FloatWriter`. That needs the CDK fix.
 *
 * This lives in the connector rather than in the CDK so the fork stays purely additive over
 * upstream. The underlying CDK bug is worth fixing upstream too.
 */
fun coerceIntegersForIcebergType(value: AirbyteValue, type: Type): AirbyteValue =
    when {
        value is IntegerValue && type.typeId() == Type.TypeID.DOUBLE ->
            NumberValue(value.value.toBigDecimal())
        value is ArrayValue && type.isListType -> {
            val elementType = type.asListType().elementType()
            ArrayValue(value.values.map { coerceIntegersForIcebergType(it, elementType) })
        }
        value is ObjectValue && type.isStructType -> {
            val struct = type.asStructType()
            val coerced = LinkedHashMap<String, AirbyteValue>(value.values.size)
            value.values.forEach { (name, fieldValue) ->
                val fieldType = struct.fieldType(name)
                coerced[name] =
                    if (fieldType == null) fieldValue
                    else coerceIntegersForIcebergType(fieldValue, fieldType)
            }
            ObjectValue(coerced)
        }
        else -> value
    }

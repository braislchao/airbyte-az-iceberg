/*
 * Copyright (c) 2026 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.azure_data_lake.write.transform

import io.airbyte.cdk.load.data.ArrayValue
import io.airbyte.cdk.load.data.IntegerValue
import io.airbyte.cdk.load.data.NumberValue
import io.airbyte.cdk.load.data.ObjectValue
import io.airbyte.cdk.load.data.StringValue
import io.airbyte.cdk.load.data.iceberg.parquet.AirbyteValueToIcebergRecord
import java.math.BigDecimal
import org.apache.iceberg.types.Types
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IcebergNumericCoercionTest {

    private val converter = AirbyteValueToIcebergRecord()

    /**
     * The production failure: an Airtable Lookup/Rollup over a numeric field emits `[3]`, the
     * element stays an IntegerValue, and the Iceberg column is list<double>.
     */
    @Test
    fun `integers in a list of double become doubles`() {
        val type = Types.ListType.ofOptional(1, Types.DoubleType.get())
        val value = ArrayValue(listOf(IntegerValue(1L), NumberValue(BigDecimal("2.5"))))

        val converted = converter.convert(coerceIntegersForIcebergType(value, type), type) as List<*>

        assertEquals(listOf(1.0, 2.5), converted)
        converted.forEach { assertEquals(java.lang.Double::class.java, it!!.javaClass) }
    }

    @Test
    fun `integers in a list of long stay longs`() {
        val type = Types.ListType.ofOptional(1, Types.LongType.get())
        val value = ArrayValue(listOf(IntegerValue(7L)))

        assertEquals(listOf(7L), converter.convert(coerceIntegersForIcebergType(value, type), type))
    }

    @Test
    fun `integers in a struct field of type double become doubles`() {
        val type =
            Types.StructType.of(
                Types.NestedField.optional(1, "score", Types.DoubleType.get()),
                Types.NestedField.optional(2, "count", Types.LongType.get()),
            )
        val value =
            ObjectValue(linkedMapOf("score" to IntegerValue(3L), "count" to IntegerValue(4L)))

        val coerced = coerceIntegersForIcebergType(value, type) as ObjectValue

        assertEquals(NumberValue(BigDecimal(3)), coerced.values["score"])
        assertEquals(IntegerValue(4L), coerced.values["count"])
    }

    @Test
    fun `values that are not integers are returned untouched`() {
        val type = Types.DoubleType.get()
        val value = StringValue("untouched")

        assertEquals(value, coerceIntegersForIcebergType(value, type))
    }

    @Test
    fun `a scalar integer on a double column becomes a double`() {
        val type = Types.DoubleType.get()

        assertEquals(
            42.0,
            converter.convert(coerceIntegersForIcebergType(IntegerValue(42L), type), type),
        )
    }
}

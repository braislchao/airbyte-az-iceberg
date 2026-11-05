/*
 * Copyright (c) 2026 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.azure_data_lake

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.ByteArrayInputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DestinationInputPatchTest {
    private val mapper = ObjectMapper()

    @Test
    fun `overwrite streams without generations get a fresh one, others get zero`() {
        val streams =
            mapper.readTree(
                """[{"destination_sync_mode":"overwrite"},
                   {"destination_sync_mode":"append"},
                   {"destination_sync_mode":"overwrite","generation_id":7,"minimum_generation_id":7,"sync_id":3}]"""
            )

        AzureDataLakeDestination.patchStreams(streams, 1234L)

        assertEquals(listOf(1234L, 1234L), listOf(streams[0]["generation_id"].asLong(), streams[0]["minimum_generation_id"].asLong()))
        assertEquals(listOf(0L, 0L), listOf(streams[1]["generation_id"].asLong(), streams[1]["minimum_generation_id"].asLong()))
        assertEquals(listOf(7L, 7L, 3L), listOf(streams[2]["generation_id"].asLong(), streams[2]["minimum_generation_id"].asLong(), streams[2]["sync_id"].asLong()))
    }

    @Test
    fun `stream status lines are reported and the input passes through unchanged`() {
        val input =
            "{\"type\":\"RECORD\",\"record\":{}}\n" +
                "{\"type\":\"TRACE\",\"trace\":{\"type\":\"STREAM_STATUS\"}}\n"
        val seen = mutableListOf<String>()

        val out = StreamStatusLoggingInputStream(ByteArrayInputStream(input.toByteArray())) { seen.add(it) }.readBytes()

        assertEquals(input, String(out))
        assertEquals(listOf("{\"type\":\"TRACE\",\"trace\":{\"type\":\"STREAM_STATUS\"}}"), seen)
    }
}

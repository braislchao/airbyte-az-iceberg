/*
 * Copyright (c) 2026 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.azure_data_lake

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.FilterInputStream
import java.io.InputStream

private val logger = KotlinLogging.logger {}

class StreamStatusLoggingInputStream(
    input: InputStream,
    private val sink: (String) -> Unit = { logger.info { "stdin stream status: $it" } },
) : FilterInputStream(input) {
    private val head = ByteArray(HEAD_BYTES)
    private var headLen = 0

    override fun read(): Int {
        val b = super.read()
        if (b >= 0) observe(b.toByte())
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        for (i in off until off + maxOf(n, 0)) observe(b[i])
        return n
    }

    private fun observe(byte: Byte) {
        if (byte == NEWLINE) {
            val line = String(head, 0, headLen, Charsets.UTF_8)
            if (line.contains("STREAM_STATUS")) sink(line)
            headLen = 0
        } else if (headLen < HEAD_BYTES) {
            head[headLen++] = byte
        }
    }

    companion object {
        private const val HEAD_BYTES = 1024
        private const val NEWLINE = '\n'.code.toByte()
    }
}

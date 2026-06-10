/*
 * Copyright (c) 2026 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.azure_data_lake

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.airbyte.cdk.AirbyteDestinationRunner
import java.io.File

object AzureDataLakeDestination {
    @JvmStatic
    fun main(args: Array<String>) {
        println("=== args received: ${args.toList()} ===")

        // Translate positional commands (check/write/spec/discover) to flag form (--check, etc.)
        val translatedArgs = if (args.isNotEmpty() && !args[0].startsWith("--")) {
            arrayOf("--${args[0]}") + args.drop(1).toTypedArray()
        } else {
            args
        }
        println("=== translated args: ${translatedArgs.toList()} ===")

        // For write operations, patch the catalog to add missing fields that older
        // Airbyte servers don't include but the CDK requires.
        val finalArgs = if (translatedArgs.contains("--write")) {
            patchCatalogIfNeeded(translatedArgs)
        } else {
            translatedArgs
        }

        AirbyteDestinationRunner.run(*finalArgs)
    }

    private fun patchCatalogIfNeeded(args: Array<String>): Array<String> {
        val catalogIdx = args.indexOf("--catalog")
        if (catalogIdx < 0 || catalogIdx + 1 >= args.size) return args

        val originalPath = args[catalogIdx + 1]
        val originalFile = File(originalPath)
        if (!originalFile.exists()) return args

        val mapper = ObjectMapper()
        val root = mapper.readTree(originalFile) as ObjectNode
        val streams = root.get("streams") ?: return args

        var modified = false
        streams.forEach { configured ->
            val node = configured as ObjectNode
            if (!node.has("generation_id") || node.get("generation_id").isNull) {
                node.put("generation_id", 0L)
                modified = true
            }
            if (!node.has("minimum_generation_id") || node.get("minimum_generation_id").isNull) {
                node.put("minimum_generation_id", 0L)
                modified = true
            }
            if (!node.has("sync_id") || node.get("sync_id").isNull) {
                node.put("sync_id", 0L)
                modified = true
            }
        }

        if (!modified) return args

        // Write patched catalog to a new file
        val patchedFile = File("/tmp/catalog_patched.json")
        mapper.writerWithDefaultPrettyPrinter().writeValue(patchedFile, root)
        println("=== catalog patched at ${patchedFile.absolutePath} ===")

        val newArgs = args.toMutableList()
        newArgs[catalogIdx + 1] = patchedFile.absolutePath
        return newArgs.toTypedArray()
    }
}

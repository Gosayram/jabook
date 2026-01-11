#!/usr/bin/env kotlin

@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Script to automatically fix Explicit API mode violations in Kotlin files.
 * 
 * This script:
 * 1. Finds all Kotlin files with Explicit API violations
 * 2. Adds `public` modifier where needed
 * 3. Adds return types where needed
 */

data class Violation(
    val file: File,
    val line: Int,
    val column: Int,
    val type: ViolationType
)

enum class ViolationType {
    VISIBILITY,
    RETURN_TYPE
}

fun main(args: Array<String>) {
    val projectRoot = File(args.getOrElse(0) { "." })
    val androidSrc = File(projectRoot, "android/app/src/main/kotlin")
    
    if (!androidSrc.exists()) {
        println("Error: android/app/src/main/kotlin directory not found")
        return
    }
    
    println("Finding Kotlin files...")
    val kotlinFiles = androidSrc.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()
    
    println("Found ${kotlinFiles.size} Kotlin files")
    println("Processing files...")
    
    var totalFixed = 0
    
    kotlinFiles.forEach { file ->
        val fixed = fixFile(file)
        if (fixed > 0) {
            println("Fixed $fixed violations in ${file.relativeTo(projectRoot)}")
            totalFixed += fixed
        }
    }
    
    println("\nTotal violations fixed: $totalFixed")
}

fun fixFile(file: File): Int {
    val content = file.readText()
    val lines = content.lines().toMutableList()
    var fixed = 0
    
    // Fix 1: Add public to top-level functions without visibility modifier
    // Pattern: ^fun (not preceded by public/private/internal/protected)
    for (i in lines.indices) {
        val line = lines[i]
        
        // Fix top-level functions
        if (line.matches(Regex("^\\s*fun\\s+\\w+.*"))) {
            if (!line.contains(Regex("\\b(public|private|internal|protected)\\s+fun"))) {
                lines[i] = line.replace(Regex("^(\\s*)fun"), "$1public fun")
                fixed++
            }
        }
        
        // Fix top-level classes
        if (line.matches(Regex("^\\s*(class|object|interface|enum class|data class|sealed class|sealed interface)\\s+\\w+.*"))) {
            if (!line.contains(Regex("\\b(public|private|internal|protected)\\s+(class|object|interface|enum class|data class|sealed class|sealed interface)"))) {
                val pattern = Regex("^(\\s*)(class|object|interface|enum class|data class|sealed class|sealed interface)")
                lines[i] = line.replace(pattern, "$1public $2")
                fixed++
            }
        }
        
        // Fix companion objects
        if (line.matches(Regex("^\\s*companion\\s+object\\s*.*"))) {
            if (!line.contains(Regex("\\b(public|private|internal|protected)\\s+companion"))) {
                lines[i] = line.replace(Regex("^(\\s*)companion"), "$1public companion")
                fixed++
            }
        }
        
        // Fix const val in companion objects
        if (line.matches(Regex("^\\s*const\\s+val\\s+\\w+.*"))) {
            if (!line.contains(Regex("\\b(public|private|internal|protected)\\s+const"))) {
                lines[i] = line.replace(Regex("^(\\s*)const"), "$1public const")
                fixed++
            }
        }
        
        // Fix val/var properties in classes (public by default, but need explicit in explicit API mode)
        // Only fix if it's a class member (indented) and not already has modifier
        if (line.matches(Regex("^\\s{4,}(val|var)\\s+\\w+.*"))) {
            if (!line.contains(Regex("\\b(public|private|internal|protected)\\s+(val|var)")) &&
                !line.contains(Regex("\\b(private|internal)\\s+(val|var)"))) {
                // Check if it's a public property (not private/internal)
                // We'll add public only if it seems to be a public API
                if (line.contains(Regex("@Inject|lateinit|public|@JvmField"))) {
                    lines[i] = line.replace(Regex("^(\\s{4,})(val|var)"), "$1public $2")
                    fixed++
                }
            }
        }
    }
    
    // Fix 2: Add return types to functions without explicit return type
    // This is more complex and requires parsing
    for (i in lines.indices) {
        val line = lines[i]
        
        // Fix functions without return type (but not constructors or property getters)
        if (line.matches(Regex("^\\s*(public\\s+)?fun\\s+\\w+.*\\)\\s*\\{"))) {
            if (!line.contains(":") && !line.contains("get()") && !line.contains("set(")) {
                // Check if it's a Unit function (ends with {)
                if (line.trimEnd().endsWith("{")) {
                    // Add : Unit before {
                    lines[i] = line.replace(Regex("(\\s*)\\{$"), "$1: Unit {")
                    fixed++
                }
            }
        }
        
        // Fix functions with expression body that need return type
        if (line.matches(Regex("^\\s*(public\\s+)?fun\\s+\\w+.*\\)\\s*="))) {
            if (!line.contains(":")) {
                // Try to infer return type from expression
                // For now, we'll add : Unit for simple cases
                val expr = line.substringAfter("=").trim()
                if (expr.isEmpty() || expr == "run {" || expr == "run {") {
                    lines[i] = line.replace(Regex("=\\s*$"), ": Unit =")
                    fixed++
                }
            }
        }
    }
    
    if (fixed > 0) {
        file.writeText(lines.joinToString("\n"))
    }
    
    return fixed
}

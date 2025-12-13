buildscript {
    repositories {
        google()
        mavenCentral()
    }
}



val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
// Workaround for Flutter plugins not specifying namespace (AGP 8.0+)
subprojects {
    afterEvaluate {
        val android = extensions.findByName("android")
        if (android != null) {
            try {
                val getNamespace = android.javaClass.getMethod("getNamespace")
                val currentNamespace = getNamespace.invoke(android)
                
                if (currentNamespace == null) {
                    val setNamespace = android.javaClass.getMethod("setNamespace", String::class.java)
                    
                    // Known namespace mappings for Flutter plugins
                    val knownNamespaces = mapOf(
                        "flutter_media_metadata" to "com.alexmercerind.flutter_media_metadata",
                    )
                    
                    val packageName = knownNamespaces[project.name]
                        ?: run {
                            // Try to extract from AndroidManifest.xml if it exists
                            val manifestFile = project.file("src/main/AndroidManifest.xml")
                            if (manifestFile.exists()) {
                                val manifestContent = manifestFile.readText()
                                val packageMatch = Regex("""package=["']([^"']+)["']""").find(manifestContent)
                                packageMatch?.groupValues?.get(1)
                            } else {
                                null
                            }
                        } ?: run {
                            // Use group if valid, otherwise fallback
                            if (project.group.toString() != "unspecified") {
                                project.group.toString()
                            } else {
                                "com.example.${project.name}"
                            }
                        }
                    
                    // packageName is always non-null due to fallback above
                    setNamespace.invoke(android, packageName)
                    // Suppress log for known plugins to reduce noise
                    if (project.name !in knownNamespaces.keys) {
                        println("Set namespace for ${project.name} to $packageName")
                    }
                }
                // FORCE compileSdk to 34 for all plugins to fix mismatch with AndroidX dependencies
                val getCompileSdk = android.javaClass.getMethod("getCompileSdkVersion")
                val currentCompileSdk = getCompileSdk.invoke(android) as? String // e.g., "android-34"
                
                // Parse version number
                val sdkVersion = currentCompileSdk?.substringAfter("android-")?.toIntOrNull()
                
                if (sdkVersion != null && sdkVersion < 34) {
                     val setCompileSdkVersion = android.javaClass.getMethod("setCompileSdkVersion", Int::class.javaPrimitiveType)
                     setCompileSdkVersion.invoke(android, 34)
                     println("Forced compileSdk for ${project.name} from $sdkVersion to 34")
                }

            } catch (e: Exception) {
                // Ignore if method not found
            }
        }
    }
}

subprojects {
    project.evaluationDependsOn(":app")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}



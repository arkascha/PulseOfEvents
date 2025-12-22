plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.symbol.processing)
}

android {
    namespace = "org.rustygnome.pulse"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.rustygnome.pulse"
        minSdk = 27
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
    }
}

// Automatically discover and package pulses
val pulsesSrcDir = project.file("src/main/assets/pulses_src")
val pulsesOutputDir = project.file("src/main/assets/pulses")

val zipPulsesTask = tasks.register("zipPulses")

if (pulsesSrcDir.exists() && pulsesSrcDir.isDirectory) {
    pulsesSrcDir.listFiles()?.filter { it.isDirectory }?.forEach { pulseDir ->
        val pulseName = pulseDir.name
        val zipTask = tasks.register<Zip>("zipPulse_$pulseName") {
            archiveFileName.set("$pulseName.pulse")
            destinationDirectory.set(pulsesOutputDir)
            
            // Files from the pulse source folder
            from(pulseDir)
            
            // Logic to bundle the correct sounds based on config.json
            val configFile = file("${pulseDir}/config.json")
            if (configFile.exists()) {
                val content = configFile.readText()
                val match = Regex("\"acousticStyle\"\\s*:\\s*\"([^\"]+)\"").find(content)
                val style = match?.groupValues?.get(1)?.trim() ?: ""
                
                val soundsFolderName = when (style) {
                    "99Sounds Percussion I" -> "99Sounds/99Sounds Drum Samples I"
                    "99Sounds Percussion II" -> "99Sounds/99Sounds Drum Samples II"
                    "orchestra_tada" -> "Orchestra/tada"
                    "orchestra_violin" -> "Orchestra/violin"
                    "orchestra_triumphant" -> "Orchestra/triumphant"
                    "rock" -> "rock"
                    "trance" -> "trance"
                    "orchestral" -> "orchestral"
                    else -> null
                }
                
                if (soundsFolderName != null) {
                    val soundsDir = project.file("src/main/assets/sounds/$soundsFolderName")
                    if (soundsDir.exists()) {
                        // Place selected sounds into a 'sounds/' folder inside the ZIP
                        from(soundsDir) {
                            into("sounds")
                        }
                    }
                }
            }
        }
        zipPulsesTask.configure {
            dependsOn(zipTask)
        }
    }
}

// Task to generate an index of all pulses
val generatePulsesIndex = tasks.register("generatePulsesIndex") {
    dependsOn(zipPulsesTask)
    doLast {
        val index = mutableListOf<Map<String, String>>()
        pulsesSrcDir.listFiles()?.filter { it.isDirectory }?.forEach { pulseDir ->
            val configFile = file("${pulseDir}/config.json")
            if (configFile.exists()) {
                val content = configFile.readText()
                val nameMatch = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(content)
                val descMatch = Regex("\"description\"\\s*:\\s*\"([^\"]+)\"").find(content)
                
                val pulseName = nameMatch?.groupValues?.get(1) ?: pulseDir.name
                val pulseDesc = descMatch?.groupValues?.get(1) ?: ""
                
                index.add(mapOf(
                    "filename" to "${pulseDir.name}.pulse",
                    "name" to pulseName,
                    "description" to pulseDesc
                ))
            }
        }
        val indexFile = file("${pulsesOutputDir}/pulses_index.json")
        indexFile.writeText(groovy.json.JsonOutput.toJson(index))
        println("Generated pulse index at: ${indexFile.absolutePath}")
    }
}

// Ensure pulses and index are packaged before assets are merged
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(generatePulsesIndex)
}

// Clean generated pulses
tasks.clean {
    delete(pulsesOutputDir)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.kafka.clients)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.json.path)
    implementation(libs.gson)
    implementation(libs.rhino)
    implementation(libs.okhttp)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

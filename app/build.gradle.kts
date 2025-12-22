plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.symbol.processing)
}

android {
    namespace = "org.rustygnome.pulse"
    compileSdk = 36

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            buildConfigField("String", "KAFKA_BOOTSTRAP_SERVERS", "\"localhost:9092\"")
            buildConfigField("String", "KAFKA_TOPIC", "\"dev_topic\"")
            buildConfigField("String", "KAFKA_API_KEY", "\"your_dev_api_key\"")
            buildConfigField("String", "KAFKA_API_SECRET", "\"your_dev_api_secret\"")
        }
        create("int") {
            dimension = "environment"
            buildConfigField("String", "KAFKA_BOOTSTRAP_SERVERS", "\"int.server:9092\"")
            buildConfigField("String", "KAFKA_TOPIC", "\"int_topic\"")
            buildConfigField("String", "KAFKA_API_KEY", "\"your_int_api_key\"")
            buildConfigField("String", "KAFKA_API_SECRET", "\"your_int_api_secret\"")
        }
        create("prd") {
            dimension = "environment"
            buildConfigField("String", "KAFKA_BOOTSTRAP_SERVERS", "\"your_kafka_bootstrap_server:9092\"")
            buildConfigField("String", "KAFKA_TOPIC", "\"your_kafka_topic\"")
            buildConfigField("String", "KAFKA_API_KEY", "\"your_prd_api_key\"")
            buildConfigField("String", "KAFKA_API_SECRET", "\"your_prd_api_secret\"")
        }
    }

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

// Automatically discover and package plugins
val pluginsSrcDir = project.file("src/main/assets/plugins_src")
val pluginsOutputDir = project.file("src/main/assets/plugins")

val zipPluginsTask = tasks.register("zipPlugins")

if (pluginsSrcDir.exists() && pluginsSrcDir.isDirectory) {
    pluginsSrcDir.listFiles()?.filter { it.isDirectory }?.forEach { pluginDir ->
        val pluginName = pluginDir.name
        val zipTask = tasks.register<Zip>("zipPlugin_$pluginName") {
            archiveFileName.set("$pluginName.pulse")
            destinationDirectory.set(pluginsOutputDir)
            
            // Files from the plugin source folder
            from(pluginDir)
            
            // Logic to bundle the correct sounds based on config.json
            val configFile = file("${pluginDir}/config.json")
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
        zipPluginsTask.configure {
            dependsOn(zipTask)
        }
    }
}

// Ensure plugins are packaged before assets are merged for all build variants
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(zipPluginsTask)
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

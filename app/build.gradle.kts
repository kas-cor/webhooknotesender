plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "com.kascorp.webhooknotesender"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kascorp.webhooknotesender"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val keystorePath = System.getenv("KEYSTORE_PATH") ?: "../webhooknotesender-release.jks"
    val keystorePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
    val keystoreFile = file(keystorePath)

    if (keystoreFile.exists() && keystorePassword.isNotEmpty()) {
        signingConfigs {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = System.getenv("KEY_ALIAS") ?: "webhooknotesender"
                keyPassword = System.getenv("KEY_PASSWORD") ?: keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystoreFile.exists() && keystorePassword.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,LICENSE.md,LICENSE-notice.md,NOTICE.md}"
        }
    }
}

// ──────────────────────────────────────────────
// Task: проверка хардкодных строк в Kotlin-коде
// ──────────────────────────────────────────────

abstract class HardcodedStringsCheckTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDir: DirectoryProperty

    @TaskAction
    fun check() {
        val dir = sourceDir.get().asFile
        if (!dir.exists()) {
            logger.warn("Source directory not found: $dir")
            return
        }

        val violations = mutableListOf<String>()

        val contentDescPattern = Regex("""contentDescription\s*=\s*"([^"]+)"""")
        val toastPattern = Regex("""Toast\.makeText\([^,]+,\s*"([^"]+)"""")
        val notificationPattern = Regex("""setContent(Text|Title)\(\s*"([^"]+)"\s*\)""")

        dir.walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { file ->
                val relativePath = dir.toPath().relativize(file.toPath()).toString()
                val lines = file.readLines()

                lines.forEachIndexed { lineIndex, line ->
                    val lineNum = lineIndex + 1

                    contentDescPattern.findAll(line).forEach { match ->
                        val captured = match.groupValues[1]
                        if (captured.isNotEmpty() && !captured.startsWith("\$") && !captured.startsWith("{")) {
                            violations.add("$relativePath:$lineNum: contentDescription хардкод: \"$captured\" → stringResource(R.string.xxx)")
                        }
                    }

                    toastPattern.findAll(line).forEach { match ->
                        val captured = match.groupValues[1]
                        if (captured.isNotEmpty() && !captured.startsWith("\$") && !captured.startsWith("{")) {
                            violations.add("$relativePath:$lineNum: Toast хардкод: \"$captured\" → context.getString(R.string.xxx)")
                        }
                    }

                    notificationPattern.findAll(line).forEach { match ->
                        val captured = match.groupValues[2]
                        if (captured.isNotEmpty() && !captured.startsWith("\$") && !captured.startsWith("{")) {
                            violations.add("$relativePath:$lineNum: setContent${match.groupValues[1]} хардкод: \"$captured\" → getString(R.string.xxx)")
                        }
                    }
                }
            }

        if (violations.isNotEmpty()) {
            logger.error("Найдены хардкодные строки в коде! Все строки для пользователя должны быть в strings.xml")
            violations.forEach { logger.error("  > $it") }
            throw GradleException("Найдено ${violations.size} хардкодных строк. Исправь их перед коммитом.")
        } else {
            logger.lifecycle("✅ Хардкодные строки не найдены. Молодцы!")
        }
    }
}

tasks.register<HardcodedStringsCheckTask>("checkHardcodedStrings") {
    description = "Scans Kotlin source files for hardcoded user-facing strings"
    group = "verification"
    sourceDir.set(layout.projectDirectory.dir("src/main/java"))
}

// Подключаем к стандартной проверке
val checkTask = tasks.findByName("check")
if (checkTask != null) {
    checkTask.dependsOn("checkHardcodedStrings")
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // Navigation
    implementation(libs.navigation.compose)

    // Lifecycle
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)

    // OkHttp
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // DataStore
    implementation(libs.datastore.preferences)

    // Kotlinx
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // Testing — unit
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.work.testing)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)

    // Testing — Compose UI (instrumentation / androidTest)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(libs.mockk)
    androidTestImplementation(libs.coroutines.test)
}

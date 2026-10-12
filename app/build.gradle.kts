import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
}
val privateSigning = Properties()
val signingFile = file(System.getenv("P38_SIGNING_PROPERTIES") ?: "${System.getProperty("user.home")}/.p38journal/signing.properties")
if (signingFile.exists()) signingFile.inputStream().use { privateSigning.load(it) }
android {
    namespace = "dev.p38journal"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.p38journal"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "5.0.0"
        resValue("string", "app_name", "P38 Journal")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        javaCompileOptions { annotationProcessorOptions { arguments["room.schemaLocation"] = "$projectDir/schemas" } }
    }
    signingConfigs {
        create("privateRelease") {
            if (signingFile.exists()) {
                storeFile = file(privateSigning.getProperty("storeFile"))
                storePassword = privateSigning.getProperty("storePassword")
                keyAlias = privateSigning.getProperty("keyAlias")
                keyPassword = privateSigning.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug"; resValue("string", "app_name", "P38 Journal \u00b7 Prueba") }
        release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("privateRelease") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES") }
    testOptions { unitTests.isReturnDefaultValues = true }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}
kapt { correctErrorTypes = true }
dependencies {
    implementation(platform("androidx.compose:compose-bom:${libs.versions.compose.get()}"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-runtime:${libs.versions.room.get()}")
    implementation("androidx.room:room-ktx:${libs.versions.room.get()}")
    kapt("androidx.room:room-compiler:${libs.versions.room.get()}")
    implementation("androidx.work:work-runtime-ktx:${libs.versions.work.get()}")
    implementation("androidx.datastore:datastore-preferences:1.1.2")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:${libs.versions.compose.get()}"))
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:${libs.versions.room.get()}")
    androidTestImplementation("androidx.work:work-testing:${libs.versions.work.get()}")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

val syncCatalogue by tasks.registering(Copy::class) {
    from(rootProject.file("data")) { include("maintenance-catalogue.json", "vehicle.json") }
    into(file("src/main/assets"))
}
tasks.named("preBuild") { dependsOn(syncCatalogue) }

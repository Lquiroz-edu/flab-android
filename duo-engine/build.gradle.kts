plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.duofold.live"
    compileSdk = 36
    defaultConfig {
        minSdk = 34
        buildConfigField("int", "VERSION_CODE", "21")
        buildConfigField("String", "VERSION_NAME", "\"0.8.0-duo-global\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }

// Descriptions of hidden framework types are compile-only, never packaged in the APK.
val wallpaperStubs by tasks.registering(JavaCompile::class) {
    source(fileTree("wallpaper-stubs") { include("**/*.java") })
    classpath = files(android.bootClasspath)
    destinationDirectory.set(layout.buildDirectory.dir("wallpaper-stubs"))
    sourceCompatibility = "17"
    targetCompatibility = "17"
}
val wallpaperStubJar by tasks.registering(Jar::class) {
    dependsOn(wallpaperStubs)
    from(wallpaperStubs.map { it.destinationDirectory })
    archiveFileName.set("wallpaper-framework-stubs.jar")
    destinationDirectory.set(layout.buildDirectory.dir("compile-only"))
}
dependencies {
    compileOnly(files(wallpaperStubJar))
    implementation(fileTree("libs") { include("*.jar") })
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    implementation(libs.androidx.window)
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}

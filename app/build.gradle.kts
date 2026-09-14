import java.util.Properties

plugins { id("com.android.application") }

val releaseSigningFile = providers.environmentVariable("FOLDY_SIGNING_PROPERTIES")
    .orElse("${System.getProperty("user.home")}/.config/foldy/signing/release.properties")
    .map { file(it) }
val releaseSigningProperties = Properties().apply {
    if (releaseSigningFile.get().isFile) releaseSigningFile.get().reader(Charsets.UTF_8).use { load(it) }
}
val validateReleaseSigning by tasks.registering {
    group = "verification"
    description = "Require an explicit release key; never substitute the Android debug key."
    doLast {
        val required = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        check(releaseSigningFile.get().isFile && required.all {
            !releaseSigningProperties.getProperty(it).isNullOrBlank()
        }) { "Release signing is missing. Configure FOLDY_SIGNING_PROPERTIES; see RELEASE.md." }
        check(file(releaseSigningProperties.getProperty("storeFile")).isFile) {
            "The configured release keystore does not exist."
        }
        check(file(releaseSigningProperties.getProperty("storeFile")).name != "debug.keystore") {
            "The Android debug keystore cannot be used for release."
        }
    }
}
android {
    namespace = "dev.poldy.lab"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.poldy.lab"
        minSdk = 33
        targetSdk = 36
        versionCode = 80
        versionName = "0.21.3"
        testInstrumentationRunner = "dev.poldy.lab.RendererBenchmark"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { aidl = true }
    signingConfigs {
        create("distribution") {
            releaseSigningProperties.getProperty("storeFile")?.let { storeFile = file(it) }
            storePassword = releaseSigningProperties.getProperty("storePassword")
            keyAlias = releaseSigningProperties.getProperty("keyAlias")
            keyPassword = releaseSigningProperties.getProperty("keyPassword")
            storeType = releaseSigningProperties.getProperty("storeType", "PKCS12")
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            isJniDebuggable = false
            signingConfig = signingConfigs.getByName("distribution")
            // Shizuku constructors, app_process entry points and hidden framework reflection
            // rely on stable names. Enable shrinking only after a separate compatibility review.
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(validateReleaseSigning) }
// Compile against verified hidden framework signatures without packaging replacement framework classes.
val compilePlatformStubs by tasks.registering(JavaCompile::class) {
    source(fileTree("src/platformStubs/java") { include("**/*.java") })
    classpath = files(androidComponents.sdkComponents.bootClasspath)
    destinationDirectory.set(layout.buildDirectory.dir("platform-stubs/classes"))
    sourceCompatibility = "17"
    targetCompatibility = "17"
}
val platformStubJar by tasks.registering(Jar::class) {
    dependsOn(compilePlatformStubs)
    from(compilePlatformStubs.flatMap { it.destinationDirectory })
    archiveFileName.set("platform-stubs.jar")
    destinationDirectory.set(layout.buildDirectory.dir("platform-stubs"))
}
dependencies {
    compileOnly(files(platformStubJar))
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    testImplementation("junit:junit:4.13.2")
}

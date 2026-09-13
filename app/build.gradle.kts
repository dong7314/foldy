plugins { id("com.android.application") }
android {
    namespace = "dev.poldy.lab"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.poldy.lab"
        minSdk = 33
        targetSdk = 36
        versionCode = 67
        versionName = "0.18.4"
        testInstrumentationRunner = "dev.poldy.lab.RendererBenchmark"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { aidl = true }
}
dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    testImplementation("junit:junit:4.13.2")
}

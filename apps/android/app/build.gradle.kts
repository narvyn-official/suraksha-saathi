plugins { id("com.android.application") }

android {
    namespace = "com.narvyn.suraksha"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.narvyn.suraksha"
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    sourceSets["main"].assets.srcDir("../../../content")
}
dependencies {
    implementation("androidx.core:core:1.17.0")
    implementation("com.google.ar:core:1.50.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

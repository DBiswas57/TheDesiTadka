plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation(project(":core-model"))
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}

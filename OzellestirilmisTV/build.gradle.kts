plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(project(":common"))
    implementation("com.google.code.gson:gson:2.8.9")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
}
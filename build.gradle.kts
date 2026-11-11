buildscript {
    dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21") }
}
plugins {
    id("com.android.application") version "8.11.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

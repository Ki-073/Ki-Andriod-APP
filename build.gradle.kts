// Top-level build file
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.4.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.4.10")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

task("clean") {
    doLast {
        delete(rootProject.layout.buildDirectory)
    }
}
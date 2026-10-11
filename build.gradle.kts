// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.spotless)
}

// Code formatting. `./gradlew spotlessCheck` reports violations; `./gradlew spotlessApply` fixes them.
// Deliberately not wired into assembleRelease, so builds never fail over style.
spotless {
  kotlin {
    target("app/src/**/*.kt")
    ktlint(libs.versions.ktlint.get())
  }
  kotlinGradle {
    target("*.gradle.kts", "app/*.gradle.kts")
    ktlint(libs.versions.ktlint.get())
  }
}

// Root build: declares the plugins used by the subprojects with `apply false`,
// so they are loaded once at the root.
plugins {
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

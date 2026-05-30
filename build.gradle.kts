plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}

tasks.register<Copy>("installGitHooks") {
    description = "Installs git hooks from scripts/git-hooks to .git/hooks"
    group = "setup"
    from("${rootProject.rootDir}/scripts/git-hooks") {
        include("*")
    }
    into("${rootProject.rootDir}/.git/hooks")
    fileMode = 0b111101101 // rwxr-xr-x (0755)
}


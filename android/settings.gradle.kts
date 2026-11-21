pluginManagement {
    // In CI, resolve dependencies from official repositories first. The Aliyun
    // mirrors are kept for local mainland-China builds, but a 502 from a mirror
    // is fatal for Gradle (it does not fall through to the next repository).
    val useOfficialReposOnly =
        System.getenv("QM_OFFICIAL_REPOS_ONLY")?.equals("true", ignoreCase = true) == true ||
            System.getenv("CI")?.equals("true", ignoreCase = true) == true
    repositories {
        if (!useOfficialReposOnly) {
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    val useOfficialReposOnly =
        System.getenv("QM_OFFICIAL_REPOS_ONLY")?.equals("true", ignoreCase = true) == true ||
            System.getenv("CI")?.equals("true", ignoreCase = true) == true
    repositories {
        if (!useOfficialReposOnly) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
        // JitPack and GitLab have no usable domestic mirror (Miuix uses JitPack).
        maven("https://jitpack.io")
        maven("https://gitlab.com/api/v4/projects/18497829/packages/maven")
    }
}

rootProject.name = "qm-updater-android"
include(":app")

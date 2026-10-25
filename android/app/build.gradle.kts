import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties


// Miuix (HyperOS 风格 Compose UI 库) 版本。要求 Kotlin >= 2.4.20 + Compose 1.12.x
val miuixVersion = "0.9.4"

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    namespace = "com.qm.updater"
    compileSdk = 37

    val buildNumber = System.getenv("BUILD_NUMBER").orEmpty()
    val buildVersion = System.getenv("BUILD_VERSION").orEmpty()

    // 版本号双端同源、全程动态获取，杜绝写死：
    //  - versionName 优先级：BUILD_VERSION（CI 注入：正式=tag 版本 / 测试=正式版本-build.编号）
    //      > 仓库根 package.json（唯一版本源）> 兜底字面量
    //  - versionCode 优先级：BUILD_NUMBER（CI 注入：正式=tag 派生 / 测试=CI run 编号）
    //      > git 提交计数（本地构建每次提交自动递增，修复"版本号永远不变"）
    //      > 由版本号派生 X*10000+Y*100+Z
    val pkgVersion = try {
        Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(file("../../package.json").readText())?.groupValues?.get(1)
    } catch (_: Exception) { null }
    val gitCommitCount = try {
        val proc = ProcessBuilder("git", "rev-list", "--count", "HEAD")
            .directory(file("../..")).redirectErrorStream(false).start()
        proc.inputStream.bufferedReader().readText().trim().takeIf { it.isNotEmpty() }?.toInt()
    } catch (_: Exception) { null }

    val releaseVersion = buildVersion.ifEmpty { pkgVersion ?: "2.1.0" }
    val releaseCode = when {
        buildNumber.isNotEmpty() -> buildNumber.toInt()
        gitCommitCount != null && gitCommitCount > 0 -> gitCommitCount
        else -> {
            val p = releaseVersion.split(".").map { it.toIntOrNull() ?: 0 }
            p.getOrElse(0) { 0 } * 10000 + p.getOrElse(1) { 0 } * 100 + p.getOrElse(2) { 0 }
        }
    }

    defaultConfig {
        applicationId = "com.qm.updater" + System.getenv("BUILD_TAG").orEmpty()
        minSdk = 26
        targetSdk = 37
        versionCode = releaseCode
        versionName = releaseVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            try {
                val props = Properties()
                props.load(FileInputStream(file("../local.properties")))
                storeFile = file(props.getProperty("keystore.file"))
                storePassword = props.getProperty("keystore.password")
                keyAlias = props.getProperty("keystore.keyalias")
                keyPassword = props.getProperty("keystore.keypassword")
            } catch (_: Exception) {
                val config = signingConfigs.getByName("debug")
                storeFile = config.storeFile
                storePassword = config.storePassword
                keyAlias = config.keyAlias
                keyPassword = config.keyPassword
                println("Signing config not found. Using debug settings.")
            }
            enableV3Signing = true
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        warning.addAll(arrayOf("ExtraTranslation", "MissingTranslation", "MissingQuantity"))
    }
}

dependencies {

    // Shizuku：没有 root 时的第二条静默安装通道。
    // 只用它的 newProcess()（返回一个远程 Process），不写自定义 AIDL。
    // 固定用 13.1.0：实测 13.1.5 已把 newProcess 收回为 private
    // （javap 确认公开方法只剩 pingBinder/getBinder/getVersion），
    // 而 13.1.0 仍然公开 newProcess —— 那正是本项目执行 pm install 需要的入口，
    // 用它就不必自己写 AIDL + UserService。
    implementation("dev.rikka.shizuku:api:13.1.0")
    implementation("dev.rikka.shizuku:provider:13.1.0")

    // Compose BOM 2026.09.00 -> androidx.compose 1.12.1，与 Miuix 依赖的 CMP 1.12.0 对齐
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")

    // Miuix —— HyperOS 风格 Compose Multiplatform UI 库
    implementation("top.yukonga.miuix.kmp:miuix-ui:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-preference:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-icons:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-squircle:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-nav:$miuixVersion")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.navigation:navigation-runtime-ktx:2.10.2")
    implementation("androidx.tv:tv-foundation:1.0.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    // KryptoPrefs 是 Kotlin 1.3 时代的上游库，传递引入 kotlin-android-extensions-runtime:1.3.50，
    // 与 gplayapi 的 kotlin-parcelize-runtime 冲突（Duplicate class kotlinx.android.parcel.*，
    // Gradle 9.8+ 严格校验直接报错）。本项目未用 @Parcelize，排除该旧运行时。
    implementation("com.github.rumboalla.KryptoPrefs:kryptoprefs-gson:0.4.3") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-android-extensions-runtime")
    }
    implementation("com.github.rumboalla.KryptoPrefs:kryptoprefs:0.4.3") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-android-extensions-runtime")
    }
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    implementation("com.auroraoss:gplayapi:3.6.4")
    implementation("com.google.code.gson:gson:2.15.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.5.1")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    implementation("io.github.g00fy2:versioncompare:1.5.0")
    implementation("io.insert-koin:koin-android:4.2.2")
    implementation("io.insert-koin:koin-androidx-compose:4.2.2")
    implementation("org.jsoup:jsoup:1.23.2")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")

    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")

}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { _ ->
            tasks.configureEach {
                if (name == "package${variant.name.replaceFirstChar { it.uppercase() }}") {
                    doLast {
                        outputs.files.files
                            .flatMap { it.walkTopDown().filter { f -> f.extension == "apk" }.toList() }
                            .forEach { apk -> runCatching { apk.copyTo(File(apk.parentFile, "${android.defaultConfig.applicationId}-${variant.buildType}.apk"), true) }.getOrNull() }
                    }
                }
            }
        }
    }
}

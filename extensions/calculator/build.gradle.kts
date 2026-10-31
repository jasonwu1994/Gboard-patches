extension {
    name = "extensions/gboard-calculator.rve"
}

android {
    namespace = "dev.jason.gboardpatches.calculator"

    defaultConfig {
        minSdk = 26
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        checkOnly += "NewApi"
    }
}

tasks.named("syncExtension") {
    dependsOn("lintDebug")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

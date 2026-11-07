/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * Optional Vert.x integration for DTE. Vert.x is compileOnlyApi: the application supplies io.vertx:vertx-core
 * (4.5.x or 5.x), so no Vert.x version is forced on it and the core project stays Vert.x-free.
 */

plugins {
    `java-library`
    jacoco
    checkstyle
    id("com.github.spotbugs")
}

repositories {
    mavenCentral()
}

// Vert.x version for the tests only; -PvertxVersion=5.x.y runs them against Vert.x 5.
val vertxVersion: String = providers.gradleProperty("vertxVersion").getOrElse(libs.versions.vertx.get())

dependencies {
    api(project(":"))
    compileOnlyApi(libs.vertx.core)

    testImplementation("io.vertx:vertx-core:$vertxVersion")
    testImplementation("io.vertx:vertx-junit5:$vertxVersion")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    spotbugsPlugins("com.h3xstream.findsecbugs:findsecbugs-plugin:1.12.0")
    spotbugsSlf4j("org.slf4j:slf4j-simple:2.0.12")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

sourceSets {
    test {
        // Reuse the core test fixtures (configurations, model results, OpenRTB request).
        resources.srcDir(rootProject.file("src/test/resources"))
    }
}

jacoco {
    toolVersion = "0.8.9"
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.1".toBigDecimal()
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
    // The core log4j2.xml writes logs/ relative to the working directory; keep it under build/.
    workingDir = layout.buildDirectory.get().asFile
    systemProperty("test.properties", "vertx-test.properties")
    systemProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.JULLogDelegateFactory")
    finalizedBy(tasks.jacocoTestReport)
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

spotbugs {
    toolVersion.set("4.8.3")
    ignoreFailures.set(false)
    effort.set(com.github.spotbugs.snom.Effort.MAX)
    reportLevel.set(com.github.spotbugs.snom.Confidence.HIGH)
    excludeFilter.set(rootProject.file("config/spotbugs/spotbugs-exclude.xml"))
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("html") {
        required.set(true)
    }
}

checkstyle {
    toolVersion = "8.45"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
    isShowViolations = true
}

tasks.javadoc {
    options {
        (this as StandardJavadocDocletOptions).apply {
            addStringOption("Xdoclint:none", "-quiet")
            encoding = "UTF-8"
            charSet = "UTF-8"
            windowTitle = "DemandDrivenTrafficEvaluator Vert.x"
        }
    }
}

tasks.jar {
    // DemandDrivenTrafficEvaluator-<core version>-vertx.jar, next to the core jar.
    archiveBaseName.set("DemandDrivenTrafficEvaluator")
    archiveClassifier.set("vertx")
    archiveVersion.set(rootProject.tasks.named<Jar>("jar").flatMap { it.archiveVersion })
}

tasks.build {
    dependsOn(tasks.javadoc)
}

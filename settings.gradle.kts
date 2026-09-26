// Lets Gradle download the Java toolchain the build asks for, instead of failing on a machine that
// does not happen to have that exact JDK. The IntelliJ Platform Gradle Plugin requires a Java 21
// toolchain, while current JetBrains IDEs bundle JBR 25, so without this the usual developer machine
// cannot configure the build at all.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "covdbg-coverage"

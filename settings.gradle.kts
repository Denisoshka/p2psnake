rootProject.name = "kotlinp2psnake"
pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
  }
  include(":desktop")
  include(":proto")
}
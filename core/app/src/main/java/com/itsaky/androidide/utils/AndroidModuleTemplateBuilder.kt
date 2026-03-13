/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.utils

object AndroidModuleTemplateBuilder {

  enum class Language {
    JAVA,
    KOTLIN
  }

  private fun compileSdkVersion() =
    COMPILE_SDK_VERSION.api

  private fun javaSourceVersion() =
    "JavaVersion.VERSION_$JAVA_SOURCE_VERSION"
  private fun javaTargetVersion() =
    "JavaVersion.VERSION_$JAVA_TARGET_VERSION"

  @JvmStatic
  fun buildGradleSrc(
    namespace: String,
    minSdk: Int,
    language: Language,
    useKts: Boolean
  ): String {
    val assignment = if (useKts) " = " else " "
    val quote = if (useKts) "\"" else "'"

    val plugins = listOfNotNull(
      androidPlugin(useKts),
      kotlinPlugin(language, useKts)
    )
    .filter(String::isNotEmpty)
    .joinToString("\n    ")

    return """
plugins {
    $plugins
}

android {
    namespace${assignment}${quote}${namespace}${quote}
    compileSdk${assignment}${compileSdkVersion()}

    defaultConfig {
        minSdk${assignment}${minSdk}
    }

    ${buildTypesBlock(useKts)}
    ${optionsConfigBlock(language, useKts)}
}
    """.trimIndent()
  }

  private fun androidPlugin(useKts: Boolean): String {
    return if (useKts)
      """id("com.android.library")""".trim()
    else
      """id 'com.android.library'""".trim()
  }

  private fun kotlinPlugin(language: Language, useKts: Boolean): String {
    if (language != Language.KOTLIN) {
      return ""
    }

    return if (useKts)
      """id("org.jetbrains.kotlin.android")""".trim()
    else
      """id 'org.jetbrains.kotlin.android'""".trim()
  }

  private fun buildTypesBlock(useKts: Boolean): String {
    return if (useKts)
      """
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
      """.trim()
    else
      """
    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
      """.trim()
  }

  private fun optionsConfigBlock(language: Language, useKts: Boolean): String {
    val assignment = if (useKts) " = " else " "
    val quote = if (useKts) "\"" else "'"

    val compileOptionsBlock = """
    compileOptions {
        sourceCompatibility${assignment}${javaSourceVersion()}
        targetCompatibility${assignment}${javaTargetVersion()}
    }
    """.trim()

    return listOfNotNull(
      compileOptionsBlock,
      kotlinOptionsBlock(language, useKts)
    )
    .filter(String::isNotEmpty)
    .joinToString("\n    ")
  }

  private fun kotlinOptionsBlock(language: Language, useKts: Boolean): String {
    if (language != Language.KOTLIN) {
      return ""
    }

    val quote = if (useKts) "\"" else "'"

    return """
    kotlinOptions {
        jvmTarget = ${quote}${JAVA_TARGET_VERSION}${quote}
    }
    """.trim()
  }

  @JvmStatic
  fun gitIgnoreSrc(): String {
    return """
    /build
    """.trimIndent()
  }
}
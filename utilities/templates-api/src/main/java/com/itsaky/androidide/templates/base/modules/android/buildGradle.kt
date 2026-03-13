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

package com.itsaky.androidide.templates.base.modules.android

import com.itsaky.androidide.templates.Language.Kotlin
import com.itsaky.androidide.templates.ModuleType
import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.modules.dependencies

private const val COMPOSE_COMPILER_VERSION = "1.5.15"

fun AndroidModuleTemplateBuilder.buildGradleSrc(
  isComposeModule: Boolean,
  viewBindingEnabled: Boolean
): String {
  val assignment = if (data.useKts) " = " else " "
  val quote = if (data.useKts) "\"" else "'"

  val plugins = listOfNotNull(
    androidPlugin(),
    kotlinPlugin()
  )
  .filter(String::isNotEmpty)
  .joinToString("\n    ")

  return """
plugins {
    $plugins
}

android {
    namespace${assignment}${quote}${data.packageName}${quote}
    compileSdk${assignment}${data.versions.compileSdk.api}

    defaultConfig {
        applicationId${assignment}"${data.packageName}"
        minSdk${assignment}${data.versions.minSdk.api}
        targetSdk${assignment}${data.versions.targetSdk.api}
        versionCode${assignment}1
        versionName${assignment}"1.0"

        vectorDrawables {
            useSupportLibrary${assignment}true
        }
    }

    ${buildTypesBlock()}
    ${optionsConfigBlock(isComposeModule, viewBindingEnabled)}
}

${dependencies()}
  """.trimIndent()
}

private fun AndroidModuleTemplateBuilder.androidPlugin(): String {
  if (data.type != ModuleType.AndroidLibrary) {
    return if (data.useKts)
      """id("com.android.application")""".trim()
    else
      """id 'com.android.application'""".trim()
  }

  return if (data.useKts)
    """id("com.android.library")""".trim()
  else
    """id 'com.android.library'""".trim()
}

private fun AndroidModuleTemplateBuilder.kotlinPlugin(): String {
  if (data.language != Kotlin) {
    return ""
  }

  return if (data.useKts)
    """id("org.jetbrains.kotlin.android")""".trim()
  else
    """id 'org.jetbrains.kotlin.android'""".trim()
}

private fun AndroidModuleTemplateBuilder.buildTypesBlock(): String {
  return if (data.useKts)
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

private fun AndroidModuleTemplateBuilder.optionsConfigBlock(
  isComposeModule: Boolean,
  viewBindingEnabled: Boolean
): String {
  val assignment = if (data.useKts) " = " else " "
  val quote = if (data.useKts) "\"" else "'"

  val compileOptionsBlock = """
    compileOptions {
        sourceCompatibility${assignment}${data.versions.javaSource()}
        targetCompatibility${assignment}${data.versions.javaTarget()}
    }
  """.trim()

  val features = when {
    isComposeModule -> "compose${assignment}true"
    viewBindingEnabled -> "viewBinding${assignment}true"
    else -> ""
  }

  val buildFeaturesBlock = features.takeIf { it.isNotBlank() }?.let {
    """
    buildFeatures {
        $it
    }
    """.trim()
  }

  return listOfNotNull(
    compileOptionsBlock,
    kotlinOptionsBlock(),
    buildFeaturesBlock,
    composeConfigBlock(isComposeModule)
  )
  .filter(String::isNotEmpty)
  .joinToString("\n    ")
}

private fun AndroidModuleTemplateBuilder.kotlinOptionsBlock(): String {
  if (data.language != Kotlin) {
      return ""
  }

  val quote = if (data.useKts) "\"" else "'"

  return """
    kotlinOptions {
        jvmTarget = ${quote}${data.versions.javaTarget}${quote}
    }
  """.trim()
}

private fun AndroidModuleTemplateBuilder.composeConfigBlock(
  isComposeModule: Boolean
): String {
  val quote = if (data.useKts) "\"" else "'"

  return if (isComposeModule)
    """
    composeOptions {
        kotlinCompilerExtensionVersion = ${quote}$COMPOSE_COMPILER_VERSION${quote}
    }
    packagingOptions {
        resources {
            excludes += ${quote}/META-INF/{AL2.0,LGPL2.1}${quote}
        }
    }
    """.trim()
  else ""
}

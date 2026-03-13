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

package com.itsaky.androidide.templates.base.root

import com.itsaky.androidide.templates.Language
import com.itsaky.androidide.templates.base.ProjectTemplateBuilder

internal fun ProjectTemplateBuilder.buildGradleSrc(): String {
  val plugins = listOfNotNull(
    androidPlugin(),
    kotlinPlugin()
  )
  .filter(String::isNotEmpty)
  .joinToString("\n    ")

  return """
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    $plugins
}

${taskConfigBlock()}
  """.trimIndent()
}

private fun ProjectTemplateBuilder.androidPlugin(): String {
  return if (data.useKts)
    """
    id("com.android.application") version "${data.version.gradlePlugin}" apply false
    id("com.android.library") version "${data.version.gradlePlugin}" apply false
    """.trim()
  else
    """
    id 'com.android.application' version '${data.version.gradlePlugin}' apply false
    id 'com.android.library' version '${data.version.gradlePlugin}' apply false
    """.trim()
}

private fun ProjectTemplateBuilder.kotlinPlugin(): String {
  if (data.language != Language.Kotlin) {
    return ""
  }

  return if (data.useKts)
    """id("org.jetbrains.kotlin.android") version "${data.version.kotlin}" apply false""".trim()
  else
    """id 'org.jetbrains.kotlin.android' version '${data.version.kotlin}' apply false""".trim()
}

private fun ProjectTemplateBuilder.taskConfigBlock(): String {
  return if (data.useKts)
    """
    tasks.register<Delete>("clean") {
        delete(rootProject.layout.buildDirectory)
    }
    """.trimIndent()
  else
    """
    task clean(type: Delete) {
        delete rootProject.layout.buildDirectory
    }
    """.trimIndent()
}

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

package com.itsaky.androidide.models

import androidx.annotation.DrawableRes
import com.blankj.utilcode.util.ImageUtils
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.resources.R
import java.io.File
import kotlin.text.Regex
import kotlin.text.RegexOption

/**
 * Info about file extensions in the file tree view.
 *
 * @author Akash Yadav
 */
enum class FileExtension(val extension: String, @DrawableRes val icon: Int) {
  PROJECT_ROOT_DIRECTORY("", R.drawable.ic_folder_module),
  APPLICATION_MODULE_DIRECTORY("", R.drawable.ic_folder_android_module),
  LIBRARY_MODULE_DIRECTORY("", R.drawable.ic_folder_library_module),
  DYNAMIC_FEATURE_MODULE_DIRECTORY("", R.drawable.ic_folder_dynamic_feature_module),
  MODULE_DIRECTORY("", R.drawable.ic_folder_module),
  ANDROID_TEST_ROOT_DIRECTORY("", R.drawable.ic_folder_android_test_root),
  SOURCE_ROOT_DIRECTORY("", R.drawable.ic_folder_source_root),
  RESOURCES_ROOT_DIRECTORY("", R.drawable.ic_folder_resources_root),
  RESOURCES_SUB_DIRECTORY("", R.drawable.ic_folder_package),
  PACKAGE_DIRECTORY("", R.drawable.ic_folder_package),
  DIRECTORY("", R.drawable.ic_folder_any),

  TOML("toml", R.drawable.ic_file_type_toml),
  IGNORE("gitignore", R.drawable.ic_file_type_ignored),
  GRADLE("gradle", R.drawable.ic_file_type_gradle),
  KTS("kts", R.drawable.ic_file_type_gradle_kts),
  PROPERTIES("properties", R.drawable.ic_file_type_properties),
  LOG("log", R.drawable.ic_file_type_changed_file),
  MARKDOWN("md", R.drawable.ic_file_type_markdown),
  XML("xml", R.drawable.ic_file_type_xml),
  HTML("html", R.drawable.ic_file_type_html),
  CSS("css", R.drawable.ic_file_type_css),
  JS("js", R.drawable.ic_file_type_java_script),
  JSON("json", R.drawable.ic_file_type_json),
  C("c", R.drawable.ic_file_type_c),
  APK("apk", R.drawable.ic_file_type_apk),

  CLASS("", R.drawable.ic_class_anonymous),
  CPP("", R.drawable.ic_file_type_cpp),
  H("", R.drawable.ic_file_type_h),
  IMAGE("", R.drawable.ic_file_type_image),
  ARCHIVE("", R.drawable.ic_file_type_archive),
  FONT("", R.drawable.ic_file_type_font),
  MANIFEST("", R.drawable.ic_file_type_manifest),
  MAVEN("", R.drawable.ic_file_type_maven),
  TEXT("", R.drawable.ic_file_type_text),
  SHELL_SCRIPT("", R.drawable.ic_file_type_shell),
  UNKNOWN("", R.drawable.ic_file_type_any);

  data class FileTypeResult(val type: FileExtension, @DrawableRes val icon: Int)

  /** Factory class for getting [FileExtension] instances. */
  class Factory {
    companion object {

      /**
       * Get [FileExtension] for the given file.
       *
       * @param file The file or directory to detect the extension for.
       * @return The determined [FileExtension].
       */
      @JvmStatic
      fun forFile(file: File?): FileTypeResult {
        val type = when {
          file == null -> UNKNOWN
          file.isDirectory -> detectDirectoryType(file)
          detectFileType(file) != UNKNOWN -> detectFileType(file)
          file.extension.lowercase() in setOf("kt", "java") -> CLASS
          else -> forExtension(file.extension)
        }

        val icon = when (type) {
          CLASS -> detectClassIcon(file!!)
          else -> type.icon
        }

        return FileTypeResult(type, icon)
      }

      /**
       * Get [FileExtension] for the given extension string.
       *
       * @param extension The file extension string.
       * @return The determined [FileExtension], or UNKNOWN if not matched.
       */
      @JvmStatic
      fun forExtension(extension: String?): FileExtension {
        if (extension.isNullOrEmpty()) return UNKNOWN
        // Iterate through all enum entries to find a match by extension string
        for (value in entries) {
          if (value.extension == extension) return value
        }
        return UNKNOWN
      }

      // Enum defining the different types of Android Gradle modules
      enum class ModuleType {
        APPLICATION,
        LIBRARY,
        DYNAMIC_FEATURE,
        OTHER,
        GROUP,
        NONE
      }

      /**
       * Detects the type of a Gradle module (Application, Library, etc.) by reading build files.
       *
       * @param file The directory (module) to check.
       * @return The determined [ModuleType].
       */
      private fun detectModuleType(file: File): ModuleType {
        val pluginTypeMap = mapOf(
          "com.android.application" to ModuleType.APPLICATION,
          "com.android.library" to ModuleType.LIBRARY,
          "com.android.dynamic-feature" to ModuleType.DYNAMIC_FEATURE
        )

        val rootDir = File(IProjectManager.getInstance().projectDirPath)
        if (file.canonicalPath == rootDir.canonicalPath) {
          return ModuleType.NONE
        }

        val gradleFile = getGradleFile(file) ?: return detectIfGroup(file)
        val content = gradleFile.readText()

        // Remove block comments
        val noBlockComments = content.replace(
          Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")

        // Remove single line comments
        val cleanContent = noBlockComments.replace(Regex("//.*"), "")

        var hasPluginDeclaration = false

        // Detect apply plugin:
        val applyRegex = Regex("""apply\s+plugin\s*:\s*['"]([^'"]+)['"]""")
        applyRegex.findAll(cleanContent).forEach { match ->
          hasPluginDeclaration = true
          val pluginId = match.groupValues[1]
          pluginTypeMap[pluginId]?.let { return it }
        }

        // Plugins block only
        val pluginsBlockRegex = Regex("plugins\\s*\\{(.*?)\\}", RegexOption.DOT_MATCHES_ALL)
        val pluginsBlock = pluginsBlockRegex.find(cleanContent)?.groupValues?.get(1)

        pluginsBlock?.let { block ->
          hasPluginDeclaration = true

          // Detect id("...")
          val idRegex = Regex("""id\s*\(?["']([^"']+)["']\)?""")
          for (match in idRegex.findAll(block)) {
            val pluginId = match.groupValues[1]
            pluginTypeMap[pluginId]?.let { return it }
          }

          // Detect alias(libs.plugins.xxx)
          val aliasRegex = Regex("""alias\s*\(\s*libs\.plugins\.([^)]+)\s*\)""")
          for (match in aliasRegex.findAll(block)) {
            val aliasName = match.groupValues[1]

            when {
              aliasName.contains("application") -> ModuleType.APPLICATION
              aliasName.contains("library") -> ModuleType.LIBRARY
              aliasName.contains("dynamic") ||
              aliasName.contains("dynamic.feature") -> ModuleType.DYNAMIC_FEATURE
            }
          }
        }

        if (hasPluginDeclaration) return ModuleType.OTHER

        return detectIfGroup(file)
      }

      private fun detectIfGroup(file: File): ModuleType {
        val isGroup = file.listFiles()?.any { child ->
          child.isDirectory && getGradleFile(child) != null
        } == true

        return if (isGroup) ModuleType.GROUP else ModuleType.NONE
      }

      private fun getGradleFile(file: File): File? {
        return listOf("build.gradle", "build.gradle.kts")
          .map { File(file, it) }
          .firstOrNull { it.exists() }
      }

      /**
       * Checks whether this file has one of the given extensions.
       *
       * The file extension is compared in a normalized form, so the letter case
       * of the actual file extension does not matter.
       *
       * Example:
       * - file.PNG, file.png → match "png"
       *
       * @param exts List of allowed file extensions (without dot).
       * @return `true` if the file has a matching extension, `false` otherwise.
       */
      private fun File.hasExtension(vararg exts: String): Boolean {
        val ext = this.extension.lowercase()
        return ext.isNotEmpty() && ext in exts
      }

      /**
       * Checks whether this file name matches the given name exactly.
       *
       * This comparison requires the file name to be written exactly as specified.
       *
       * Example:
       * - "AndroidManifest.xml" → match
       * - "androidmanifest.xml" → no match
       *
       * @param name The exact file name to match.
       * @return `true` if the file name matches exactly, `false` otherwise.
       */
      private fun File.isNamedExactly(name: String): Boolean {
        return this.name == name
      }

      /**
       * Checks whether this file has no extension and its name matches
       * one of the given names.
       *
       * This is intended for executable or script files that commonly
       * do not use file extensions.
       *
       * Example:
       * - gradlew, GradleW → match
       * - gradlew.sh → no match
       *
       * @param names List of allowed file names.
       * @return `true` if the file has no extension and the name matches, `false` otherwise.
       */
      private fun File.nameEqualsIgnoreCase(vararg names: String): Boolean {
        if (this.extension.isNotEmpty()) return false
        return names.any {
          it.equals(this.name, ignoreCase = true)
        }
      }

      private fun srcMain(file: File): Boolean {
        val parent = file.parentFile ?: return false

        val dirNames = setOf("main", "androidTest", "test")
        return file.name in dirNames && parent.name == "src"
      }

      private fun mainSource(file: File): Boolean {
        var current: File? = file.parentFile

        while (current != null) {
          if (current.name in setOf("java+kotlin", "java", "kotlin")) {
            return current.parentFile?.let { srcMain(it) } == true
          }
          current = current.parentFile
        }
        return false
      }

      private fun subRes(file: File): Boolean {
        val parent = file.parentFile ?: return false

        return parent.name == "res" &&
          parent.parentFile?.let { srcMain(it) } == true
      }

      /**
       * Determines the specific type of a directory for icon display.
       *
       * @param file The directory to check.
       * @return The specific [FileExtension] for the directory type.
       */
      private fun detectDirectoryType(file: File): FileExtension {
        val moduleType = detectModuleType(file)
        if (moduleType != ModuleType.NONE) {
          return when (moduleType) {
            ModuleType.APPLICATION -> APPLICATION_MODULE_DIRECTORY
            ModuleType.LIBRARY -> LIBRARY_MODULE_DIRECTORY
            ModuleType.DYNAMIC_FEATURE -> DYNAMIC_FEATURE_MODULE_DIRECTORY
            ModuleType.OTHER -> MODULE_DIRECTORY
            ModuleType.GROUP -> MODULE_DIRECTORY
            else -> DIRECTORY
          }
        }

        val rootDir = File(IProjectManager.getInstance().projectDirPath)
        if (file.canonicalPath == rootDir.canonicalPath) {
          return PROJECT_ROOT_DIRECTORY
        }

        val name = file.name
        val parent = file.parentFile?.name

        if (name == "main" && parent == "src") {
          val moduleDir = file.parentFile?.parentFile
          if (moduleDir != null) {
            return when (detectModuleType(moduleDir)) {
              ModuleType.APPLICATION -> APPLICATION_MODULE_DIRECTORY
              ModuleType.LIBRARY -> LIBRARY_MODULE_DIRECTORY
              ModuleType.DYNAMIC_FEATURE -> DYNAMIC_FEATURE_MODULE_DIRECTORY
              else -> MODULE_DIRECTORY
            }
          }
        }

        val testDirs = setOf("androidTest", "test")
        if (name in testDirs && parent == "src") {
          return ANDROID_TEST_ROOT_DIRECTORY
        }

        val sourceDirs = setOf("java+kotlin", "java", "kotlin", "manifests")
        if (file.name in sourceDirs && file.parentFile?.let { srcMain(it) } == true) {
          return SOURCE_ROOT_DIRECTORY
        }

        val packageDirs = file.name.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*$"))
        if (packageDirs && mainSource(file)) {
          return PACKAGE_DIRECTORY
        }

        val resourcesDirs = setOf("assets", "res")
        if (file.name in resourcesDirs && file.parentFile?.let { srcMain(it) } == true) {
          return RESOURCES_ROOT_DIRECTORY
        }

        if (subRes(file)) {
          return RESOURCES_SUB_DIRECTORY
        }

        return DIRECTORY
      }

      /**
       * Detects file type based on content, special name, or specific extensions.
       *
       * @param file The file to check.
       * @return The specific [FileExtension] for the file type.
       */
      private fun detectFileType(file: File): FileExtension {
        if (ImageUtils.isImage(file)) {
          return IMAGE
        }

        if (file.hasExtension("jar", "aar", "zip", "tar", "gz", "7z", "rar")) {
          return ARCHIVE
        }

        if (file.hasExtension("ttf", "otf", "woff", "woff2", "eot")) {
          return FONT
        }

        if (file.hasExtension("cpp", "cc", "cxx")) {
          return CPP
        }

        if (file.hasExtension("h", "hpp", "hh", "hxx")) {
          return H
        }

        if (file.isNamedExactly("AndroidManifest.xml")) {
          return MANIFEST
        }

        if (file.isNamedExactly("pom.xml")) {
          return MAVEN
        }

        if (file.hasExtension("txt", "pro", "bat")) {
          return TEXT
        }

        if (file.hasExtension("sh", "bash", "zsh", "ksh", "csh", "fish") ||
            file.nameEqualsIgnoreCase(
              "gradlew", "bashrc", "bash_profile", "profile",
              "zshrc", "zprofile", "rc", "run", "start", "shell"
            )
        ) {
          return SHELL_SCRIPT
        }

        return UNKNOWN
      }

      /**
       * Detects the appropriate drawable resource ID for a Kotlin or Java class file icon
       * based on its content (e.g., whether it defines an abstract class, interface, enum, etc.).
       *
       * This method reads the file's content and performs regex matching to determine
       * the structural type of the class/file.
       *
       * @param file The Kotlin (.kt) or Java (.java) file to analyze.
       * @return The [DrawableRes] ID for the detected class type icon.
       */
      @DrawableRes
      private fun detectClassIcon(file: File): Int {
        val className = file.nameWithoutExtension
        val isKotlin = file.extension.lowercase() == "kt"

        val text = try {
          file.readText()
        } catch (_: Exception) {
          return if (isKotlin)
            R.drawable.ic_file_type_kotlin else R.drawable.ic_file_type_java
        }

        val cleaned = text
          .replace(Regex("/\\*([\\s\\S]*?)\\*/"), "")
          .replace(Regex("(?m)^\\s*//.*$"), "")

        fun match(regex: String) =
          Regex(regex, setOf(RegexOption.MULTILINE)).containsMatchIn(cleaned)

        val isClass = match("""class\s+$className\b""")
        val isInterface = match("""interface\s+$className\b""")
        val isEnum = match("""enum\s+class\s+$className\b|enum\s+$className\b""")
        val isAnnotation = match("""annotation\s+class\s+$className\b|@interface\s+$className\b""")
        val isAbstract = match("""abstract\s+class\s+$className\b""")

        return when {
          isClass -> if (isKotlin)
            R.drawable.ic_class_class_kotlin else R.drawable.ic_class_class

          isInterface -> if (isKotlin)
            R.drawable.ic_class_interface_kotlin else R.drawable.ic_class_interface

          isEnum -> if (isKotlin)
            R.drawable.ic_class_enum_kotlin else R.drawable.ic_class_enum

          isAnnotation -> if (isKotlin)
            R.drawable.ic_class_annotation_kotlin else R.drawable.ic_class_annotation

          isAbstract -> if (isKotlin)
            R.drawable.ic_class_abstract_kotlin else R.drawable.ic_class_abstract

          else -> if (isKotlin)
            R.drawable.ic_file_type_kotlin else R.drawable.ic_file_type_java
        }
      }
    }
  }
}

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

package com.itsaky.androidide.actions.filetree

import android.content.Context
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.core.widget.doAfterTextChanged
import com.blankj.utilcode.util.FileIOUtils
import com.google.android.material.textfield.TextInputLayout
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.requireFile
import com.itsaky.androidide.adapters.viewholders.FileTreeViewHolder
import com.itsaky.androidide.databinding.LayoutCreateModuleBinding
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.utils.AndroidModuleTemplateBuilder.Language
import com.itsaky.androidide.utils.AndroidUtils
import com.itsaky.androidide.utils.DialogUtils
import com.itsaky.androidide.utils.ProjectWriter
import com.itsaky.androidide.utils.Sdk
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.flashSuccess
import com.unnamed.b.atv.model.TreeNode
import java.io.File

/**
 * File tree action to create a new module.
 *
 * @author M Cikadu-Dev
 */
class NewModuleAction(context: Context, override val order: Int) :
  BaseDirNodeAction(
    context = context,
    labelRes = R.string.new_module,
    iconRes = R.drawable.ic_android
  ) {

  private var selectedLanguage: Language = Language.JAVA
  private var selectedSdk: Sdk = Sdk.Nougat

  private var targetModules: List<ModuleItem> = emptyList()

  data class ModuleItem(
    val name: String,
    var checked: Boolean = false
  )

  override val id: String = "ide.editor.fileTree.newModule"

  override suspend fun execAction(data: ActionData) {
    val context = data.requireActivity()
    val node = data.getTreeNode()
    val parent = data.requireFile()

    val projectDir = File(IProjectManager.getInstance().projectDirPath)
    if (isInvalidModuleLocation(parent, projectDir)) {
      flashError(R.string.msg_invalid_module_location)
      return
    }

    showCreateModuleDialog(context, node, parent)
  }

  private fun showCreateModuleDialog(
    context: Context,
    node: TreeNode?,
    parent: File
  ) {
    val binding = LayoutCreateModuleBinding.inflate(LayoutInflater.from(context))
    val builder = DialogUtils.newMaterialDialogBuilder(context)

    val moduleName = binding.moduleName
    val packageName = binding.packageName

    moduleName.setText("mylibrary")
    packageName.setText("com.example.mylibrary")

    moduleName.setRawInputType(
      InputType.TYPE_CLASS_TEXT or
      InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    )
    moduleName.imeOptions = EditorInfo.IME_ACTION_NEXT
    moduleName.setOnEditorActionListener { _, actionId, _ ->
      if (actionId == EditorInfo.IME_ACTION_NEXT) {
        packageName.requestFocus()

        packageName.setSelection(
          packageName.text?.length ?: 0
        )

        true
      } else {
        false
      }
    }

    packageName.setRawInputType(
      InputType.TYPE_CLASS_TEXT or
      InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    )
    packageName.imeOptions = EditorInfo.IME_ACTION_DONE
    packageName.setOnEditorActionListener { v, actionId, _ ->
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        v.clearFocus()

        val imm = context.getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(v.windowToken, 0)

        true
      } else {
        false
      }
    }

    moduleName.doAfterTextChanged { text ->
      val moduleName = text?.toString()?.trim() ?: return@doAfterTextChanged

      val basePackage = "com.example"

      val generated = if (moduleName.isNotBlank()) {
        AndroidUtils.projectNameToPackageName(moduleName, "$basePackage.")
      } else {
        "$basePackage."
      }

      packageName.setText(generated)

      isValidateName(binding)
    }

    packageName.doAfterTextChanged {
      isValidateName(binding)
    }

    targetModules = getDefaultTargetModules()

    binding.btnSelectModules.setOnClickListener {
      val modules = getExistingModules(
        File(IProjectManager.getInstance().projectDirPath)
      )

      val updatedModules = modules.map { module ->
        module.copy(
          checked = targetModules.any { it.name == module.name }
        )
      }

      showModulePickerDialog(context, updatedModules) { selected ->
        targetModules = selected
      }
    }

    setupLanguage(context, binding)
    setupSdk(context, binding)

    builder.setTitle(R.string.title_create_new_module)
    builder.setView(binding.root)
    builder.setPositiveButton(R.string.text_create) { dialog, _ ->
      dialog.dismiss()
      createModule(context, node, parent, binding)
    }
    builder.setNegativeButton(android.R.string.cancel, null)
    builder.setCancelable(false)
    builder.create().show()
  }

  private fun isValidateName(binding: LayoutCreateModuleBinding): Boolean {
    var valid = true

    val moduleNameLayout = binding.moduleNameLayout
    val packageNameLayout = binding.packageNameLayout

    val moduleName = binding.moduleName.text.toString().trim()
    val packageName = binding.packageName.text.toString().trim()

    val msgEmptyValue = binding.root.context.getString(
      R.string.msg_value_empty
    )

    if (moduleName.isBlank()) {
      moduleNameLayout.error = msgEmptyValue
      valid = false
    } else {
      moduleNameLayout.error = null
    }

    val packageError = AndroidUtils.validatePackageName(packageName)
    packageNameLayout.error =
      when {
        packageName.isBlank() -> msgEmptyValue
        else -> packageError
      }

    if (packageNameLayout.error != null) valid = false

    return valid
  }

  private fun showModulePickerDialog(
    context: Context,
    modules: List<ModuleItem>,
    onResult: (List<ModuleItem>) -> Unit
  ) {
    val names = modules.map { it.name }.toTypedArray()
    val checked = modules.map { it.checked }.toBooleanArray()

    val builder = DialogUtils.newMaterialDialogBuilder(context)

    builder.setTitle(R.string.msg_add_to_modules)
    builder.setMultiChoiceItems(names, checked) { _, which, isChecked ->
      modules[which].checked = isChecked
    }
    builder.setPositiveButton(android.R.string.ok) { _, _ ->
      onResult(modules.filter { it.checked })
    }
    builder.setNegativeButton(android.R.string.cancel, null)
    builder.create().show()
  }

  private fun getDefaultTargetModules(): List<ModuleItem> {
    return listOf(ModuleItem("app", true))
  }

  private fun getExistingModules(moduleDir: File): List<ModuleItem> {
    val modules = mutableListOf<ModuleItem>()

    moduleDir.walkTopDown()
      .onEnter { dir ->
        if (dir == moduleDir) return@onEnter true
        if (dir.name.startsWith(".")) return@onEnter false
        if (dir.name == "build") return@onEnter false
        if (dir.name == "src") return@onEnter false
        true
    }
    .filter { dir ->
      dir.isDirectory &&
      dir != moduleDir && (
        File(dir, "build.gradle").exists() ||
        File(dir, "build.gradle.kts").exists()
      )
    }
    .forEach { dir ->
      val moduleName = dir.relativeTo(moduleDir)
        .path
        .replace(File.separatorChar, ':')

      modules.add(
        ModuleItem(
          name = moduleName,
          checked = moduleName == "app"
        )
      )
    }

    return modules.sortedWith(
      compareBy<ModuleItem> { it.name != "app" }
        .thenBy { it.name.count { c -> c == ':' } }
        .thenBy { it.name.lowercase() }
    )
  }

  private fun setupLanguage(context: Context, binding: LayoutCreateModuleBinding) {
    val languageInput = binding.languageProject
    val languageLayout = binding.languageLayout

    val languages = Language.values()

    val languageAdapter = ArrayAdapter(
      context,
      androidx.appcompat.R.layout.support_simple_spinner_dropdown_item,
      languages.map { it.name.lowercase().replaceFirstChar(Char::uppercase) }
    )

    languageInput.setAdapter(languageAdapter)

    selectedLanguage = Language.JAVA
    languageInput.setText("Java", false)

    setupDropdownBehavior(
      context,
      input = languageInput,
      layout = languageLayout,
      onItemSelected = { position ->
        selectedLanguage = languages[position]
        val icon = when (selectedLanguage) {
          Language.JAVA -> R.drawable.ic_language_java
          Language.KOTLIN -> R.drawable.ic_language_kotlin
        }
        languageLayout.setStartIconDrawable(icon)
      }
    )
  }

  private fun setupSdk(context: Context, binding: LayoutCreateModuleBinding) {
    val sdkInput = binding.minSdk
    val sdkLayout = binding.minSdkLayout

    val sdks = Sdk.values()

    val sdkAdapter = ArrayAdapter(
      context,
      androidx.appcompat.R.layout.support_simple_spinner_dropdown_item,
      sdks.map { it.displayName() }
    )

    sdkInput.setAdapter(sdkAdapter)

    selectedSdk = Sdk.Nougat
    sdkInput.setText(Sdk.Nougat.displayName(), false)

    val density = context.resources.displayMetrics.density
    sdkInput.dropDownHeight = (200 * density).toInt()

    setupDropdownBehavior(
      context,
      input = sdkInput,
      layout = sdkLayout,
      onItemSelected = { position ->
        selectedSdk = sdks[position]
      }
    )
  }

  private fun setupDropdownBehavior(
    context: Context,
    input: AutoCompleteTextView,
    layout: TextInputLayout,
    onItemSelected: (position: Int) -> Unit
  ) {
    layout.setEndIconOnClickListener {
      if (!input.isPopupShowing) {
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(input.windowToken, 0)
        input.showDropDown()
      }
    }

    input.setOnClickListener {
      val imm = context.getSystemService(InputMethodManager::class.java)
      imm?.hideSoftInputFromWindow(input.windowToken, 0)
    }

    input.setOnItemClickListener { _, _, position, _ ->
      onItemSelected(position)
    }

    input.setOnDismissListener {
      input.post {
        input.clearFocus()
        input.rootView.clearFocus()
      }
    }
  }

  private fun addDependencyToModule(
    gradleFile: File,
    dependencyModule: String,
    useKts: Boolean
  ) {
    val content = gradleFile.readText()

    val dependencyLine = if (useKts) {
      """implementation(project(":$dependencyModule"))"""
    } else {
      """implementation project(':$dependencyModule')"""
    }

    val escapedModulePath = Regex.escape(":$dependencyModule")

    val dependencyRegex = if (useKts) {
      Regex(
        """^\s*\w+\s*\(\s*project\(\s*["']$escapedModulePath["']\s*\)\s*\)""",
        RegexOption.MULTILINE
      )
    } else {
      Regex(
        """^\s*\w+\s+project\s+['"]$escapedModulePath['"]""",
        RegexOption.MULTILINE
      )
    }

    if (dependencyRegex.containsMatchIn(content)) return

    val regex = Regex(
      "dependencies\\s*\\{([\\s\\S]*?)\\}",
      RegexOption.MULTILINE
    )

    val newContent = if (regex.containsMatchIn(content)) {
      val match = regex.find(content)!!
      val blockStart = match.range.first
      val blockEnd = match.range.last

      val blockBody = match.groupValues[1]
        .removePrefix("\n")
        .trimEnd()

      buildString {
        append(content.substring(0, blockStart))
        append("dependencies {")
        appendLine()
        if (blockBody.isNotBlank()) {
          append(blockBody)
          appendLine()
        }
        append("    $dependencyLine")
        appendLine()
        append("}")
        append(content.substring(blockEnd + 1))
      }
    } else {
      buildString {
        append(content.trimEnd())
        appendLine()
        appendLine()
        appendLine("dependencies {")
        appendLine("    $dependencyLine")
        append("}")
      }
    }

    FileIOUtils.writeFileFromString(gradleFile, newContent)
  }

  private fun findGradleModule(
    projectDir: File,
    moduleName: String
  ): Pair<File, Boolean> {
    val moduleDir = File(projectDir, moduleName)

    val kts = File(moduleDir, "build.gradle.kts")
    if (kts.exists()) return kts to true

    val groovy = File(moduleDir, "build.gradle")
    if (groovy.exists()) return groovy to false

    throw IllegalStateException("No gradle file for module $moduleName")
  }

  private fun includeModuleInSettings(projectDir: File, moduleName: String) {
    val result = findSettingsGradle(projectDir) ?: return
    val (file, isKts) = result

    val content = file.readText()

    val includeLine = if (isKts) {
      """include(":$moduleName")"""
    } else {
      """include ':$moduleName'"""
    }

    val escapedModulePath = Regex.escape(":$moduleName")

    val includeRegex = Regex(
      """^\s*include\s*\(?\s*["']$escapedModulePath["']\s*\)?""",
      RegexOption.MULTILINE
    )

    if (includeRegex.containsMatchIn(content)) return

    val newContent = buildString {
      append(content.trimEnd())
      appendLine()
      append(includeLine)
    }

    FileIOUtils.writeFileFromString(file, newContent)
  }

  private fun findSettingsGradle(projectDir: File): Pair<File, Boolean>? {
    val kts = File(projectDir, "settings.gradle.kts")
    if (kts.exists()) return kts to true

    val groovy = File(projectDir, "settings.gradle")
    if (groovy.exists()) return groovy to false

    return null
  }

  private fun isInvalidModuleLocation(moduleDir: File, projectDir: File): Boolean {
    if (!moduleDir.canonicalPath.startsWith(projectDir.canonicalPath)) {
      return true
    }

    var currentDir: File? = moduleDir
    while (currentDir != null && currentDir != projectDir) {
      if (currentDir.name.startsWith(".")) return true

      val restrictedNames = setOf("build", "gradle")
      if (currentDir.name in restrictedNames) return true

      if (isGradleModule(currentDir)) return true

      currentDir = currentDir.parentFile
    }

    return false
  }

  private fun isGradleModule(moduleDir: File): Boolean {
    return moduleDir.isDirectory && (
      File(moduleDir, "build.gradle").exists() ||
      File(moduleDir, "build.gradle.kts").exists()
    )
  }

  private fun createModule(
    context: Context,
    node: TreeNode?,
    projectDir: File,
    binding: LayoutCreateModuleBinding
  ) {
    val moduleName = binding.moduleName.text.toString().trim()
    val namespace = binding.packageName.text.toString().trim()
    val minSdk = selectedSdk.api
    val language = selectedLanguage
    val useKts = binding.useKts.isChecked

    if (!isValidateName(binding)) {
      flashError(R.string.msg_invalid_project_details)
      return
    }

    if (moduleName.isBlank() || namespace.isBlank()) {
      flashError(R.string.msg_invalid_project_details)
      return
    }

    val moduleDir = File(projectDir, moduleName)
    if (moduleDir.exists()) {
      flashError(R.string.msg_module_exists)
      return
    }

    createModuleStructure(
      context,
      node,
      moduleDir,
      moduleName,
      namespace,
      minSdk,
      language,
      useKts
    )

    targetModules = getDefaultTargetModules()
  }

  private fun createModuleStructure(
    context: Context,
    node: TreeNode?,
    moduleDir: File,
    moduleName: String,
    namespace: String,
    minSdk: Int,
    language: Language,
    useKts: Boolean
  ) {
    val srcMain = File(moduleDir, "src/main")
    val sourceDir = File(
      srcMain,
      "java/" + namespace.replace('.', '/')
    )
    val resDir = File(srcMain, "res")

    sourceDir.mkdirs()
    resDir.mkdirs()

    val gradleFile = if (useKts) "build.gradle.kts" else "build.gradle"
      FileIOUtils.writeFileFromString(
      File(moduleDir, gradleFile),
      ProjectWriter.createBuildGradleFile(
        namespace,
        minSdk,
        language,
        useKts
      )
    )

    FileIOUtils.writeFileFromString(
      File(moduleDir, "proguard-rules.pro"),
      ProjectWriter.createProguardRulesFile()
    )

    FileIOUtils.writeFileFromString(
      File(moduleDir, ".gitignore"),
      ProjectWriter.createGitIgnoreFile()
    )

    FileIOUtils.writeFileFromString(
      File(srcMain, "AndroidManifest.xml"),
      ProjectWriter.createManifestFile()
    )

    val projectDir = File(IProjectManager.getInstance().projectDirPath)

    val relativePath = moduleDir
      .relativeTo(projectDir)
      .path
      .replace(File.separator, ":")

    includeModuleInSettings(projectDir, relativePath)

    for (module in targetModules) {
      try {
        val (gradle, isKts) = findGradleModule(projectDir, module.name)
        addDependencyToModule(
          gradleFile = gradle,
          dependencyModule = relativePath,
          useKts = isKts
        )
      } catch (e: Exception) {
        Log.w("NewModuleAction", "Failed to add dependency to ${module.name}", e)
      }
    }

    flashSuccess(R.string.msg_module_created)
    if (node != null) {
      val newNode = TreeNode(moduleDir)
      newNode.viewHolder = FileTreeViewHolder(context)
      node.addChild(newNode)
      requestExpandNode(node)
    } else {
      requestFileListing()
    }
  }
}
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
import android.view.LayoutInflater
import androidx.core.view.isVisible
import com.blankj.utilcode.util.FileIOUtils
import com.google.android.material.button.MaterialButton
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.requireFile
import com.itsaky.androidide.adapters.viewholders.FileTreeViewHolder
import com.itsaky.androidide.databinding.LayoutCreateSourceFileBinding
import com.itsaky.androidide.eventbus.events.file.FileCreationEvent
import com.itsaky.androidide.preferences.databinding.LayoutDialogTextInputBinding
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.utils.DialogUtils
import com.itsaky.androidide.utils.Environment
import com.itsaky.androidide.utils.ProjectWriter
import com.itsaky.androidide.utils.SingleTextWatcher
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.flashSuccess
import com.unnamed.b.atv.model.TreeNode
import jdkx.lang.model.SourceVersion
import org.greenrobot.eventbus.EventBus
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Objects
import java.util.regex.Pattern

/**
 * File tree action to create a new file.
 *
 * @author Akash Yadav
 */
class NewFileAction(context: Context, override val order: Int) :
  BaseDirNodeAction(
    context = context,
    labelRes = R.string.new_file,
    iconRes = R.drawable.ic_add_file
  ) {

  override val id: String = "ide.editor.fileTree.newFile"

  companion object {

    const val SOURCE_PATH_REGEX = "/.*/src/.*?/(java|kotlin|java\\+kotlin)"
    const val RES_PATH_REGEX = "/.*/src/.*/res"
    const val DRAWABLE_RES_PATH_REGEX = "/.*/src/.*/res/drawable"
    const val LAYOUT_RES_PATH_REGEX = "/.*/src/.*/res/layout"
    const val MENU_RES_PATH_REGEX = "/.*/src/.*/res/menu"

    private val log = LoggerFactory.getLogger(NewFileAction::class.java)
  }

  override suspend fun execAction(data: ActionData) {
    val context = data.requireActivity()
    val node = data.getTreeNode()
    val file = data.requireFile()

    try {
      createNewFile(context, node, file, false)
    } catch (e: Exception) {
      log.error("Failed to create new file", e)
      flashError(e.cause?.message ?: e.message)
    }
  }

  private fun createNewFile(
    context: Context,
    node: TreeNode?,
    file: File,
    forceUnknownType: Boolean
  ) {
    if (forceUnknownType) {
      createNewEmptyFile(context, node, file)
      return
    }

    val projectDir = IProjectManager.getInstance().projectDirPath
    Objects.requireNonNull(projectDir)

    val isSource = 
      Pattern.compile(Pattern.quote(projectDir) + SOURCE_PATH_REGEX)
        .matcher(file.absolutePath)
        .find()
    val isRes =
      Pattern.compile(Pattern.quote(projectDir) + RES_PATH_REGEX)
        .matcher(file.absolutePath)
        .find()
    val isDrawableRes =
      Pattern.compile(Pattern.quote(projectDir) + DRAWABLE_RES_PATH_REGEX)
        .matcher(file.absolutePath)
        .find()
    val isLayoutRes =
      Pattern.compile(Pattern.quote(projectDir) + LAYOUT_RES_PATH_REGEX)
        .matcher(file.absolutePath)
        .find()
    val isMenuRes =
      Pattern.compile(Pattern.quote(projectDir) + MENU_RES_PATH_REGEX)
        .matcher(file.absolutePath)
        .find()

    if (isSource) {
      createSourceFile(context, node, file)
      return
    }

    if (isRes && file.name == "res") {
      createNewResource(context, node, file)
      return
    }

    if (isDrawableRes && file.name == "drawable") {
      createDrawableRes(context, node, file)
      return
    }

    if (isLayoutRes && file.name == "layout") {
      createLayoutRes(context, node, file)
      return
    }

    if (isMenuRes && file.name == "menu") {
      createMenuRes(context, node, file)
      return
    }

    createNewEmptyFile(context, node, file)
  }

  private fun createSourceFile(context: Context, node: TreeNode?, file: File) {
    val binding = LayoutCreateSourceFileBinding.inflate(LayoutInflater.from(context))
    val builder = DialogUtils.newMaterialDialogBuilder(context)

    val iconMapping = mapOf(
      binding.typeClass.id to Pair(R.drawable.ic_class_class,
        R.drawable.ic_class_class_kotlin),
      binding.typeActivity.id to Pair(R.drawable.ic_class_class,
        R.drawable.ic_class_class_kotlin),
      binding.typeInterface.id to Pair(R.drawable.ic_class_interface,
        R.drawable.ic_class_interface_kotlin),
      binding.typeEnum.id to Pair(R.drawable.ic_class_enum,
        R.drawable.ic_class_enum_kotlin)
    )

    val updateIcons = { checkedId: Int ->
      val isFileTypeSelected = checkedId == binding.fileTypeKotlin.id

      for ((buttonId, iconPair) in iconMapping) {
        val button = binding.root.findViewById<MaterialButton>(buttonId)
        val iconRes = if (isFileTypeSelected) iconPair.second else iconPair.first
        button?.setIconResource(iconRes)
      }
    }

    binding.fileTypeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
      if (isChecked) {
        updateIcons(checkedId)
      }
    }

    binding.typeGroup.addOnButtonCheckedListener { _, _, _ ->
      binding.createLayout.isVisible = binding.typeGroup.checkedButtonId == binding.typeActivity.id
    }

    val defaultCheckedId = binding.fileTypeGroup.checkedButtonId 
    if (defaultCheckedId != -1) {
      updateIcons(defaultCheckedId)
    }

    binding.name.editText?.addTextChangedListener(
      object : SingleTextWatcher() {
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
          if (isValidClassName(s)) {
            binding.name.isErrorEnabled = true
            binding.name.error = context.getString(R.string.msg_invalid_name)
          } else {
            binding.name.isErrorEnabled = false
          }
        }
      }
    )
    builder.setView(binding.root)
    builder.setTitle(R.string.new_class_file)
    builder.setPositiveButton(R.string.text_create) { dialogInterface, _ ->
      dialogInterface.dismiss()
      try {
        doCreateSourceFile(context, node, file, binding)
      } catch (e: Exception) {
        log.error("Failed to create Class file", e)
        flashError(e.cause?.message ?: e.message)
      }
    }
    builder.setNegativeButton(android.R.string.cancel, null)
    builder.setCancelable(false)
    builder.create().show()
  }

  private fun doCreateSourceFile(
    context: Context,
    node: TreeNode?,
    file: File,
    binding: LayoutCreateSourceFileBinding
  ) {
    if (binding.name.isErrorEnabled) {
      flashError(R.string.msg_invalid_name)
      return
    }

    val name: String = binding.name.editText!!.text.toString().trim()
    if (name.isBlank()) {
      flashError(R.string.msg_invalid_name)
      return
    }

    val isKotlin = binding.fileTypeGroup.checkedButtonId == binding.fileTypeKotlin.id

    val autoLayout =
      binding.typeGroup.checkedButtonId == binding.typeActivity.id &&
          binding.createLayout.isChecked

    val packageName = ProjectWriter.getPackageName(file)
    if (packageName == null || packageName.trim { it <= ' ' }.isEmpty()) {
      flashError(R.string.msg_get_package_failed)
      return
    }

    val id: Int = binding.typeGroup.checkedButtonId

    val extension = if (isKotlin) ".kt" else ".java"
    val fileNameWithExt = if (name.endsWith(extension)) name else "$name$extension"
    val className = if (!name.contains(".")) name else name.substring(0, name.lastIndexOf("."))

    val created =
      when (id) {
        binding.typeClass.id ->
          createFile(
            context,
            node,
            file,
            fileNameWithExt,
            ProjectWriter.createClass(packageName, className, isKotlin)
          )

        binding.typeInterface.id ->
          createFile(
            context,
            node,
            file,
            fileNameWithExt,
            ProjectWriter.createInterface(packageName, className, isKotlin)
          )

        binding.typeEnum.id ->
          createFile(
            context,
            node,
            file,
            fileNameWithExt,
            ProjectWriter.createEnum(packageName, className, isKotlin)
          )

        binding.typeActivity.id ->
          createFile(
            context,
            node,
            file,
            fileNameWithExt,
            ProjectWriter.createActivity(packageName, className, isKotlin)
          )

        else -> createFile(context, node, file, name, "")
      }

    if (created && autoLayout) {
      val packagePath = packageName.toString().replace(".", "/")
      createAutoLayout(context, file, fileNameWithExt, packagePath)
    }
  }

  private fun isValidClassName(s: CharSequence?) =
    s == null || !SourceVersion.isName(s) || SourceVersion.isKeyword(s)

  private fun createAutoLayout(
    context: Context,
    directory: File,
    fileName: String,
    packagePath: String
  ) {
    val baseName = if (fileName.contains('.'))

    fileName.substring(0, fileName.lastIndexOf('.')) else fileName

    val absolutePath = directory.absolutePath
    val dir = absolutePath.let {
      it.replace("/(java|kotlin|java\\+kotlin)/$packagePath", "/res/layout")
    }

    val layoutName = ProjectWriter.createLayoutName(baseName + ".xml")
    val newFileLayout = File(dir, layoutName)

    if (newFileLayout.exists()) {
      flashError(R.string.msg_layout_file_exists)
      return
    }

    if (!FileIOUtils.writeFileFromString(newFileLayout, ProjectWriter.createLayout())) {
      flashError(R.string.msg_layout_file_creation_failed)
      return
    }

    notifyFileCreated(context, newFileLayout)
  }

  private fun createDrawableRes(context: Context, node: TreeNode?, file: File) {
    createNewFileWithContent(
      context,
      node,
      Environment.mkdirIfNotExits(file),
      ProjectWriter.createDrawable(),
      ".xml"
    )
  }

  private fun createLayoutRes(context: Context, node: TreeNode?, file: File) {
    createNewFileWithContent(
      context,
      node,
      Environment.mkdirIfNotExits(file),
      ProjectWriter.createLayout(),
      ".xml"
    )
  }

  private fun createMenuRes(context: Context, node: TreeNode?, file: File) {
    createNewFileWithContent(
      context,
      node,
      Environment.mkdirIfNotExits(file),
      ProjectWriter.createMenu(),
      ".xml"
    )
  }

  private fun createNewResource(context: Context, node: TreeNode?, file: File) {
    val labels =
      arrayOf(
        context.getString(R.string.restype_drawable),
        context.getString(R.string.restype_layout),
        context.getString(R.string.restype_menu),
        context.getString(R.string.restype_other)
      )
    val builder = DialogUtils.newMaterialDialogBuilder(context)
    builder.setTitle(R.string.new_xml_resource)
    builder.setItems(labels) { _, position ->
      when (position) {
        0 -> createDrawableRes(context, node, File(file, "drawable"))
        1 -> createLayoutRes(context, node, File(file, "layout"))
        2 -> createMenuRes(context, node, File(file, "menu"))
        3 -> createNewFile(context, node, file, true)
      }
    }
    builder.create().show()
  }

  private fun createNewEmptyFile(context: Context, node: TreeNode?, file: File) {
    createNewFileWithContent(context, node, file, "")
  }

  private fun createNewFileWithContent(
    context: Context,
    node: TreeNode?,
    file: File,
    content: String
  ) {
    createNewFileWithContent(context, node, file, content, null)
  }

  private fun createNewFileWithContent(
    context: Context,
    node: TreeNode?,
    folder: File,
    content: String,
    extension: String?,
  ) {
    val binding = LayoutDialogTextInputBinding.inflate(LayoutInflater.from(context))
    val builder = DialogUtils.newMaterialDialogBuilder(context)

    binding.name.editText!!.setHint(R.string.file_name)

    builder.setTitle(R.string.new_file)
    builder.setMessage(
      context.getString(R.string.msg_can_contain_slashes) +
          "\n\n" +
          context.getString(R.string.msg_newfile_dest, folder.absolutePath)
    )
    builder.setView(binding.root)
    builder.setCancelable(false)
    builder.setPositiveButton(R.string.text_create) { dialogInterface, _ ->
      dialogInterface.dismiss()
      var name = binding.name.editText!!.text.toString().trim()
      if (name.isBlank()) {
        flashError(R.string.msg_invalid_name)
        return@setPositiveButton
      }

      if (extension != null && extension.trim { it <= ' ' }.isNotEmpty()) {
        name = if (name.endsWith(extension)) name else name + extension
      }

      try {
        createFile(context, node, folder, name, content)
      } catch (e: Exception) {
        log.error("Failed to create file", e)
        flashError(e.cause?.message ?: e.message)
      }
    }
    builder.setNegativeButton(android.R.string.cancel, null)
    builder.create().show()
  }

  private fun createFile(
    context: Context,
    node: TreeNode?,
    directory: File,
    name: String,
    content: String
  ): Boolean {
    if (name.length !in 1..40 || name.startsWith("/")) {
      flashError(R.string.msg_invalid_name)
      return false
    }

    val newFile = File(directory, name)

    if (newFile.exists()) {
      flashError(R.string.msg_file_exists)
      return false
    }

    if (!FileIOUtils.writeFileFromString(newFile, content)) {
      flashError(R.string.msg_file_creation_failed)
      return false
    }

    notifyFileCreated(context, newFile)
    // TODO Notify language servers about file created event
    flashSuccess(R.string.msg_file_created)
    if (node != null) {
      val newNode = TreeNode(newFile)
      newNode.viewHolder = FileTreeViewHolder(context)
      node.addChild(newNode)
      requestExpandNode(node)
    } else {
      requestFileListing()
    }

    return true
  }

  private fun notifyFileCreated(context: Context, file: File) {
    EventBus.getDefault().post(FileCreationEvent(file).putData(context))
  }
}

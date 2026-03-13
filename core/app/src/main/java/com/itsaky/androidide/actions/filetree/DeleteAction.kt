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

import android.app.ProgressDialog
import android.content.Context
import com.blankj.utilcode.util.FileUtils
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.requireFile
import com.itsaky.androidide.eventbus.events.file.FileDeletionEvent
import com.itsaky.androidide.projects.FileManager
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.tasks.executeAsync
import com.itsaky.androidide.utils.DialogUtils
import com.itsaky.androidide.utils.FlashType
import com.itsaky.androidide.utils.flashMessage
import org.greenrobot.eventbus.EventBus
import java.io.File

/**
 * File tree action to delete files.
 *
 * @author Akash Yadav
 */
class DeleteAction(context: Context, override val order: Int) :
  BaseFileTreeAction(context, labelRes = R.string.delete_file, iconRes = R.drawable.ic_delete) {

  override val id: String = "ide.editor.fileTree.delete"

  override suspend fun execAction(data: ActionData) {
    val context = data.requireActivity()
    val file = data.requireFile()
    val lastHeld = data.getTreeNode()

    val projectDir = File(IProjectManager.getInstance().projectDirPath)
    if (file.canonicalPath == projectDir.canonicalPath) {
      flashMessage(
        R.string.delete_directory_failed,
        FlashType.ERROR
      )
      return
    }

    val builder = DialogUtils.newMaterialDialogBuilder(context)
    builder
      .setNegativeButton(R.string.no, null)
      .setPositiveButton(R.string.yes) { dialogInterface, _ ->
        dialogInterface.dismiss()
        @Suppress("DEPRECATION")
        val progressDialog =
          ProgressDialog.show(context, null, context.getString(R.string.please_wait), true, false)
        executeAsync({
          val gradleModules = findGradleModules(file)

          val deleted = FileUtils.delete(file)

          if (deleted) {
            gradleModules.forEach { module ->
              val path = getModulePath(module)
              removeModuleFromSettings(path)
              removeModuleDependency(path)
            }
          }
          deleted
        }) {
          progressDialog.dismiss()

          val deleted = it ?: false

          flashMessage(
            if (deleted) R.string.deleted else R.string.delete_file_failed,
            if (deleted) FlashType.SUCCESS else FlashType.ERROR
          )

          if (!deleted) {
            return@executeAsync
          }

          notifyFileDeleted(file, context)

          if (lastHeld != null) {
            val parent = lastHeld.parent
            parent.deleteChild(lastHeld)
            requestExpandNode(parent)
          } else {
            requestFileListing()
          }

          val frag = context.getEditorForFile(file)
          if (frag != null) {
            context.closeFile(context.findIndexOfEditorByFile(frag.file))
          }
        }
      }
      .setTitle(R.string.title_confirm_delete)
      .setMessage(
        context.getString(
          R.string.msg_confirm_delete,
          String.format("%s [%s]", file.name, file.absolutePath)
        )
      )
      .setCancelable(false)
      .create()
      .show()
  }

  private fun findGradleModules(file: File): List<File> {
    val modules = mutableListOf<File>()
    if (isGradleModule(file)) modules.add(file)
    file.listFiles()?.filter { it.isDirectory }?.forEach {
      modules.addAll(findGradleModules(it))
    }
    return modules
  }

  private fun isGradleModule(moduleDir: File): Boolean {
    return moduleDir.isDirectory && (
      File(moduleDir, "build.gradle").exists() ||
      File(moduleDir, "build.gradle.kts").exists()
    )
  }

  private fun getModulePath(moduleDir: File): String {
    val projectDir = File(IProjectManager.getInstance().projectDirPath)

    require(moduleDir.absolutePath.startsWith(projectDir.absolutePath)) {
      "File is outside project directory"
    }

    val relativePath = moduleDir.relativeTo(projectDir).path

    return ":" + relativePath.replace(File.separatorChar, ':')
  }

  private fun removeModuleFromSettings(modulePath: String) {
    val projectDir = File(IProjectManager.getInstance().projectDirPath)

    val settingsFile = listOf(
      File(projectDir, "settings.gradle.kts"),
      File(projectDir, "settings.gradle")
    ).firstOrNull { it.exists() } ?: return

    val content = settingsFile.readText()

    val includeRegex = Regex(
      """include\s*(\((.*?)\)|([^\n]*))""",
      setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)
    )

    val moduleRegex = Regex("""['"]([^'"]+)['"]""")

    val newContent = includeRegex.replace(content) { match ->
      val inside = match.groups[2]?.value ?: match.groups[3]?.value ?: ""

      val modules = moduleRegex.findAll(inside)
        .map { it.groupValues[1] }
        .filter { it != modulePath }
        .toList()

      if (modules.isEmpty()) {
        ""
      } else {
        val rebuilt = modules.joinToString(", ") { "'$it'" }

        if (match.value.contains("(")) {
            "include($rebuilt)"
        } else {
            "include $rebuilt"
        }
      }
    }.replace(Regex("""(\r?\n)[ \t]*(\r?\n)+"""), "\n\n")
     .trimEnd()

    settingsFile.writeText(newContent)
  }

  private fun removeModuleDependency(modulePath: String) {
    val projectDir = File(IProjectManager.getInstance().projectDirPath)

    val escapedModulePath = Regex.escape(modulePath)

    val dependencyRegex = Regex(
      """(?m)^[ \t]*\w+[ \t]*(\(\s*)?project\s*\(\s*(path\s*=\s*)?["']$escapedModulePath["']\s*\)\s*\)?[ \t]*\r?\n?""",
      RegexOption.MULTILINE
    )

    projectDir.walkTopDown()
      .filter {
        it.name == "build.gradle" ||
        it.name == "build.gradle.kts"
      }
      .forEach { gradleFile ->

        val content = gradleFile.readText()

        val newContent = content
          .replace(dependencyRegex, "")
          .replace(Regex("""(\r?\n){3,}"""), "\n\n")
          .trimEnd()

        if (newContent != content) {
          gradleFile.writeText(newContent)
        }
    }
  }

  private fun notifyFileDeleted(file: File, context: Context) {
    val deletionEvent = FileDeletionEvent(file)

    // Notify FileManager first
    FileManager.onFileDeleted(deletionEvent)

    EventBus.getDefault().post(deletionEvent.putData(context))
  }
}

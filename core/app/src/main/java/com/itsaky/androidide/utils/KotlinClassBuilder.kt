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

import android.app.Activity
import android.os.Bundle
import com.itsaky.androidide.preferences.utils.indentationString
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName

object KotlinClassBuilder { 

  @JvmStatic
  fun createClass(packageName: String, className: String): String {
    val type = TypeSpec.classBuilder(className)
      .addModifiers(PUBLIC)
      .build()

    return toKotlinFile(packageName, className, type)
  }

  @JvmStatic
  fun createInterface(packageName: String, className: String): String {
    val type = TypeSpec.interfaceBuilder(className)
      .addModifiers(PUBLIC)
      .build()

    return toKotlinFile(packageName, className, type)
  }

  @JvmStatic
  fun createEnum(packageName: String, className: String): String {
    val type = TypeSpec.enumBuilder(className)
      .addEnumConstant("ENUM_DECLARED")
      .build()

    return toKotlinFile(packageName, className, type)
  }

  @JvmStatic
  fun createActivity(packageName: String, className: String): String {
    val onCreate = FunSpec.builder("onCreate")
      .addModifiers(OVERRIDE)
      .addParameter("savedInstanceState", Bundle::class.asClassName()
        .copy(nullable = true)
      )
      .addStatement("super.onCreate(savedInstanceState)")
      .build()

    val type = TypeSpec.classBuilder(className)
      .superclass(Activity::class.asClassName())
      .addFunction(onCreate)
      .build()

    return toKotlinFile(packageName, className, type)
  }

  private fun toKotlinFile(
    packageName: String,
    fileName: String,
    type: TypeSpec,
    block: FileSpec.Builder.() -> Unit = {}
  ): String {
    return FileSpec.builder(packageName, fileName)
      .indent(indentationString)
      .addType(type)
      .apply(block)
      .build()
      .toString()
      .trimIndent()
  }
}
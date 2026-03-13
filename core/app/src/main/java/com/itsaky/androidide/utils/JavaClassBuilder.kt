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
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeSpec
import jdkx.lang.model.element.Modifier.PROTECTED
import jdkx.lang.model.element.Modifier.PUBLIC

object JavaClassBuilder { 

  @JvmStatic
  fun createClass(packageName: String, className: String): String {
    val type = TypeSpec.classBuilder(className)
      .addModifiers(PUBLIC)
      .build()

    return toJavaFile(packageName, type)
  }

  @JvmStatic
  fun createInterface(packageName: String, className: String): String {
    val type = TypeSpec.interfaceBuilder(className)
      .addModifiers(PUBLIC)
      .build()

    return toJavaFile(packageName, type)
  }

  @JvmStatic
  fun createEnum(packageName: String, className: String): String {
    val type = TypeSpec.enumBuilder(className)
      .addModifiers(PUBLIC)
      .addEnumConstant("ENUM_DECLARED")
      .build()

    return toJavaFile(packageName, type)
  }

  @JvmStatic
  fun createActivity(packageName: String, className: String): String {
    val onCreate = MethodSpec.methodBuilder("onCreate")
      .addAnnotation(Override::class.java)
      .addModifiers(PROTECTED)
      .addParameter(Bundle::class.java, "savedInstanceState")
      .addStatement("super.onCreate(savedInstanceState)")
      .build()

    val type = TypeSpec.classBuilder(className)
      .addModifiers(PUBLIC)
      .superclass(Activity::class.java)
      .addMethod(onCreate)
      .build()

    return toJavaFile(packageName, type) { skipJavaLangImports(true) }
  }

  private fun toJavaFile(
    packageName: String,
    type: TypeSpec,
    block: JavaFile.Builder.() -> Unit = {}
  ): String {
    return JavaFile.builder(packageName, type)
      .indent(indentationString)
      .apply(block)
      .build()
      .toString()
      .trimIndent()
  }
}
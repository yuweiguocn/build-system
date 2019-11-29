/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.builder.symbols

import com.android.SdkConstants
import com.android.ide.common.symbols.Symbol
import com.android.ide.common.symbols.SymbolTable
import com.android.ide.common.symbols.canonicalizeValueResourceName
import com.android.resources.ResourceType
import com.google.common.base.Splitter
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_MAXS
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.ACC_FINAL
import org.objectweb.asm.Opcodes.ACC_PRIVATE
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.ACC_SUPER
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.BIPUSH
import org.objectweb.asm.Opcodes.DUP
import org.objectweb.asm.Opcodes.IASTORE
import org.objectweb.asm.Opcodes.INVOKESPECIAL
import org.objectweb.asm.Opcodes.NEWARRAY
import org.objectweb.asm.Opcodes.PUTSTATIC
import org.objectweb.asm.Opcodes.RETURN
import org.objectweb.asm.Opcodes.T_INT
import java.io.BufferedOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.EnumSet
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

private val VALUE_ID_SPLITTER = Splitter.on(',').trimResults()

@Throws(IOException::class)
fun exportToCompiledJava(tables: Iterable<SymbolTable>, outJar: Path) {
    JarOutputStream(BufferedOutputStream(Files.newOutputStream(outJar))).use { jarOutputStream ->
        tables.forEach { table ->
            val resourceTypes = EnumSet.noneOf(ResourceType::class.java)
            for (resType in ResourceType.values()) {
                // Don't write empty R$ classes.
                val bytes = generateResourceTypeClass(table, resType) ?: continue
                resourceTypes.add(resType)
                val innerR = internalName(table, resType)
                jarOutputStream.putNextEntry(ZipEntry(innerR + SdkConstants.DOT_CLASS))
                jarOutputStream.write(bytes)
            }

            // Generate and write the main R class file.
            val packageR = internalName(table, null)
            jarOutputStream.putNextEntry(ZipEntry(packageR + SdkConstants.DOT_CLASS))
            jarOutputStream.write(generateOuterRClass(resourceTypes, packageR))
        }
    }
}

private fun generateOuterRClass(resourceTypes: EnumSet<ResourceType>, packageR: String): ByteArray {
    val cw = ClassWriter(COMPUTE_MAXS)
    cw.visit(
            Opcodes.V1_8,
            ACC_PUBLIC + ACC_FINAL + ACC_SUPER,
            packageR, null,
            "java/lang/Object", null)

    for (rt in resourceTypes) {
        cw.visitInnerClass(
                packageR + "$" + rt.getName(),
                packageR,
                rt.getName(),
                ACC_PUBLIC + ACC_FINAL + ACC_STATIC)
    }

    // Constructor
    val mv: MethodVisitor
    mv = cw.visitMethod(ACC_PRIVATE, "<init>", "()V", null, null)
    mv.visitCode()
    mv.visitVarInsn(ALOAD, 0)
    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
    mv.visitInsn(RETURN)
    mv.visitMaxs(0, 0)
    mv.visitEnd()

    cw.visitEnd()

    return cw.toByteArray()
}

private fun generateResourceTypeClass(table: SymbolTable, resType: ResourceType): ByteArray? {
    val symbols = table.getSymbolByResourceType(resType)
    if (symbols.isEmpty()) {
        return null
    }
    val cw = ClassWriter(COMPUTE_MAXS)
    val internalName = internalName(table, resType)
    cw.visit(
            Opcodes.V1_8,
            ACC_PUBLIC + ACC_FINAL + ACC_SUPER,
            internalName, null,
            "java/lang/Object", null)

    cw.visitInnerClass(
            internalName,
            internalName(table, null),
            resType.getName(),
            ACC_PUBLIC + ACC_FINAL + ACC_STATIC)

    for (s in symbols) {
        cw.visitField(
                ACC_PUBLIC + ACC_STATIC,
                s.canonicalName,
                s.javaType.desc,
                null,
                if (s is Symbol.StyleableSymbol) null else s.intValue
        )
                .visitEnd()

        if (s is Symbol.StyleableSymbol) {
            val children = s.children
            for ((i, child) in children.withIndex()) {
                cw.visitField(
                        ACC_PUBLIC + ACC_STATIC,
                        "${s.canonicalName}_${canonicalizeValueResourceName(child)}",
                        "I",
                        null,
                        i)
            }
        }
    }

    // Constructor
    val init = cw.visitMethod(ACC_PRIVATE, "<init>", "()V", null, null)
    init.visitCode()
    init.visitVarInsn(ALOAD, 0)
    init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
    init.visitInsn(RETURN)
    init.visitMaxs(0, 0)
    init.visitEnd()

    // init method
    if (resType == ResourceType.STYLEABLE) {
        val clinit = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null)
        clinit.visitCode()
        for (s in symbols) {
            s as Symbol.StyleableSymbol
            val values = s.values
            clinit.visitIntInsn(BIPUSH, values.size)
            clinit.visitIntInsn(NEWARRAY, T_INT)

            for ((i, value) in values.withIndex()) {
                clinit.visitInsn(DUP)
                clinit.visitIntInsn(BIPUSH, i)
                clinit.visitLdcInsn(value)
                clinit.visitInsn(IASTORE)
            }

            clinit.visitFieldInsn(PUTSTATIC, internalName, s.canonicalName, "[I")
        }
        clinit.visitInsn(RETURN)
        clinit.visitMaxs(0, 0)
        clinit.visitEnd()
    }

    cw.visitEnd()

    return cw.toByteArray()
}

private fun internalName(table: SymbolTable, type: ResourceType?): String {
    val className = if (type == null) "R" else "R$${type.getName()}"

    return if (table.tablePackage.isEmpty()) {
        className
    } else {
        "${table.tablePackage.replace(".", "/")}/$className"
    }
}

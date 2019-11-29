/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.process

import android.databinding.tool.util.StringUtils
import com.android.SdkConstants
import com.android.utils.FileUtils
import com.google.common.base.Joiner
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.logging.Logger

open class OpenJDKJarSigner {

    companion object {

        val jarSignerExecutable =
            if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) "jarsigner.exe"
            else "jarsigner"

        val logger: Logger = Logger.getLogger(OpenJDKJarSigner::javaClass.name)
    }

    /**
     * Signs the jar file
     */
    fun sign(toBeSigned: File, signature: JarSigner.Signature) {

        if (!toBeSigned.exists()) {
            throw FileNotFoundException("Signing target ${toBeSigned.absolutePath} not found")
        }

        val jarSigner = locatedJarSigner()
        val args = mutableListOf<String>(
            if (jarSigner!=null) jarSigner.absolutePath else jarSignerExecutable)

        args.add("-keystore")
        args.add(signature.keystoreFile.absolutePath)

        var keyStorePasswordFile: File? = null
        var aliasPasswordFile: File? = null

        // write passwords to a file so it cannot be spied on.
        if (signature.keystorePassword != null) {
            keyStorePasswordFile = File.createTempFile("store", "prv")
            FileUtils.writeToFile(keyStorePasswordFile, signature.keystorePassword)
            args.add("-storepass:file")
            args.add(keyStorePasswordFile.absolutePath)
        }

        if (signature.keyPassword != null) {
            aliasPasswordFile = File.createTempFile("alias", "prv")
            FileUtils.writeToFile(aliasPasswordFile, signature.keyPassword)
            args.add("--keypass:file")
            args.add(aliasPasswordFile.absolutePath)
        }

        args.add(toBeSigned.absolutePath)

        if (signature.keyAlias != null) {
            args.add(signature.keyAlias)
        }

        val errorLog = File.createTempFile("error", ".log")
        val outputLog = File.createTempFile("output", ".log")

        logger.fine("Invoking " + Joiner.on(" ").join(args))
        val process: Process = try {
            start(ProcessBuilder(args).redirectError(errorLog).redirectOutput(outputLog))
        } catch(e: IOException) {
            throw RuntimeException("Cannot start \"$jarSignerExecutable\" process, please add it " +
                        "to the PATH", e)
        }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val errors = FileUtils.loadFileWithUnixLineSeparators(errorLog)
            val output = FileUtils.loadFileWithUnixLineSeparators(outputLog)
            throw RuntimeException("${jarSignerExecutable}failed with exit code $exitCode :\n" +
                if (StringUtils.isNotBlank(errors)) errors else output)
        }

        keyStorePasswordFile?.delete()
        aliasPasswordFile?.delete()
    }

    open fun start(processBuilder: ProcessBuilder) = processBuilder.start()!!

    /**
     * Return the "jarsigner" tool location or null if it cannot be determined.
     */
    open fun locatedJarSigner(): File? {
        // Look in the java.home bin folder, on jdk installations or Mac OS X, this is where the
        // javasigner will be located.
        val javaHome = File(System.getProperty("java.home"))
        var jarSigner = getJarSigner(javaHome)
        if (jarSigner.exists()) {
            return jarSigner
        } else {
            // if not in java.home bin, it's probable that the java.home points to a JRE
            // installation, we should then look one folder up and in the bin folder.
            jarSigner = getJarSigner(javaHome.parentFile)
            // if still cant' find it, give up.
            return if (jarSigner.exists()) jarSigner else null
        }
    }

    /**
     * Returns the jarsigner tool location with the bin folder.
     */
    private fun getJarSigner(parentDir: File) = File(File(parentDir, "bin"), jarSignerExecutable)
}
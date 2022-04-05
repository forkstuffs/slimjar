//
// MIT License
//
// Copyright (c) 2021 Vaishnav Anil
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//

package io.github.slimjar

import io.github.slimjar.exceptions.ShadowNotFoundException
import io.github.slimjar.func.*
import io.github.slimjar.task.SlimJar
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.plugins.JavaPlugin
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.maven

const val SLIM_CONFIGURATION_NAME = "slim"
const val SLIM_API_CONFIGURATION_NAME = "slimApi"
const val SLIM_JAR_TASK_NAME = "slimJar"
private const val RESOURCES_TASK = "processResources"

class SlimJarPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = with(project) {
        // Applies Java if not present, since it's required for the compileOnly configuration
        plugins.apply(JavaPlugin::class.java)

        val slimConfig = createConfig(
            SLIM_CONFIGURATION_NAME,
            JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME,
            JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME
        )
        if (plugins.hasPlugin("java-library")) {
            createConfig(
                SLIM_API_CONFIGURATION_NAME,
                JavaPlugin.COMPILE_ONLY_API_CONFIGURATION_NAME,
                JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME
            )
        }

        val slimJar = tasks.create(SLIM_JAR_TASK_NAME, SlimJar::class.java, slimConfig)
        // Auto adds the slimJar lib dependency
        afterEvaluate {
            if (applyReleaseRepo) {
                repositories.maven("https://repo.vshnv.tech/")
            }
            if (applySnapshotRepo) {
                repositories.maven("https://repo.vshnv.tech/snapshots/")
            }
            if (plugins.hasPlugin("java-library")) {
                scanSlim(project).forEach {
                    project.dependencies.slim(it)
                }
            }
        }
        project.dependencies.extra.set(
            "slimjar",
            asGroovyClosure("+") { version -> slimJarLib(version) }
        )
        // Runs the task once resources are being processed to save the json file
        tasks.findByName(RESOURCES_TASK)?.finalizedBy(slimJar)
    }

    private fun scanSlim(project: Project): Collection<Dependency> {
        val found = HashSet<Dependency>()
        val impl = project.configurations.findByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)
        if (impl == null) {
            return emptyList()
        }
        impl.dependencies
            .filterIsInstance<DefaultProjectDependency>()
            .map { it.dependencyProject }
            .forEach {
                found.addAll(scanSlim(it))
                val slim = it.configurations.findByName(SLIM_CONFIGURATION_NAME)
                if (slim == null) {
                    return@forEach
                }
                slim.dependencies
                    .filterNotNull()
                    .forEach { dep ->
                        found.add(dep)
                    }
            }
        return found
    }
}

internal fun slimJarLib(version: String) = "io.github.slimjar:slimjar:$version"

package cn.oryjk.changuan

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArchitectureTest {
    private fun sourceRoot(): File {
        val candidates = listOf(
            File("src/main/java/cn/oryjk/changuan"),
            File("app/src/main/java/cn/oryjk/changuan"),
            File("android/app/src/main/java/cn/oryjk/changuan"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Cannot locate Android main source directory from ${File(".").absolutePath}")
    }

    @Test
    fun `project is 100 percent Kotlin with zero Java source files`() {
        val root = sourceRoot()
        val javaFiles = root.walkTopDown().filter { it.isFile && it.extension == "java" }.toList()
        assertTrue("Expected zero .java files, found: $javaFiles", javaFiles.isEmpty())
    }

    @Test
    fun `domain layer has no outer layer or framework imports`() {
        val domainDir = File(sourceRoot(), "domain")
        val forbiddenPrefixes = listOf(
            "import android.",
            "import androidx.",
            "import okhttp3.",
            "import com.google.gson.",
            "import cn.oryjk.changuan.application",
            "import cn.oryjk.changuan.infrastructure",
            "import cn.oryjk.changuan.presentation",
            "import cn.oryjk.changuan.app",
        )
        domainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.readLines().forEach { line ->
                val trimmed = line.trim()
                forbiddenPrefixes.forEach { prefix ->
                    assertTrue(
                        "Forbidden import '$trimmed' in domain file ${file.name}",
                        !trimmed.startsWith(prefix),
                    )
                }
            }
        }
    }

    @Test
    fun `application layer only depends on domain`() {
        val appLayerDir = File(sourceRoot(), "application")
        val forbiddenPrefixes = listOf(
            "import android.",
            "import androidx.",
            "import okhttp3.",
            "import com.google.gson.",
            "import cn.oryjk.changuan.infrastructure",
            "import cn.oryjk.changuan.presentation",
            "import cn.oryjk.changuan.app",
        )
        appLayerDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.readLines().forEach { line ->
                val trimmed = line.trim()
                forbiddenPrefixes.forEach { prefix ->
                    assertTrue(
                        "Forbidden import '$trimmed' in application file ${file.name}",
                        !trimmed.startsWith(prefix),
                    )
                }
            }
        }
    }

    @Test
    fun `presentation layer does not import infrastructure and charts do not access auth or network`() {
        val presentationDir = File(sourceRoot(), "presentation")
        presentationDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.readLines().forEach { line ->
                val trimmed = line.trim()
                assertTrue(
                    "Presentation file ${file.name} must not import infrastructure: $trimmed",
                    !trimmed.startsWith("import cn.oryjk.changuan.infrastructure"),
                )
                assertTrue(
                    "Presentation file ${file.name} must not import okhttp3: $trimmed",
                    !trimmed.startsWith("import okhttp3."),
                )
            }
        }

        val chartsDir = File(presentationDir, "charts")
        chartsDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.readLines().forEach { line ->
                val trimmed = line.trim()
                assertTrue(
                    "Charts file ${file.name} must not import application auth ports: $trimmed",
                    !trimmed.startsWith("import cn.oryjk.changuan.application"),
                )
            }
        }
    }
}

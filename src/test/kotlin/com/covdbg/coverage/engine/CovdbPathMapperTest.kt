package com.covdbg.coverage.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Local files are simulated: [localFiles] holds the canonical paths that "exist", looked up
 * case-insensitively the way Windows does, and returned in their canonical spelling.
 */
class CovdbPathMapperTest {

    private fun mapper(
        localFiles: List<String>,
        sourceRoot: String? = """C:\build\proj""",
        projectRoot: String? = "D:/work/proj"
    ) = CovdbPathMapper(
        sourceRoot = sourceRoot,
        projectRoot = projectRoot,
        existingFile = { path -> localFiles.firstOrNull { it.equals(path, ignoreCase = true) } },
        projectFilesNamed = { name -> localFiles.filter { it.substringAfterLast('/') == name } }
    )

    @Test
    fun `a path that exists here is used in its canonical spelling`() {
        val m = mapper(listOf("D:/work/proj/src/a.cpp"))
        assertEquals("D:/work/proj/src/a.cpp", m.find("""d:\WORK\proj\src\a.cpp"""))
    }

    @Test
    fun `a path under the database's source root is re-rooted at the project`() {
        val m = mapper(listOf("D:/work/proj/src/a.cpp"))
        assertEquals("D:/work/proj/src/a.cpp", m.find("""C:\build\proj\src\a.cpp"""))
    }

    @Test
    fun `a source root match is not fooled by a sibling directory with the same prefix`() {
        // C:\build\proj-old is not under C:\build\proj; only the unique-name fallback may map it.
        val m = mapper(listOf("D:/work/proj/src/a.cpp", "D:/work/proj/lib/a.cpp"))
        assertNull(m.find("""C:\build\proj-old\src\a.cpp"""))
    }

    @Test
    fun `a file name found once in the project is taken to be the same file`() {
        val m = mapper(listOf("D:/work/proj/src/moved/widget.cpp"), sourceRoot = null)
        assertEquals("D:/work/proj/src/moved/widget.cpp", m.find("""E:\elsewhere\widget.cpp"""))
    }

    @Test
    fun `an ambiguous file name is left unmapped`() {
        val m = mapper(listOf("D:/work/proj/a/util.cpp", "D:/work/proj/b/util.cpp"), sourceRoot = null)
        assertNull(m.find("""E:\elsewhere\util.cpp"""))
    }
}

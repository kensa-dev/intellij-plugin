package dev.kensa.plugin.intellij.agentskills

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentSkillBundleBuilderTest {

    private val skillRaw = """
        ---
        name: kensa-development
        description: review tests
        ---

        # Body

        Real content.
    """.trimIndent()

    private val references = listOf(
        "references/fixtures.md" to "# Fixtures\n\nFixture rules.",
        "references/setup-steps.md" to "# Setup steps\n\nSetup rules.",
    )

    @Test
    fun `strips Claude frontmatter`() {
        val stripped = AgentSkillBundleBuilder.stripClaudeFrontmatter(skillRaw)
        assertFalse(stripped.startsWith("---"))
        assertTrue(stripped.startsWith("# Body"))
    }

    @Test
    fun `single file output for Copilot scoped target prepends applyTo frontmatter`() {
        val output = AgentSkillBundleBuilder.buildSingleFile(SkillTarget.COPILOT_SCOPED, skillRaw, references)
        assertTrue(output.startsWith("---\napplyTo: \"**/*Test.{kt,java}\"\n---\n\n"))
        assertTrue(output.contains("# Body"))
        assertTrue(output.contains("\n# Fixtures\n\n# Fixtures\n\nFixture rules."))
    }

    @Test
    fun `single file output for always-loaded target has no frontmatter`() {
        val output = AgentSkillBundleBuilder.buildSingleFile(SkillTarget.COPILOT_ALWAYS, skillRaw, references)
        assertFalse(output.startsWith("---"))
        assertTrue(output.startsWith("# Body"))
    }

    @Test
    fun `references are appended under headings derived from filename`() {
        val output = AgentSkillBundleBuilder.buildSingleFile(SkillTarget.JUNIE, skillRaw, references)
        assertTrue(output.contains("\n# Fixtures\n"), "expected Fixtures heading")
        assertTrue(output.contains("\n# Setup Steps\n"), "expected humanized Setup Steps heading")
    }

    @Test
    fun `Claude Code target preserves original skill frontmatter and writes multiple files`() {
        val files = AgentSkillBundleBuilder.buildClaudeSkillFiles(SkillTarget.CLAUDE_CODE, skillRaw, references)

        assertEquals(
            setOf(
                ".claude/skills/kensa-development/SKILL.md",
                ".claude/skills/kensa-development/references/fixtures.md",
                ".claude/skills/kensa-development/references/setup-steps.md",
            ),
            files.keys,
        )
        assertEquals(skillRaw, files[".claude/skills/kensa-development/SKILL.md"])
        assertTrue(files[".claude/skills/kensa-development/SKILL.md"]!!.startsWith("---"),
            "Claude frontmatter must be preserved")
        assertEquals("# Fixtures\n\nFixture rules.", files[".claude/skills/kensa-development/references/fixtures.md"])
        assertEquals("# Setup steps\n\nSetup rules.", files[".claude/skills/kensa-development/references/setup-steps.md"])
    }

    @Test
    fun `output paths reflect target`() {
        assertEquals(".github/instructions/kensa.instructions.md", SkillTarget.COPILOT_SCOPED.outputPath)
        assertEquals(".github/copilot-instructions.md", SkillTarget.COPILOT_ALWAYS.outputPath)
        assertEquals(".cursor/rules/kensa.mdc", SkillTarget.CURSOR.outputPath)
        assertEquals(".junie/guidelines.md", SkillTarget.JUNIE.outputPath)
        assertEquals(".claude/skills/kensa-development", SkillTarget.CLAUDE_CODE.outputPath)
        assertTrue(SkillTarget.CLAUDE_CODE.isDirectory)
    }
}

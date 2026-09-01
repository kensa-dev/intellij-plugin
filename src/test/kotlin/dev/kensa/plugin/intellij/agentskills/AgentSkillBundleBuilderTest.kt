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
    fun `output paths reflect target`() {
        assertEquals(".github/instructions/kensa.instructions.md", SkillTarget.COPILOT_SCOPED.outputPath)
        assertEquals(".github/copilot-instructions.md", SkillTarget.COPILOT_ALWAYS.outputPath)
        assertEquals(".cursor/rules/kensa.mdc", SkillTarget.CURSOR.outputPath)
        assertEquals(".junie/guidelines.md", SkillTarget.JUNIE.outputPath)
        assertEquals(".claude/skills/kensa-development", SkillTarget.CLAUDE_CODE.outputPath)
        assertTrue(SkillTarget.CLAUDE_CODE.isDirectory)
    }
    @Test
    fun `Claude Code install copies every file the manifest lists`() {
        val files = AgentSkillBundleBuilder.filesFor(SkillTarget.CLAUDE_CODE, "/test-skills/kensa-development")

        assertEquals(
            setOf(
                ".claude/skills/kensa-development/SKILL.md",
                ".claude/skills/kensa-development/references/captured-outputs.md",
                ".claude/skills/kensa-development/references/fixtures.md",
                ".claude/skills/kensa-development/references/interactions.md",
                ".claude/skills/kensa-development/references/rendered-value.md",
                ".claude/skills/kensa-development/references/setup-steps.md",
                ".claude/skills/kensa-development/references/mcp-tools.md",
                ".claude/skills/kensa-development/references/authoring/overview.md",
            ),
            files.keys,
        )
        assertEquals("# Overview\n\nPipeline.\n", files[".claude/skills/kensa-development/references/authoring/overview.md"])
    }

    @Test
    fun `single file install concatenates only the practice references`() {
        val output = AgentSkillBundleBuilder.filesFor(SkillTarget.JUNIE, "/test-skills/kensa-development").values.single()

        assertTrue(output.contains("fixtures rules."))
        assertTrue(output.contains("setup-steps rules."))
        assertFalse(output.contains("mcp-tools rules."))
        assertFalse(output.contains("Pipeline."))
    }
}

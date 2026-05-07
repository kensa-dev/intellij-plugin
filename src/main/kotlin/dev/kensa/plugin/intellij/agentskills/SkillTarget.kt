package dev.kensa.plugin.intellij.agentskills

enum class SkillTarget(
    val displayName: String,
    val outputPath: String,
    val frontmatter: String?,
    val isDirectory: Boolean = false,
) {
    COPILOT_SCOPED(
        displayName = "GitHub Copilot — scoped to test files",
        outputPath = ".github/instructions/kensa.instructions.md",
        frontmatter = "applyTo: \"**/*Test.{kt,java}\"",
    ),
    COPILOT_ALWAYS(
        displayName = "GitHub Copilot — always loaded",
        outputPath = ".github/copilot-instructions.md",
        frontmatter = null,
    ),
    JUNIE(
        displayName = "JetBrains Junie",
        outputPath = ".junie/guidelines.md",
        frontmatter = null,
    ),
    CURSOR(
        displayName = "Cursor",
        outputPath = ".cursor/rules/kensa.mdc",
        frontmatter = null,
    ),
    CLAUDE_CODE(
        displayName = "Claude Code skill files",
        outputPath = ".claude/skills/kensa-development",
        frontmatter = null,
        isDirectory = true,
    );

    override fun toString(): String = displayName
}

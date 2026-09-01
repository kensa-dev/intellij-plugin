package dev.kensa.plugin.intellij.agentskills

object AgentSkillBundleBuilder {

    private const val RESOURCE_ROOT = "/skills/kensa-development"
    private const val SKILL_FILE = "SKILL.md"
    private const val MANIFEST_FILE = "manifest.txt"

    // The single-file targets (Copilot, Junie, Cursor) get the practice rules
    // only: the authoring pipeline and MCP notes are Claude-specific and would
    // bloat an always-loaded instruction file.
    private val PRACTICE_REFERENCES = listOf(
        "captured-outputs",
        "fixtures",
        "interactions",
        "rendered-value",
        "setup-steps",
    )

    fun filesFor(target: SkillTarget, resourceRoot: String = RESOURCE_ROOT): Map<String, String> {
        if (target.isDirectory) {
            // The manifest lists every file fetchKensaSkills bundled, so the
            // installed skill matches the skill the router expects.
            return readResource("$resourceRoot/$MANIFEST_FILE").lineSequence()
                .filter { it.isNotBlank() }
                .associate { relative -> "${target.outputPath}/$relative" to readResource("$resourceRoot/$relative") }
        }
        val skillRaw = readResource("$resourceRoot/$SKILL_FILE")
        val references = PRACTICE_REFERENCES.map { name ->
            "references/$name.md" to readResource("$resourceRoot/references/$name.md")
        }
        return mapOf(target.outputPath to buildSingleFile(target, skillRaw, references))
    }

    internal fun buildSingleFile(
        target: SkillTarget,
        skillRaw: String,
        references: List<Pair<String, String>>,
    ): String = buildString {
        target.frontmatter?.let { fm -> append("---\n").append(fm).append("\n---\n\n") }
        append(stripClaudeFrontmatter(skillRaw).trimEnd()).append('\n')
        for ((relativePath, content) in references) {
            val name = relativePath.substringAfter("references/").removeSuffix(".md")
            append("\n\n---\n\n# ")
            append(humanize(name))
            append("\n\n")
            append(content.trimEnd())
            append('\n')
        }
    }

    internal fun stripClaudeFrontmatter(text: String): String {
        if (!text.startsWith("---")) return text
        val firstNewline = text.indexOf('\n')
        if (firstNewline < 0) return text
        val end = text.indexOf("\n---", firstNewline)
        if (end < 0) return text
        val afterClose = text.indexOf('\n', end + 4)
        return if (afterClose < 0) "" else text.substring(afterClose + 1).trimStart('\n')
    }

    private fun humanize(slug: String): String =
        slug.split('-').joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }

    private fun readResource(path: String): String =
        AgentSkillBundleBuilder::class.java.getResourceAsStream(path)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: error("Bundled Kensa skill resource missing: $path. Run ./gradlew fetchKensaSkills.")
}

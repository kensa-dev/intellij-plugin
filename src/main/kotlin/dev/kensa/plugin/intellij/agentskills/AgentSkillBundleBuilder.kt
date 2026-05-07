package dev.kensa.plugin.intellij.agentskills

object AgentSkillBundleBuilder {

    private const val RESOURCE_ROOT = "/skills/kensa-development"
    private const val SKILL_FILE = "SKILL.md"

    private val REFERENCE_FILES = listOf(
        "captured-outputs",
        "fixtures",
        "interactions",
        "rendered-value",
        "setup-steps",
    )

    fun filesFor(target: SkillTarget): Map<String, String> {
        val skillRaw = readResource("$RESOURCE_ROOT/$SKILL_FILE")
        val references = REFERENCE_FILES.map { name ->
            "references/$name.md" to readResource("$RESOURCE_ROOT/references/$name.md")
        }
        return if (target.isDirectory) {
            buildClaudeSkillFiles(target, skillRaw, references)
        } else {
            mapOf(target.outputPath to buildSingleFile(target, skillRaw, references))
        }
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

    internal fun buildClaudeSkillFiles(
        target: SkillTarget,
        skillRaw: String,
        references: List<Pair<String, String>>,
    ): Map<String, String> = buildMap {
        put("${target.outputPath}/$SKILL_FILE", skillRaw)
        for ((relativePath, content) in references) {
            put("${target.outputPath}/$relativePath", content)
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

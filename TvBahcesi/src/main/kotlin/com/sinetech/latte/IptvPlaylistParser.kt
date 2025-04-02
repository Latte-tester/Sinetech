package com.sinetech.latte

data class M3UItem(
    val title: String? = null,
    val url: String? = null,
    val attributes: Map<String, Any> = emptyMap(),
    val season: Int = 1,
    val episode: Int = 0
)

data class M3UPlaylist(
    val items: List<M3UItem> = emptyList()
)

class IptvPlaylistParser {
    fun parseM3U(content: String): M3UPlaylist {
        val items = mutableListOf<M3UItem>()
        var currentAttributes = mutableMapOf<String, Any>()
        var currentTitle: String? = null

        content.lines().forEach { line ->
            when {
                line.startsWith("#EXTINF:") -> {
                    currentAttributes = parseAttributes(line)
                    currentTitle = extractTitle(line)
                }
                line.isNotBlank() && !line.startsWith("#") -> {
                    items.add(
                        M3UItem(
                            title = currentTitle,
                            url = line.trim(),
                            attributes = currentAttributes
                        )
                    )
                    currentAttributes = mutableMapOf()
                    currentTitle = null
                }
            }
        }

        return M3UPlaylist(items)
    }

    private fun parseAttributes(line: String): MutableMap<String, Any> {
        val attributes = mutableMapOf<String, Any>()
        val regex = "([a-zA-Z-]+)=\"([^\"]*)\"|".toRegex()

        regex.findAll(line).forEach { matchResult ->
            val (key, value) = matchResult.destructured
            val cleanValue = cleanAttributeValue(value)
            attributes[key] = cleanValue
        }

        return attributes
    }

    private fun cleanAttributeValue(value: String): String {
        return value.replace(Regex("like Gecko\\) Chrome/[\\d.]+\\s*Safari/[\\d.]+\\s*CrKey/[\\d.]+"), "")
            .replace(Regex(",like Gecko\\) Chrome.*?"), "")
            .replace(Regex("\\s*,\\s*$"), "")
            .trim()
    }

    private fun extractTitle(line: String): String? {
        val commaIndex = line.lastIndexOf(",")
        return if (commaIndex != -1 && commaIndex + 1 < line.length) {
            val title = line.substring(commaIndex + 1).trim()
            cleanAttributeValue(title)
        } else {
            null
        }
    }
}
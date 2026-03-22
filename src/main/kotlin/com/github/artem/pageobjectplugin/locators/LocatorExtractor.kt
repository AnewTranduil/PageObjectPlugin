package com.github.artem.pageobjectplugin.locators

data class ExtractedLocator(
    val type: String,
    val value: String,
    val cssSelector: String?
)

object LocatorExtractor {

    private val LOCATOR_PATTERN = Regex(
        """(?:this\.)?page\.locator\(\s*['"` ]([^'"` ]+)['"` ]\s*\)"""
    )

    private val GET_BY_ROLE_PATTERN = Regex(
        """(?:this\.)?page\.getByRole\(\s*['"](\w+)['"]\s*(?:,\s*\{[^}]*name:\s*['"]([^'"]+)['"][^}]*\})?\s*\)"""
    )

    private val GET_BY_TEXT_PATTERN = Regex(
        """(?:this\.)?page\.getByText\(\s*['"]([^'"]+)['"]\s*\)"""
    )

    private val GET_BY_TEST_ID_PATTERN = Regex(
        """(?:this\.)?page\.getByTestId\(\s*['"]([^'"]+)['"]\s*\)"""
    )

    private val GET_BY_PLACEHOLDER_PATTERN = Regex(
        """(?:this\.)?page\.getByPlaceholder\(\s*['"]([^'"]+)['"]\s*\)"""
    )

    fun extract(line: String): ExtractedLocator? {
        // Try each pattern in order

        GET_BY_TEST_ID_PATTERN.find(line)?.let { match ->
            val testId = match.groupValues[1]
            return ExtractedLocator(
                type = "getByTestId",
                value = testId,
                cssSelector = "[data-testid=\"$testId\"]"
            )
        }

        GET_BY_ROLE_PATTERN.find(line)?.let { match ->
            val role = match.groupValues[1]
            val name = match.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
            return ExtractedLocator(
                type = "getByRole",
                value = if (name != null) "$role:$name" else role,
                cssSelector = "[role=\"$role\"]"
            )
        }

        GET_BY_TEXT_PATTERN.find(line)?.let { match ->
            val text = match.groupValues[1]
            return ExtractedLocator(
                type = "getByText",
                value = text,
                cssSelector = null
            )
        }

        GET_BY_PLACEHOLDER_PATTERN.find(line)?.let { match ->
            val placeholder = match.groupValues[1]
            return ExtractedLocator(
                type = "getByPlaceholder",
                value = placeholder,
                cssSelector = "[placeholder=\"$placeholder\"]"
            )
        }

        // For chained locators like page.locator('form').locator('input'), find the last one
        val locatorMatches = LOCATOR_PATTERN.findAll(line).toList()
        if (locatorMatches.isNotEmpty()) {
            val lastMatch = locatorMatches.last()
            val selector = lastMatch.groupValues[1]
            return ExtractedLocator(
                type = "locator",
                value = selector,
                cssSelector = selector
            )
        }

        return null
    }
}

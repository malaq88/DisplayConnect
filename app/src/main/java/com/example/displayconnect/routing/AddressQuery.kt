package com.example.displayconnect.routing

object AddressQuery {
    fun normalize(value: String): String {
        val clean = value.trim().replace(Regex("\\s+"), " ")
        return clean.replace(Regex("^(R\\.|Av\\.?|Tv\\.?|Rod\\.?)\\s+", RegexOption.IGNORE_CASE)) {
            when (it.groupValues[1].lowercase().trimEnd('.')) {
                "r" -> "Rua "
                "av" -> "Avenida "
                "tv" -> "Travessa "
                else -> "Rodovia "
            }
        }
    }
    fun withCity(address: String, city: String): String =
        listOf(normalize(address), city.trim()).filter { it.isNotBlank() }.joinToString(", ")
    // Only explicitly separated house numbers. Preserve Rua 25 de Marco and BR-101.
    private val houseNumber = Regex("(?:,\\s*|\\s+n[º°.]?\\s*)(\\d+[a-zA-Z]?)(?=\\s*(?:,| - |$))", RegexOption.IGNORE_CASE)
    fun requestedHouseNumber(address: String): String? = houseNumber.find(address)?.groupValues?.get(1)
    fun withoutHouseNumber(address: String): String = normalize(address.replace(houseNumber, "").trim(' ', ','))
}

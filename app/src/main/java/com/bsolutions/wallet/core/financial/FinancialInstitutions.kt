package com.bsolutions.wallet.core.financial

data class FinancialInstitution(val id: String, val countryCode: String, val name: String)
data class FinancialCountry(val code: String, val name: String)

/** Catálogo local, offline y sin coste. No habilita sincronización bancaria. */
object FinancialInstitutions {
    const val DOMINICAN_REPUBLIC = "DO"
    const val OTHER_COUNTRY = "OTHER"

    val supportedCountries = listOf(
        FinancialCountry(DOMINICAN_REPUBLIC, "República Dominicana"),
        FinancialCountry(OTHER_COUNTRY, "Otro país")
    )
    val dominicanRepublic = listOf(
        "Banreservas", "Banco Popular Dominicano", "Banco BHD", "Qik Banco Digital", "Scotiabank República Dominicana",
        "Banco Santa Cruz", "Banco Caribe", "Banco Promerica", "Banco Lafise", "Banco Vimenca",
        "Banco Ademi", "APAP", "Asociación Cibao", "Asociación La Nacional"
    ).map { name -> FinancialInstitution("do_${name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')}", DOMINICAN_REPUBLIC, name) }

    fun forCountry(countryCode: String): List<FinancialInstitution> =
        if (countryCode == DOMINICAN_REPUBLIC) dominicanRepublic else emptyList()

    fun countryName(countryCode: String): String =
        supportedCountries.firstOrNull { it.code == countryCode }?.name ?: "Otro país"
}

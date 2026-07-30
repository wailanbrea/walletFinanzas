package com.bsolutions.wallet.core.financial

data class FinancialInstitution(
    val id: String,
    val countryCode: String,
    val name: String,
    val officialDomain: String? = null,
    /** Ruta del logo publicado en la ficha oficial de la SB (ej. /media/vkinapnx/santa-cruz.svg). */
    val logoPath: String? = null
) {
    /**
     * Logo oficial servido por la Superintendencia de Bancos. Se prefiere al favicon del
     * dominio: es el activo que la propia entidad registra ante el regulador, no depende
     * de proveedores comerciales (Google/Clearbit) y la solicitud no incluye datos del usuario.
     * La primera descarga sí llega al servidor de la SB; las siguientes usan caché local.
     * Null cuando la ficha no publica activo: la UI cae al monograma local.
     */
    val logoUrl: String?
        get() = logoPath?.let { "$SUPERINTENDENCIA_BASE_URL$it" }

    companion object {
        const val SUPERINTENDENCIA_BASE_URL = "https://sb.gob.do"
    }
}
data class FinancialCountry(val code: String, val name: String)

/** Catálogo local, offline y sin coste. No habilita sincronización bancaria. */
object FinancialInstitutions {
    const val DOMINICAN_REPUBLIC = "DO"
    const val OTHER_COUNTRY = "OTHER"

    val supportedCountries = listOf(
        FinancialCountry(DOMINICAN_REPUBLIC, "República Dominicana"),
        FinancialCountry(OTHER_COUNTRY, "Otro país")
    )
    /**
     * Entidades autorizadas según el resumen oficial de la SB a junio de 2025.
     * Cada `logoPath` sale de la ficha pública de la entidad en sb.gob.do (verificado
     * HTTP 200 el 23/07/2026). La SB publica SVG para la mayoría y PNG/JPG para el resto.
     */
    val dominicanRepublic = listOf(
        institution("banreservas", "Banreservas", "banreservas.com", "/media/dlghc233/banreservas.svg"),
        institution("popular", "Banco Popular Dominicano", "popularenlinea.com", "/media/ojai4exw/popular.svg"),
        institution("bhd", "Banco BHD", "bhd.com.do", "/media/lddpcf23/bhd.svg"),
        institution("santa_cruz", "Banco Santa Cruz", "bsc.com.do", "/media/vkinapnx/santa-cruz.svg"),
        institution("scotiabank", "Scotiabank República Dominicana", "do.scotiabank.com", "/media/mpicp2lo/scotiabank_logo.png"),
        institution("promerica", "Banco Promerica", "promerica.com.do", "/media/a5mpuapi/promerica.svg"),
        institution("caribe", "Banco Caribe", "bancocaribe.com.do", "/media/b3jncnch/caribe.svg"),
        institution("banesco", "Banesco", "banesco.com.do", "/media/yt0hnnwr/banesco.svg"),
        institution("bdi", "Banco BDI", "bdi.com.do", "/media/4cdo0p0a/bdi.svg"),
        institution("lopez_de_haro", "Banco López de Haro", "blh.com.do", "/media/f4gfpduz/lopez-de-haro.svg"),
        institution("vimenca", "Banco Vimenca", "bancovimenca.com", "/media/t5fblovo/microsoftteams-image-53.png"),
        institution("ademi", "Banco Ademi", "bancoademi.com.do", "/media/owjfoake/logo-ademi-final-editable-01.png"),
        institution("citibank", "Citibank", "citibank.com", "/media/fafpqhvl/citibank.svg"),
        institution("lafise", "Banco Lafise", "lafise.com", "/media/32mhtesy/lafise.svg"),
        institution("qik", "Qik Banco Digital", "qik.do", "/media/hoajdf31/qik_logo.svg"),
        institution("jmmb", "JMMB Bank", "jmmb.com.do", "/media/2odbx0oi/jmmb-bank-logo-regulatory-01.png"),
        institution("agricola", "Banco Agrícola", "bagricola.gob.do", "/media/4zbjynos/banco-agricola.jpg"),
        institution("bandex", "BANDEX", "bandex.com.do", "/media/q40fbyvw/logo-bandex.jpg"),
        institution("apap", "Asociación Popular de Ahorros y Préstamos", "apap.com.do", "/media/xebc1hje/apap.svg"),
        institution("cibao", "Asociación Cibao de Ahorros y Préstamos", "acap.com.do", "/media/n5jhag10/icono-t-fc.png"),
        institution("la_nacional", "Asociación La Nacional de Ahorros y Préstamos", "alnap.com.do", "/media/0w5hebbc/alnap.svg"),
        institution("alaver", "Asociación La Vega Real (ALAVER)", "alaver.com.do", "/media/aujdisxr/alaver.svg"),
        institution("duarte", "Asociación Duarte de Ahorros y Préstamos", "aduarte.com.do", "/media/si4h3stt/duarte.svg"),
        institution("mocana", "Asociación Mocana de Ahorros y Préstamos", "asomocana.com.do", "/media/brrhwxvm/mocana.svg"),
        institution("abonap", "Asociación Bonao de Ahorros y Préstamos (ABONAP)", "abonap.com.do", "/media/14wd5yzq/abonap.svg"),
        institution("romana", "Asociación Romana de Ahorros y Préstamos", "asociacionromana.com.do", "/media/coknuedw/logo-arap-2023-01.png"),
        institution("peravia", "Asociación Peravia de Ahorros y Préstamos", "asociacionperavia.com.do", "/media/inahugsg/logo-peravia.png"),
        institution("maguana", "Asociación Maguana de Ahorros y Préstamos", null, "/media/oq5bxcwo/maguana.svg")
    )

    fun findByName(name: String?): FinancialInstitution? {
        val canonicalName = when (name) {
            "APAP" -> "Asociación Popular de Ahorros y Préstamos"
            "Asociación Cibao" -> "Asociación Cibao de Ahorros y Préstamos"
            "Asociación La Nacional" -> "Asociación La Nacional de Ahorros y Préstamos"
            else -> name
        }
        return dominicanRepublic.firstOrNull { it.name == canonicalName }
    }

    fun forCountry(countryCode: String): List<FinancialInstitution> =
        if (countryCode == DOMINICAN_REPUBLIC) dominicanRepublic else emptyList()

    fun countryName(countryCode: String): String =
        supportedCountries.firstOrNull { it.code == countryCode }?.name ?: "Otro país"

    private fun institution(id: String, name: String, domain: String?, logoPath: String? = null) =
        FinancialInstitution("do_$id", DOMINICAN_REPUBLIC, name, domain, logoPath)
}

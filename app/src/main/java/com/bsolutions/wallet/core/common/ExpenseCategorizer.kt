package com.bsolutions.wallet.core.common

import com.bsolutions.wallet.domain.model.Category
import java.text.Normalizer

/** Catálogo inicial con ids deterministas para que el sembrado sea idempotente. */
object DefaultCategories {
    data class Seed(
        val id: String,
        val name: String,
        val icon: String,
        val colorHex: String,
        /** "EXPENSE", "INCOME" o "BOTH". */
        val type: String = "EXPENSE"
    )

    val seeds: List<Seed> = listOf(
        Seed("cat_alimentacion", "Alimentación", "restaurant", "#E57373"),
        Seed("cat_supermercado", "Supermercado", "shopping_bag", "#43A047"),
        Seed("cat_merienda", "Merienda", "fastfood", "#FF8A65"),
        Seed("cat_restaurantes", "Restaurantes", "restaurant_menu", "#EF5350"),
        Seed("cat_transporte", "Transporte", "directions_car", "#64B5F6"),
        Seed("cat_servicios", "Servicios", "payments", "#FFB74D"),
        Seed("cat_salud", "Salud", "local_hospital", "#4DB6AC"),
        Seed("cat_entretenimiento", "Entretenimiento", "movie", "#BA68C8"),
        Seed("cat_compras", "Compras", "shopping_cart", "#F06292"),
        Seed("cat_vivienda", "Vivienda", "home", "#A1887F"),
        Seed("cat_educacion", "Educación", "school", "#7986CB"),
        Seed("cat_mascotas", "Mascotas", "pets", "#8D6E63"),
        Seed("cat_viajes", "Viajes", "flight", "#4FC3F7"),
        Seed("cat_deporte", "Deporte", "fitness_center", "#66BB6A"),
        Seed("cat_regalos", "Regalos", "card_giftcard", "#F48FB1"),
        Seed("cat_salario", "Salario", "payments", "#81C784", "INCOME"),
        // "BOTH" a proposito: el dinero sale al prestarlo y vuelve al cobrarlo, y con la
        // misma etiqueta en las dos patas se netea en vez de inflar gastos e ingresos.
        Seed("cat_prestamos_terceros", "Préstamos a terceros", "payments", "#9575CD", "BOTH"),
        // "BOTH" porque una transferencia sale de una cuenta y entra en otra: las dos
        // patas llevan la misma etiqueta y se anulan entre si.
        Seed("cat_transferencias", "Transferencias", "payments", "#78909C", "BOTH"),
        Seed("cat_otros", "Otros", "card_giftcard", "#90A4AE", "BOTH"),
    )

    fun asCategories(): List<Category> = seeds.map { Category(it.id, it.name, it.icon, it.colorHex, it.type) }
}

/**
 * Autocategorización local. Las reglas del usuario tienen prioridad y las reglas
 * integradas apuntan a ids estables, por lo que renombrar una categoría no rompe
 * la inferencia.
 */
object ExpenseCategorizer {
    private val rules: List<Pair<Regex, String>> = listOf(
        Regex("merienda|picadera|pastelito|pastelon|empanada|frito|pica pollo|chimi|yaniqueque|snack|dulce|helado|chocolate|galleta|refresco|jugo|cafe|batida|frappe|kipe|quipe") to "cat_merienda",
        Regex("restaur|pizz|burger|hamburgues|almuerzo|cena|desayuno|brunch|buffet|comedor|wendy|mcdonald|kfc|domino|papa john|taco|sushi|parrilla|adrian tropical") to "cat_restaurantes",
        Regex("veterinari|mascota|perro|gato|petshop|pet shop|purina") to "cat_mascotas",
        Regex("vuelo|hotel|airbnb|pasaje|aeropuerto|arajet|jetblue|resort|excursion|viaje") to "cat_viajes",
        Regex("gym|gimnasio|crossfit|pesas|futbol|beisbol|basket|padel|tenis|deporte|entrenamiento") to "cat_deporte",
        Regex("regalo|cumpleanos|aniversario|boda|detalle") to "cat_regalos",
        Regex("supermerc|colmado|nacional|jumbo|sirena|pola|bravo|super |grocer|market|plaza lama|carniceria|panaderia|despensa|compra del mes") to "cat_supermercado",
        Regex("aliment|comida") to "cat_alimentacion",
        Regex("uber|taxi|didi|indriver|gasolina|combustible|peaje|parqueo|metro|guagua|omsa|transport|goma|mecanic|bonogas") to "cat_transporte",
        Regex("farmacia|carol|gbc|clinica|hospital|medic|dentista|laboratorio|salud|consulta|vacuna|pastilla|seguro medico|ars ") to "cat_salud",
        Regex("netflix|spotify|hbo|disney|cine|caribbean|entreten|juego|steam|concierto|teatro|bar |cerveza|discoteca|billar|karaoke") to "cat_entretenimiento",
        Regex("edeeste|edenorte|edesur|luz|agua|caasd|internet|claro|altice|viva|wind|factura|servicio|telefon|cable|propano") to "cat_servicios",
        // Los couriers de RD: lo que se paga ahi es el envio de una compra por internet,
        // asi que cae del mismo lado que la compra. Si prefieres que vaya a tu propia
        // categoria (Amazon, por ejemplo), una regla tuya en Reglas de categorias manda
        // sobre esta: las propias se miran antes que las integradas.
        Regex("boxpaq|aeropaq|epaq|bluexpress|jetbox|caribe express|mailbox|courier|currier|paqueteria") to "cat_compras",
        Regex("zara|ropa|tienda|shopping|amazon|shein|temu|mall|zapato|calzado|jean|ikea|electrodomestico|celular") to "cat_compras",
        Regex("alquiler|renta|hipoteca|vivienda|condominio") to "cat_vivienda",
        Regex("colegio|universidad|educac|curso|libro|matricula|inscripcion|profesor") to "cat_educacion",
        Regex("salario|sueldo|nomina|pago de|deposito de|honorario|quincena") to "cat_salario",
    )

    /** Nombre visible integrado inferido del texto, conservado por compatibilidad. */
    fun infer(text: String): String? {
        val categoryId = inferCategoryId(text) ?: return null
        return DefaultCategories.seeds.firstOrNull { it.id == categoryId }?.name
    }

    /** Id estable de una categoría integrada, o null si no hay coincidencia. */
    fun inferCategoryId(text: String): String? {
        val normalized = normalizeText(text)
        if (normalized.isBlank()) return null
        return rules.firstOrNull { it.first.containsMatchIn(normalized) }?.second
    }

    /**
     * Resuelve primero una regla personalizada válida y luego una integrada activa.
     * Una regla que apunta a una categoría eliminada se ignora de forma segura.
     */
    fun categoryIdFor(
        text: String,
        categories: List<Category>,
        customRules: List<CustomCategoryRule> = emptyList()
    ): String? {
        val normalized = normalizeText(text)
        if (normalized.isBlank()) return null

        customRules.firstOrNull { rule ->
            val keyword = normalizeText(rule.keyword)
            keyword.isNotBlank() && normalized.contains(keyword) &&
                categories.any { it.id == rule.categoryId }
        }?.let { return it.categoryId }

        val builtInId = inferCategoryId(normalized) ?: return null
        return categories.firstOrNull { it.id == builtInId }?.id
    }

    fun normalizeText(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .trim()
}

/** Categorías virtuales para referencias vacías o eliminadas. Nunca se persisten. */
object CategoryPlaceholders {
    const val UNCATEGORIZED_ID = "__uncategorized__"

    fun uncategorized(): Category = Category(
        id = UNCATEGORIZED_ID,
        name = "Sin categoría",
        icon = "category",
        colorHex = "#90A4AE"
    )

    fun deleted(originalId: String): Category = Category(
        id = originalId,
        name = "Categoría eliminada",
        icon = "category",
        colorHex = "#90A4AE"
    )

    fun aggregateId(categoryId: String, activeCategories: Map<String, Category>): String =
        categoryId.takeIf(activeCategories::containsKey) ?: UNCATEGORIZED_ID
}

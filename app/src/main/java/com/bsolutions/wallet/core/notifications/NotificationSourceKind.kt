package com.bsolutions.wallet.core.notifications

/**
 * De que tipo es la app que notifica.
 *
 * La lista de fuentes trae todo lo que ha notificado alguna vez —el clima, la tienda de
 * apps, los mensajes— y encontrar el banco ahi dentro es buscar entre veinte. Esto la
 * parte en las dos que importan.
 */
enum class NotificationSourceKind {
    /** Apps de banco, tarjeta o pago. */
    BANK,

    /** Clientes de correo: por ahi llegan los avisos que el banco manda por email. */
    EMAIL,

    OTHER
}

/**
 * Se decide por el nombre del paquete y no por una lista cerrada de bancos.
 *
 * Una lista cerrada deja fuera al banco que no este apuntado, y en RD hay muchos: el
 * nombre del paquete casi siempre lleva el del banco o la palabra que lo delata. Es una
 * pista, no una garantia, y por eso solo sirve para ordenar la lista: quien decide que se
 * autoriza sigue siendo el usuario.
 */
fun notificationSourceKind(packageName: String): NotificationSourceKind {
    val name = packageName.lowercase()

    val email = listOf(
        "com.google.android.gm",
        "com.microsoft.office.outlook",
        "com.yahoo.mobile.client.android.mail",
        "com.samsung.android.email",
        "ch.protonmail",
        "com.fsck.k9",
        "mail"
    )
    if (email.any { name.contains(it) }) return NotificationSourceKind.EMAIL

    val bank = listOf(
        "banreservas", "popular", "bhd", "scotiabank", "santacruz", "banesco",
        "promerica", "lafise", "caribe", "vimenca", "ademi", "bancamerica",
        "qik", "tpago", "azul", "cardnet", "wallet", "bank", "banco", "visa",
        "mastercard", "paypal", "remesa", "credito"
    )
    if (bank.any { name.contains(it) }) return NotificationSourceKind.BANK

    return NotificationSourceKind.OTHER
}

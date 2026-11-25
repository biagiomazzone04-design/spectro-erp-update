package com.spectro.app.data

import kotlin.math.round

/**
 * Catalogo iniziale ricostruito dal listino "COSTI ZHENTA 13-10-26".
 * I codici sono conservati esattamente come nel documento di origine.
 */
object ZenthaCatalog {
    const val SOURCE = "COSTI ZHENTA 13-10-2026"
    const val PRICE_LIST = "Listino Zentha 47%"
    const val MARKUP_PERCENT = 47.0

    val products: List<ZenthaProduct> = listOf(
        product("01VGMD INS DONNA", "Maglia donna GM inserti", "Maglia", "MAGLIA DONNA GM INSERTI LISCIO FINDITEX + POLY MESH + DORIA", 1.02, .25, .13, 1.67, 0.0, .40, 2.80, .03, .04, 6.34, 9.32),
        product("02BGM DOUBLE UOMO", "Maglia uomo GM Double", "Maglia", "MAGLIA UOMO GM DOUBLE POLYMESH FINDITEX", 2.02, 0.0, 0.0, 3.35, 0.0, .40, 4.10, .03, .04, 9.94, 14.61),
        product("02BGM UOMO", "Maglia uomo GM", "Maglia", "MAGLIA UOMO GM POLYMESH FINDITEX + COSTA DORIA", 1.01, .19, 0.0, 1.67, 0.0, .40, 2.30, .03, .04, 5.64, 8.29),
        product("02BASKE PCU UOMO", "Pantaloncino uomo basket", "Pantaloncino", "PANTALONCINO UOMO BASKET POLYMESH FINDITEX", .90, 0.0, 0.0, 1.85, .80, .40, 2.80, .03, .04, 6.82, 10.03),
        product("02BASKE PCU BABY", "Pantaloncino baby basket", "Pantaloncino", "PANT BABY BASKET POLYMESH FINDITEX", .78, 0.0, 0.0, 1.50, .80, .40, 2.80, .03, .04, 6.35, 9.33)
            .copy(sourceReference = "Listino fornito il 25/11/2026 · valore sorgente 0,065 (unità non indicata)"),
        product("01PCDO DONNA UNITO", "Pantaloncino donna unito", "Pantaloncino", "PANTALONCINO CORTO DONNA ELAST DANZA CTA UNITO", 1.15, 0.0, 0.0, .90, .50, .40, 3.80, .03, .04, 6.82, 10.03),
        product("01PCDO DONNA", "Pantaloncino donna sublimato", "Pantaloncino", "PANTALONCINO CORTO DONNA ELAST DANZA CTA SUBLY", 1.15, 0.0, 0.0, 1.85, .50, .40, 3.80, .03, .04, 7.77, 11.42),
        product("03VBM PIEGA BABY", "Maglia baby calcio/volley", "Maglia", "MAGLIA CALCIO/VOLLEY MC/BORDI 100% POLIESTERE", .76, .13, 0.0, 1.70, 0.0, .40, 2.30, .03, .04, 5.36, 7.88),
        product("03VPC BABY", "Pantaloncino baby volley/calcio", "Pantaloncino", "PANTALONCINO CORTO VOLLEY/CALCIO 100% POLIESTERE", .65, 0.0, 0.0, 1.50, 0.0, .40, 2.80, .03, .04, 5.42, 7.97),
        product("03VBM PIEGA UOMO", "Maglia uomo calcio/volley", "Maglia", "MAGLIA CALCIO/VOLLEY PIEGHE 100% POLIESTERE", 1.05, .13, 0.0, 2.50, 0.0, .40, 2.30, .03, .04, 6.45, 9.48),
        product("03VPC UOMO", "Pantaloncino uomo volley/calcio", "Pantaloncino", "PANTALONCINO CORTO VOLLEY/CALCIO 100% POLIESTERE", .86, 0.0, 0.0, 1.62, 0.0, .40, 2.80, .03, .04, 5.75, 8.45),
        product("01 VGMD INS BABY", "Maglia baby GM inserti", "Maglia", "MAGLIA BABY GM INSERTI LISCIO FINDITEX + POLY MESH + DORIA", .77, .25, .13, 1.40, 0.0, .40, 2.80, .03, .04, 5.82, 8.56),
        product("02BGM DOUBLE BABY", "Maglia baby GM Double", "Maglia", "MAGLIA BABY GM DOUBLE POLYMESH FINDITEX", 1.23, 0.0, 0.0, 2.70, 0.0, .40, 4.10, .03, .04, 8.50, 12.50),
        product("01PCDO BABY UNITO", "Pantaloncino baby unito", "Pantaloncino", "PANTALONCINO CORTO BABY ELAST DANZA CTA UNITO", .71, 0.0, 0.0, .90, .50, .40, 3.80, .03, .04, 6.38, 9.38),
        product("01PCDO BABY", "Pantaloncino baby sublimato", "Pantaloncino", "PANTALONCINO CORTO BABY ELAST DANZA CTA SUBLY", .71, 0.0, 0.0, 1.40, .50, .40, 3.80, .03, .04, 6.88, 10.11)
    )

    private fun product(
        sku: String,
        name: String,
        category: String,
        description: String,
        fabric: Double,
        rib: Double,
        lining: Double,
        print: Double,
        accessories: Double,
        cutting: Double,
        sewing: Double,
        label: Double,
        packaging: Double,
        total: Double,
        price: Double
    ) = ZenthaProduct(
        name = name,
        sku = sku,
        category = category,
        standardCost = total,
        standardPrice = price,
        description = description,
        fabricCost = fabric,
        ribCost = rib,
        liningCost = lining,
        printCost = print,
        accessoryCost = accessories,
        cuttingCost = cutting,
        sewingCost = sewing,
        labelCost = label,
        packagingCost = packaging,
        priceListName = PRICE_LIST,
        markupPercent = MARKUP_PERCENT,
        sourceReference = SOURCE
    )

    fun roundMoney(value: Double): Double = round(value * 100.0) / 100.0
}

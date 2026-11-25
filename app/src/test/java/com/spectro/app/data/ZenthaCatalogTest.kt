package com.spectro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenthaCatalogTest {
    @Test
    fun importedCatalogContainsFifteenUniqueArticleCodes() {
        assertEquals(15, ZenthaCatalog.products.size)
        assertEquals(
            ZenthaCatalog.products.size,
            ZenthaCatalog.products.map { it.sku.trim().uppercase() }.distinct().size
        )
        assertTrue(ZenthaCatalog.products.all { it.sku.isNotBlank() && it.name.isNotBlank() })
    }

    @Test
    fun babyBasketShortsHaveTheSuppliedCostAndPrice() {
        val product = ZenthaCatalog.products.single { it.sku == "02BASKE PCU BABY" }
        assertEquals(6.35, product.standardCost, 0.001)
        assertEquals(9.33, product.standardPrice, 0.001)
    }

    @Test
    fun importedAnalyticalCostsMatchDeclaredTotals() {
        ZenthaCatalog.products.forEach { product ->
            assertTrue("Manca il dettaglio costi per ${product.sku}", product.hasCostBreakdown)
            assertEquals("Totale costo non coerente per ${product.sku}", product.standardCost, ZenthaCatalog.roundMoney(product.costBreakdownTotal), 0.001)
            assertEquals(ZenthaCatalog.PRICE_LIST, product.priceListName)
            assertEquals(ZenthaCatalog.MARKUP_PERCENT, product.markupPercent, 0.001)
        }
    }

    @Test
    fun sourcePricesUseFortySevenPercentMarkupRoundedToCents() {
        ZenthaCatalog.products.forEach { product ->
            assertEquals(
                "Prezzo listino non coerente per ${product.sku}",
                product.standardPrice,
                ZenthaCatalog.roundMoney(product.standardCost * 1.47),
                0.001
            )
        }
    }

    @Test
    fun productionExportCannotExposeEconomicData() {
        val production = ZenthaExportProfile.PRODUCTION
        assertFalse(production.showSalePrices)
        assertFalse(production.showInternalCosts)
        assertFalse(production.showPayments)
        assertTrue(production.showProductionDetails)

        assertTrue(ZenthaExportProfile.ACCOUNTING.showSalePrices)
        assertFalse(ZenthaExportProfile.ACCOUNTING.showInternalCosts)
        assertTrue(ZenthaExportProfile.COMPLETE.showInternalCosts)
    }
}

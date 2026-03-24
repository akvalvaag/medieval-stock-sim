package com.medievalmarket.service;

import com.medievalmarket.model.Good;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GoodsCatalogueTest {

    @Test
    void catalogueHas29Goods() {
        GoodsCatalogue catalogue = new GoodsCatalogue();
        assertThat(catalogue.getGoods()).hasSize(29);
    }

    @Test
    void allGoodsHavePositiveBasePrice() {
        GoodsCatalogue catalogue = new GoodsCatalogue();
        for (Good good : catalogue.getGoods()) {
            assertThat(good.getBasePrice()).isGreaterThan(0);
        }
    }

    @Test
    void miningCategoryHasEightGoods() {
        GoodsCatalogue catalogue = new GoodsCatalogue();
        long count = catalogue.getGoods().stream()
            .filter(g -> "Mining".equals(g.getCategory()))
            .count();
        assertThat(count).isEqualTo(8);
    }

    @Test
    void agricultureCategoryHasElevenGoods() {
        GoodsCatalogue catalogue = new GoodsCatalogue();
        long count = catalogue.getGoods().stream()
            .filter(g -> "Agriculture".equals(g.getCategory()))
            .count();
        assertThat(count).isEqualTo(11);
    }

    @Test
    void timberAndCraftCategoryHasTenGoods() {
        GoodsCatalogue catalogue = new GoodsCatalogue();
        long count = catalogue.getGoods().stream()
            .filter(g -> "Timber & Craft".equals(g.getCategory()))
            .count();
        assertThat(count).isEqualTo(10);
    }

    @Test
    void goodNamesAreUnique() {
        GoodsCatalogue catalogue = new GoodsCatalogue();
        long uniqueNames = catalogue.getGoods().stream()
            .map(Good::getName).distinct().count();
        assertThat(uniqueNames).isEqualTo(29);
    }
}

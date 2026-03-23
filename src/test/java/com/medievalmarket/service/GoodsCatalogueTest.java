package com.medievalmarket.service;

import com.medievalmarket.model.Good;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GoodsCatalogueTest {

    @Test
    void catalogueHas15Goods() {
        GoodsCatalogue catalogue = new GoodsCatalogue();
        assertThat(catalogue.getGoods()).hasSize(15);
    }

    @Test
    void allGoodsHavePositiveBasePrice() {
        GoodsCatalogue catalogue = new GoodsCatalogue();
        for (Good good : catalogue.getGoods()) {
            assertThat(good.getBasePrice()).isGreaterThan(0);
        }
    }

    @Test
    void miningCategoryHasFiveGoods() {
        GoodsCatalogue catalogue = new GoodsCatalogue();
        long count = catalogue.getGoods().stream()
            .filter(g -> "Mining".equals(g.getCategory()))
            .count();
        assertThat(count).isEqualTo(5);
    }

    @Test
    void goodNamesAreUnique() {
        GoodsCatalogue catalogue = new GoodsCatalogue();
        long uniqueNames = catalogue.getGoods().stream()
            .map(Good::getName).distinct().count();
        assertThat(uniqueNames).isEqualTo(15);
    }
}

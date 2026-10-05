// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.linenflow;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 件数守恒和金额精度边界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class QuantityPolicyTest {
  @Test
  void inspectionMustConserveReceived() {
    var l = new BatchLine();
    l.receivedQty = 100;
    assertDoesNotThrow(() -> QuantityPolicy.inspection(l, 90, 8, 2));
    assertThrows(Problem.class, () -> QuantityPolicy.inspection(l, 90, 9, 2));
  }

  @Test
  void rewashCannotEraseGoodOrDiscardedHistory() {
    var l = new BatchLine();
    l.receivedQty = 100;
    l.goodQty = 90;
    l.discardQty = 2;
    assertDoesNotThrow(() -> QuantityPolicy.inspection(l, 98, 0, 2));
    assertThrows(Problem.class, () -> QuantityPolicy.inspection(l, 89, 9, 2));
    assertThrows(Problem.class, () -> QuantityPolicy.inspection(l, 99, 0, 1));
  }

  @Test
  void availabilityIncludesUnresolvedShipments() {
    assertEquals(30, QuantityPolicy.available(100, 60, 10));
    assertThrows(Problem.class, () -> QuantityPolicy.available(100, 60, 41));
  }

  @Test
  void countsAndMoneyAreStrict() {
    assertThrows(Problem.class, () -> QuantityPolicy.quantity(null));
    assertThrows(Problem.class, () -> QuantityPolicy.quantity(-1));
    assertThrows(Problem.class, () -> QuantityPolicy.quantity(1000001));
    assertEquals(new BigDecimal("2.50"), CatalogService.money(new BigDecimal("2.5")));
    assertThrows(Problem.class, () -> CatalogService.money(new BigDecimal("1.001")));
    assertThrows(Problem.class, () -> CatalogService.money(BigDecimal.ZERO));
  }
}

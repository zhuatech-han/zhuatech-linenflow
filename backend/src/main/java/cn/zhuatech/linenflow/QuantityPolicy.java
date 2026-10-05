// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.linenflow;

/** 布草数量守恒、返洗只处理未合格部分、配送不重复占用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class QuantityPolicy {
  private QuantityPolicy() {}

  /** 检查有限整数件数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static int quantity(Integer n) {
    if (n == null || n < 0 || n > 1000000) throw new Problem(400, "INVALID_QUANTITY");
    return n;
  }

  /** 每次质检均覆盖实收总量，已经合格或报损的数量不可减少。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void inspection(BatchLine line, int good, int rewash, int discard) {
    quantity(good);
    quantity(rewash);
    quantity(discard);
    if ((long) good + rewash + discard != line.receivedQty
        || good < line.goodQty
        || discard < line.discardQty) throw new Problem(400, "QUANTITY_BALANCE");
  }

  /** 剩余可发出量扣除所有未解决交接的预留，防止超发。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static int available(int good, int signed, int reserved) {
    int n = good - signed - reserved;
    if (n < 0) throw new Problem(409, "QUANTITY_BALANCE");
    return n;
  }
}

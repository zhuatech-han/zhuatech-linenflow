// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.linenflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/**
 * 送洗、实收、质检和签收数量及冻结价格。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech /
 * zhuatech2
 */
@Entity
@Table(name = "batch_line")
public class BatchLine {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "batch_id", nullable = false)
  public Long batchId;

  @Column(name = "item_id", nullable = false)
  public Long itemId;

  @Column(name = "item_name", nullable = false, length = 120)
  public String itemName;

  @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
  public BigDecimal unitPrice;

  @Column(name = "price_reference", nullable = false, length = 200)
  public String priceReference;

  @Column(name = "declared_qty", nullable = false)
  public int declaredQty;

  @Column(name = "received_qty", nullable = false)
  public int receivedQty;

  @Column(name = "good_qty", nullable = false)
  public int goodQty;

  @Column(name = "rewash_qty", nullable = false)
  public int rewashQty;

  @Column(name = "discard_qty", nullable = false)
  public int discardQty;

  @Column(name = "signed_qty", nullable = false)
  public int signedQty;
}

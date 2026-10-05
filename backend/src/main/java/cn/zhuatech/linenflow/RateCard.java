// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.linenflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/**
 * 酒店布草约定洗涤单价；送洗提交时冻结快照。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech
 * / zhuatech2
 */
@Entity
@Table(name = "rate_card")
public class RateCard {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "customer_id", nullable = false)
  public Long customerId;

  @Column(name = "item_id", nullable = false)
  public Long itemId;

  @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
  public BigDecimal unitPrice;

  @Column(name = "reference", nullable = false, length = 200)
  public String reference;

  @Column(name = "enabled", nullable = false)
  public boolean enabled;
}

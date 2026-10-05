// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.linenflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/**
 * 已发生线下收款及独立冲正记录。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech /
 * zhuatech2
 */
@Entity
@Table(name = "payment")
public class Payment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "invoice_id", nullable = false)
  public Long invoiceId;

  @Column(name = "amount", nullable = false, precision = 16, scale = 2)
  public BigDecimal amount;

  @Column(name = "reference", nullable = false, length = 200)
  public String reference;

  @Column(name = "actor_id", nullable = false)
  public Long actorId;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "reversed_by", nullable = true)
  public Long reversedBy;

  @Column(name = "reversal_reference", nullable = true, length = 200)
  public String reversalReference;

  @Column(name = "reversal_note", nullable = true, length = 1000)
  public String reversalNote;
}

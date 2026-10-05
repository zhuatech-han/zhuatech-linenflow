// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.linenflow;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** 对账单批次关联及金额快照，取消后仍保留历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "invoice_batch")
public class InvoiceBatch {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "invoice_id", nullable = false)
  public Long invoiceId;

  @Column(name = "batch_id", nullable = false)
  public Long batchId;

  @Column(name = "amount", nullable = false, precision = 16, scale = 2)
  public BigDecimal amount;
}

// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.linenflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/**
 * 按完成月份汇集洗涤费用的客户对账单。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech /
 * zhuatech2
 */
@Entity
@Table(name = "monthly_invoice")
public class MonthlyInvoice {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "code", nullable = false, length = 80)
  public String code;

  @Column(name = "customer_id", nullable = false)
  public Long customerId;

  @Column(name = "customer_name", nullable = false, length = 120)
  public String customerName;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "period", nullable = false, length = 7)
  public String period;

  @Column(name = "status", nullable = false, length = 30)
  public String status;

  @Column(name = "version", nullable = false)
  public long version;

  @Column(name = "amount", nullable = false, precision = 16, scale = 2)
  public BigDecimal amount;

  @Column(name = "note", nullable = false, length = 1000)
  public String note;

  @Column(name = "author_id", nullable = false)
  public Long authorId;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}

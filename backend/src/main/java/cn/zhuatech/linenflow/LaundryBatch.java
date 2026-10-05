// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.linenflow;

import jakarta.persistence.*;
import java.time.*;

/**
 * 一份客户自有布草送洗单及处理状态。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech /
 * zhuatech2
 */
@Entity
@Table(name = "laundry_batch")
public class LaundryBatch {
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

  @Column(name = "author_id", nullable = false)
  public Long authorId;

  @Column(name = "service_date", nullable = false)
  public LocalDate serviceDate;

  @Column(name = "note", nullable = false, length = 1000)
  public String note;

  @Column(name = "status", nullable = false, length = 30)
  public String status;

  @Column(name = "version", nullable = false)
  public long version;

  @Column(name = "processor_id", nullable = true)
  public Long processorId;

  @Column(name = "count_note", nullable = false, length = 1000)
  public String countNote;

  @Column(name = "quality_note", nullable = false, length = 1000)
  public String qualityNote;

  @Column(name = "completed_at", nullable = true)
  public Instant completedAt;

  @Column(name = "invoice_id", nullable = true)
  public Long invoiceId;
}

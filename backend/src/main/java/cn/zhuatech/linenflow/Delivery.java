// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.linenflow;

import jakarta.persistence.*;
import java.time.*;

/**
 * 分批配送和拒收实物退回核验。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech /
 * zhuatech2
 */
@Entity
@Table(name = "delivery")
public class Delivery {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "batch_id", nullable = false)
  public Long batchId;

  @Column(name = "dispatcher_id", nullable = false)
  public Long dispatcherId;

  @Column(name = "reference", nullable = false, length = 200)
  public String reference;

  @Column(name = "status", nullable = false, length = 30)
  public String status;

  @Column(name = "note", nullable = false, length = 1000)
  public String note;

  @Column(name = "signer_id", nullable = true)
  public Long signerId;

  @Column(name = "returner_id", nullable = true)
  public Long returnerId;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}

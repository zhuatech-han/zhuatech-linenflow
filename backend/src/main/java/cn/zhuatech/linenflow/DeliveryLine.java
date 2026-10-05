// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.linenflow;

import jakarta.persistence.*;
import java.time.*;

/**
 * 每笔配送的发出和客户实际签收数量。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech /
 * zhuatech2
 */
@Entity
@Table(name = "delivery_line")
public class DeliveryLine {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "delivery_id", nullable = false)
  public Long deliveryId;

  @Column(name = "batch_line_id", nullable = false)
  public Long batchLineId;

  @Column(name = "sent_qty", nullable = false)
  public int sentQty;

  @Column(name = "accepted_qty", nullable = false)
  public int acceptedQty;
}

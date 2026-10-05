// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.linenflow;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 只将酒店已确认完成的批次纳入对账，收款为已发生线下事实的登记。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class InvoiceService {
  final Store db;
  final AccessService access;
  final CatalogService catalog;
  final LaundryService laundry;
  final Clock clock;

  public InvoiceService(
      Store db, AccessService access, CatalogService catalog, LaundryService laundry, Clock clock) {
    this.db = db;
    this.access = access;
    this.catalog = catalog;
    this.laundry = laundry;
    this.clock = clock;
  }

  /** 创建固定客户与完成月份的账单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Input(String requestKey, Long customerId, String period, String note) {}

  /** 账单和款项命令，仅接收凭证、金额和原款项编号。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Command(
      String requestKey,
      Long version,
      String note,
      String reference,
      BigDecimal amount,
      Long paymentId) {}

  private boolean visible(MonthlyInvoice i) {
    var a = access.current();
    return a.customerId != null
        ? Objects.equals(a.customerId, i.customerId)
        : access.visible(i.departmentId)
            && (!access.role().scope.equals("SELF") || Objects.equals(i.authorId, a.id));
  }

  private MonthlyInvoice readable(Long id) {
    access.require("invoice.read");
    var i = db.get(MonthlyInvoice.class, id);
    if (!visible(i)) throw new Problem(403, "OUT_OF_SCOPE");
    return i;
  }

  private List<Payment> payments(Long id) {
    return db.query(Payment.class, "from Payment where invoiceId=?1 order by id", id);
  }

  private List<InvoiceBatch> batches(Long id) {
    return db.query(InvoiceBatch.class, "from InvoiceBatch where invoiceId=?1 order by id", id);
  }

  /** 原付款留存，独立冲正只将其排除于净收款。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public BigDecimal paid(Long id) {
    return payments(id).stream()
        .filter(p -> p.reversedBy == null)
        .map(p -> p.amount)
        .reduce(new BigDecimal("0.00"), BigDecimal::add);
  }

  /** 本人或部门范围内的账单目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public List<MonthlyInvoice> visibleInvoices() {
    access.require("invoice.read");
    return db.all(MonthlyInvoice.class).stream().filter(this::visible).toList();
  }

  /** 账单全文及逐批次行价，业务授权由账单所属客户确定。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object detail(Long id) {
    var i = readable(id);
    var rows = new ArrayList<Object>();
    for (var link : batches(id)) {
      var b = db.get(LaundryBatch.class, link.batchId);
      rows.add(Map.of("record", b, "lines", laundry.lines(b.id), "amount", link.amount));
    }
    return Map.of(
        "record",
        i,
        "batches",
        rows,
        "payments",
        payments(id),
        "paid",
        paid(id),
        "balance",
        i.amount.subtract(paid(id)),
        "events",
        db.query(
            BusinessEvent.class,
            "from BusinessEvent where objectType='INVOICE' and objectId=?1 order by id",
            id));
  }

  /** 范围内搜索、过滤、分页和稳定排序。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object list(String search, String status, int page, int size, String sort) {
    LaundryService.validateList(search, page, size, sort);
    if (!status.isEmpty()
        && !Set.of("DRAFT", "ISSUED", "DISPUTED", "CONFIRMED", "PAID", "CANCELLED")
            .contains(status)) throw new Problem(400, "INVALID_STATUS");
    var rows =
        new ArrayList<>(
            visibleInvoices().stream()
                .filter(
                    i ->
                        (status.isEmpty() || status.equals(i.status))
                            && (i.code + " " + i.customerName + " " + i.period)
                                .toLowerCase(Locale.ROOT)
                                .contains(search.toLowerCase(Locale.ROOT)))
                .toList());
    rows.sort(
        sort.equals("date")
            ? Comparator.comparing((MonthlyInvoice i) -> i.period).thenComparing(i -> i.id)
            : sort.equals("code")
                ? Comparator.comparing(i -> i.code)
                : Comparator.comparing((MonthlyInvoice i) -> i.id).reversed());
    int start = Math.min(rows.size(), page * size);
    return Map.of(
        "items",
        rows.subList(start, Math.min(rows.size(), start + size)),
        "total",
        rows.size(),
        "page",
        page,
        "size",
        size);
  }

  /** 生成对账草稿并锁定未计费的完成批次；按签收合格件数计费，返洗和报损不重复收费。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object create(Input v) {
    catalog.mutation();
    access.require("invoice.write");
    catalog.staff();
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    String fp = laundry.fingerprint("CREATE_INVOICE", null, v);
    var prior = laundry.retry(v.requestKey, fp);
    if (prior != null) return detail(prior);
    var c = db.get(Customer.class, v.customerId);
    if (!catalog.visible(c)) throw new Problem(403, "OUT_OF_SCOPE");
    if (v.period == null || !v.period.matches("[0-9]{4}-[0-9]{2}"))
      throw new Problem(400, "INVALID_PERIOD");
    YearMonth period;
    try {
      period = YearMonth.parse(v.period);
    } catch (java.time.format.DateTimeParseException e) {
      throw new Problem(400, "INVALID_PERIOD");
    }
    if (period.getYear() < 2000 || period.isAfter(YearMonth.from(laundry.today())))
      throw new Problem(400, "INVALID_PERIOD");
    var candidates =
        db
            .query(
                LaundryBatch.class,
                "from LaundryBatch where customerId=?1 and status='COMPLETED' and invoiceId is null order by id",
                c.id)
            .stream()
            .filter(
                b ->
                    YearMonth.from(b.completedAt.atZone(ZoneId.of("Asia/Shanghai"))).equals(period))
            .toList();
    if (candidates.isEmpty()) throw new Problem(409, "NO_ELIGIBLE_BATCHES");
    if (db.all(MonthlyInvoice.class).size() >= 1000) throw new Problem(409, "INVOICE_LIMIT");
    var i = new MonthlyInvoice();
    i.code = "LS-" + UUID.randomUUID().toString().substring(0, 18).toUpperCase(Locale.ROOT);
    i.customerId = c.id;
    i.customerName = c.name;
    i.departmentId = c.departmentId;
    i.period = v.period;
    i.note = AdminService.text(v.note, 1000);
    i.status = "DRAFT";
    i.authorId = access.current().id;
    i.createdAt = BusinessTime.now(clock);
    i.amount = new BigDecimal("0.00");
    db.save(i);
    for (var b : candidates) {
      if (!b.departmentId.equals(i.departmentId)) throw new Problem(409, "OUT_OF_SCOPE");
      var amount =
          laundry.lines(b.id).stream()
              .map(l -> l.unitPrice.multiply(BigDecimal.valueOf(l.signedQty)))
              .reduce(new BigDecimal("0.00"), BigDecimal::add);
      var link = new InvoiceBatch();
      link.invoiceId = i.id;
      link.batchId = b.id;
      link.amount = amount;
      db.save(link);
      b.invoiceId = i.id;
      b.version++;
      i.amount = i.amount.add(amount);
    }
    if (i.amount.compareTo(new BigDecimal("99999999999999.99")) > 0)
      throw new Problem(400, "INVALID_AMOUNT");
    laundry.event("INVOICE", i.id, "CREATE", i.note, i.departmentId, batches(i.id));
    laundry.remember(v.requestKey, fp, i.id);
    return detail(i.id);
  }

  /** 出账、异议、确认、线下收款和独立冲正均保护状态与余额。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object command(Long id, String action, Command v) {
    catalog.mutation();
    String p =
        switch (action) {
          case "issue", "cancel" -> "invoice.write";
          case "confirm", "dispute" -> "invoice.confirm";
          case "pay", "reverse" -> "payment.write";
          default -> throw new Problem(404, "NOT_FOUND");
        };
    access.require(p);
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    var i = readable(id);
    String fp = laundry.fingerprint("INVOICE_" + action, id, v);
    var prior = laundry.retry(v.requestKey, fp);
    if (prior != null) return detail(prior);
    if (v.version == null || v.version != i.version) throw new Problem(409, "STALE_VERSION");
    String note = AdminService.text(v.note, 1000);
    var actor = access.current();
    switch (action) {
      case "issue" -> {
        catalog.staff();
        state(i, "DRAFT");
        i.status = "ISSUED";
      }
      case "cancel" -> {
        catalog.staff();
        state(i, "DRAFT", "ISSUED", "DISPUTED");
        for (var link : batches(id)) {
          var b = db.get(LaundryBatch.class, link.batchId);
          if (Objects.equals(b.invoiceId, id)) {
            b.invoiceId = null;
            b.version++;
          }
        }
        i.status = "CANCELLED";
      }
      case "confirm" -> {
        hotel(i);
        state(i, "ISSUED");
        i.status = i.amount.signum() == 0 ? "PAID" : "CONFIRMED";
      }
      case "dispute" -> {
        hotel(i);
        state(i, "ISSUED");
        i.status = "DISPUTED";
      }
      case "pay" -> {
        catalog.staff();
        state(i, "CONFIRMED");
        if (payments(id).size() >= 200) throw new Problem(409, "PAYMENT_LIMIT");
        var amount = CatalogService.money(v.amount);
        if (amount.compareTo(i.amount.subtract(paid(id))) > 0)
          throw new Problem(409, "EXCESS_PAYMENT");
        reference(v.reference);
        var payment = new Payment();
        payment.invoiceId = id;
        payment.amount = amount;
        payment.reference = v.reference.trim();
        payment.actorId = actor.id;
        payment.createdAt = BusinessTime.now(clock);
        db.save(payment);
        if (paid(id).compareTo(i.amount) == 0) i.status = "PAID";
      }
      case "reverse" -> {
        catalog.staff();
        state(i, "CONFIRMED", "PAID");
        var payment = db.get(Payment.class, v.paymentId);
        if (!payment.invoiceId.equals(id) || payment.reversedBy != null)
          throw new Problem(409, "INVALID_PAYMENT");
        if (payment.actorId.equals(actor.id)) throw new Problem(403, "INDEPENDENT_REVERSAL");
        reference(v.reference);
        payment.reversedBy = actor.id;
        payment.reversalReference = v.reference.trim();
        payment.reversalNote = note;
        i.status = "CONFIRMED";
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    i.version++;
    i.note = note;
    laundry.event(
        "INVOICE",
        id,
        action,
        note,
        i.departmentId,
        Map.of("record", i, "paid", paid(id), "payments", payments(id)));
    laundry.remember(v.requestKey, fp, id);
    return detail(id);
  }

  private void state(MonthlyInvoice i, String... states) {
    if (!Arrays.asList(states).contains(i.status)) throw new Problem(409, "INVALID_STATE");
  }

  private void hotel(MonthlyInvoice i) {
    if (!Objects.equals(access.current().customerId, i.customerId))
      throw new Problem(403, "HOTEL_ONLY");
  }

  private void reference(String ref) {
    String value = AdminService.text(ref, 200);
    if (!db.query(Payment.class, "from Payment where reference=?1 or reversalReference=?1", value)
        .isEmpty()) throw new Problem(409, "DUPLICATE_REFERENCE");
  }
}

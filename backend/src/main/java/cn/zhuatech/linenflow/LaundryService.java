// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.linenflow;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 酒店自有布草送洗、独立质检与双边交接；所有数量更新同事务提交。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class LaundryService {
  final Store db;
  final AccessService access;
  final CatalogService catalog;
  final Clock clock;
  final ObjectMapper mapper;
  static final Set<String> STATES =
      Set.of(
          "DRAFT",
          "DECLARED",
          "COUNTED",
          "ACCEPTED",
          "WASHING",
          "REWASH",
          "READY",
          "DELIVERED",
          "COMPLETED",
          "CANCELLED");

  public LaundryService(
      Store db, AccessService access, CatalogService catalog, Clock clock, ObjectMapper mapper) {
    this.db = db;
    this.access = access;
    this.catalog = catalog;
    this.clock = clock;
    this.mapper = mapper;
  }

  /** 草稿品类及酒店声明件数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record DraftLine(Long itemId, Integer quantity) {}

  /** 草稿不能接收客户端状态、价格、实收或签收数量。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Input(
      String requestKey,
      Long version,
      Long customerId,
      LocalDate serviceDate,
      String note,
      List<DraftLine> lines) {}

  /** 按服务端行号传入本次数量，接口分别解释用途。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Count(
      Long lineId, Integer quantity, Integer good, Integer rewash, Integer discard) {}

  /** 单据命令、实物凭证及数量，失败重试必须保留UUID。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Command(
      String requestKey, Long version, String note, String reference, List<Count> lines) {}

  /** 上海业务日期；不接受浏览器自报签收时间。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public LocalDate today() {
    return LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
  }

  /** 非客户SELF范围仅查看本人编制或执行的批次；客户始终按绑定酒店隔离。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean visible(LaundryBatch b) {
    var a = access.current();
    if (a.customerId != null) return Objects.equals(a.customerId, b.customerId);
    return access.visible(b.departmentId)
        && (!access.role().scope.equals("SELF")
            || Objects.equals(a.id, b.authorId)
            || Objects.equals(a.id, b.processorId));
  }

  /** 知道编号也必须通过实时数据权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public LaundryBatch readable(Long id) {
    access.require("batch.read");
    var b = db.get(LaundryBatch.class, id);
    if (!visible(b)) throw new Problem(403, "OUT_OF_SCOPE");
    return b;
  }

  /** 返回稳定顺序的批次行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public List<BatchLine> lines(Long id) {
    return db.query(BatchLine.class, "from BatchLine where batchId=?1 order by id", id);
  }

  private List<Delivery> deliveries(Long id) {
    return db.query(Delivery.class, "from Delivery where batchId=?1 order by id", id);
  }

  private List<DeliveryLine> deliveryLines(Long id) {
    return db.query(DeliveryLine.class, "from DeliveryLine where deliveryId=?1 order by id", id);
  }

  private int reserved(LaundryBatch b, Long line) {
    int n = 0;
    for (var d : deliveries(b.id))
      if (Set.of("SENT", "AWAITING_RETURN").contains(d.status))
        for (var l : deliveryLines(d.id))
          if (l.batchLineId.equals(line)) n += l.sentQty - l.acceptedQty;
    return n;
  }

  private Map<String, Object> view(LaundryBatch b) {
    var shipments = new ArrayList<Object>();
    for (var d : deliveries(b.id)) shipments.add(Map.of("record", d, "lines", deliveryLines(d.id)));
    var available = new LinkedHashMap<String, Integer>();
    for (var l : lines(b.id))
      available.put(
          l.id.toString(), QuantityPolicy.available(l.goodQty, l.signedQty, reserved(b, l.id)));
    return Map.of(
        "record", b, "lines", lines(b.id), "deliveries", shipments, "available", available);
  }

  /** 完整批次详情含不可修改事件，客户不能看到其他客户。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object detail(Long id) {
    var b = readable(id);
    var v = new LinkedHashMap<String, Object>(view(b));
    v.put(
        "events",
        db.query(
            BusinessEvent.class,
            "from BusinessEvent where objectType='BATCH' and objectId=?1 order by id",
            id));
    return v;
  }

  /** 范围内有界批次目录，搜索、状态、排序和分页。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object list(String search, String status, int page, int size, String sort) {
    validateList(search, page, size, sort);
    if (!status.isEmpty() && !STATES.contains(status)) throw new Problem(400, "INVALID_STATUS");
    var rows =
        new ArrayList<>(
            visibleBatches().stream()
                .filter(
                    b ->
                        (status.isEmpty() || status.equals(b.status))
                            && (b.code + " " + b.customerName + " " + b.note)
                                .toLowerCase(Locale.ROOT)
                                .contains(search.toLowerCase(Locale.ROOT)))
                .toList());
    rows.sort(
        sort.equals("date")
            ? Comparator.comparing((LaundryBatch b) -> b.serviceDate).thenComparing(b -> b.id)
            : sort.equals("code")
                ? Comparator.comparing(b -> b.code)
                : Comparator.comparing((LaundryBatch b) -> b.id).reversed());
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

  /** 有限扫描，实际上限1000批次，超限明确要求归档或扩展。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public List<LaundryBatch> visibleBatches() {
    access.require("batch.read");
    return db.all(LaundryBatch.class).stream().filter(this::visible).toList();
  }

  /** 列表参数校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void validateList(String search, int page, int size, String sort) {
    if (search == null
        || search.length() > 160
        || page < 0
        || page > 1000
        || size < 1
        || size > 100
        || !Set.of("newest", "date", "code").contains(sort))
      throw new Problem(400, "INVALID_INPUT");
  }

  /** 草稿创建或修改，重新校验客户、品类和约定价。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object save(Long id, Input v) {
    catalog.mutation();
    access.require("batch.write");
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    String fp = fingerprint("SAVE_BATCH", id, v);
    var prior = retry(v.requestKey, fp);
    if (prior != null) return detail(prior);
    var c = db.get(Customer.class, v.customerId);
    if (!catalog.visible(c)) throw new Problem(403, "OUT_OF_SCOPE");
    if (!c.enabled) throw new Problem(409, "CUSTOMER_DISABLED");
    var b = id == null ? new LaundryBatch() : readable(id);
    if (id != null) {
      state(b, "DRAFT");
      version(b, v.version);
      if (!b.customerId.equals(c.id)) throw new Problem(409, "IMMUTABLE_REFERENCE");
    } else {
      int max =
          Integer.parseInt(
              db.query(SystemSetting.class, "from SystemSetting where code='maxBatches'")
                  .getFirst()
                  .value);
      if (db.all(LaundryBatch.class).size() >= max) throw new Problem(409, "BATCH_LIMIT");
      b.customerId = c.id;
      b.customerName = c.name;
      b.departmentId = c.departmentId;
      b.authorId = access.current().id;
      b.code = "LF-" + UUID.randomUUID().toString().substring(0, 18).toUpperCase(Locale.ROOT);
      b.status = "DRAFT";
      b.countNote = "";
      b.qualityNote = "";
    }
    if (v.serviceDate == null
        || v.serviceDate.isBefore(LocalDate.of(2000, 1, 1))
        || v.serviceDate.isAfter(today().plusDays(7))) throw new Problem(400, "INVALID_DATE");
    b.serviceDate = v.serviceDate;
    b.note = optional(v.note, 1000);
    if (v.lines == null || v.lines.isEmpty() || v.lines.size() > 50)
      throw new Problem(400, "INVALID_LINES");
    var ids = new HashSet<Long>();
    for (var l : v.lines)
      if (l == null || !ids.add(l.itemId) || QuantityPolicy.quantity(l.quantity) == 0)
        throw new Problem(400, "INVALID_LINES");
    if (id == null) db.save(b);
    for (var old : lines(b.id)) db.delete(old);
    for (var l : v.lines) {
      var item = db.get(LinenItem.class, l.itemId);
      var rate = rate(c.id, item.id);
      if (!item.enabled) throw new Problem(409, "ITEM_DISABLED");
      var row = new BatchLine();
      row.batchId = b.id;
      row.itemId = item.id;
      row.itemName = item.name;
      row.unitPrice = rate.unitPrice;
      row.priceReference = rate.reference;
      row.declaredQty = l.quantity;
      db.save(row);
    }
    b.version++;
    event("BATCH", b.id, "SAVE", b.note, b.departmentId, lines(b.id));
    remember(v.requestKey, fp, b.id);
    return detail(b.id);
  }

  /** 仅删除未送出的草稿，删除事件同时留操作审计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object delete(Long id, Command v) {
    catalog.mutation();
    access.require("batch.write");
    var b = readable(id);
    state(b, "DRAFT");
    version(b, v.version);
    for (var l : lines(id)) db.delete(l);
    for (var e :
        db.query(
            BusinessEvent.class, "from BusinessEvent where objectType='BATCH' and objectId=?1", id))
      db.delete(e);
    db.delete(b);
    access.audit("DELETE_DRAFT", id, b.departmentId);
    return Map.of("ok", true);
  }

  private RateCard rate(Long c, Long i) {
    var rates =
        db.query(
            RateCard.class,
            "from RateCard where customerId=?1 and itemId=?2 and enabled=true",
            c,
            i);
    if (rates.isEmpty()) throw new Problem(409, "RATE_MISSING");
    return rates.getFirst();
  }

  /** 状态命令均拒绝非法状态、陈旧版本、越权及数量失衡。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object command(Long id, String action, Command v) {
    catalog.mutation();
    String perm =
        switch (action) {
          case "submit", "cancel" -> "batch.write";
          case "count", "return" -> "batch.receive";
          case "accept-count", "reject-count", "sign", "complete" -> "batch.confirm";
          case "wash" -> "batch.process";
          case "inspect" -> "batch.inspect";
          case "dispatch" -> "batch.dispatch";
          default -> throw new Problem(404, "NOT_FOUND");
        };
    access.require(perm);
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    var b = readable(id);
    String fp = fingerprint(action, id, v);
    var prior = retry(v.requestKey, fp);
    if (prior != null) return detail(prior);
    version(b, v.version);
    String note = AdminService.text(v.note, 1000);
    var actor = access.current();
    switch (action) {
      case "submit" -> {
        state(b, "DRAFT");
        if (b.serviceDate.isAfter(today())) throw new Problem(400, "INVALID_DATE");
        var c = db.get(Customer.class, b.customerId);
        if (!c.enabled || !c.departmentId.equals(b.departmentId))
          throw new Problem(409, "CUSTOMER_DISABLED");
        for (var l : lines(id)) {
          var i = db.get(LinenItem.class, l.itemId);
          if (!i.enabled) throw new Problem(409, "ITEM_DISABLED");
          var r = rate(b.customerId, l.itemId);
          l.unitPrice = r.unitPrice;
          l.priceReference = r.reference;
        }
        b.status = "DECLARED";
      }
      case "cancel" -> {
        state(b, "DRAFT", "DECLARED");
        b.status = "CANCELLED";
      }
      case "count" -> {
        catalog.staff();
        state(b, "DECLARED");
        var rows = allCounts(b, v.lines);
        int total = 0;
        for (var l : lines(id)) {
          l.receivedQty = QuantityPolicy.quantity(rows.get(l.id).quantity);
          total += l.receivedQty;
        }
        if (total == 0) throw new Problem(400, "EMPTY_RECEIPT");
        b.countNote = note;
        b.status = "COUNTED";
      }
      case "accept-count" -> {
        hotel(b);
        state(b, "COUNTED");
        b.status = "ACCEPTED";
      }
      case "reject-count" -> {
        hotel(b);
        state(b, "COUNTED");
        b.status = "DECLARED";
      }
      case "wash" -> {
        catalog.staff();
        state(b, "ACCEPTED", "REWASH");
        b.processorId = actor.id;
        b.status = "WASHING";
      }
      case "inspect" -> {
        catalog.staff();
        state(b, "WASHING");
        if (actor.id.equals(b.processorId)) throw new Problem(403, "INDEPENDENT_INSPECTOR");
        var rows = allCounts(b, v.lines);
        int rewash = 0;
        for (var l : lines(id)) {
          var r = rows.get(l.id);
          int good = QuantityPolicy.quantity(r.good),
              rw = QuantityPolicy.quantity(r.rewash),
              discard = QuantityPolicy.quantity(r.discard);
          QuantityPolicy.inspection(l, good, rw, discard);
          l.goodQty = good;
          l.rewashQty = rw;
          l.discardQty = discard;
          rewash += rw;
        }
        b.qualityNote = note;
        b.status = rewash > 0 ? "REWASH" : "READY";
        updateDeliveryState(b);
      }
      case "dispatch" -> {
        catalog.staff();
        state(b, "READY");
        if (deliveries(id).size() >= 200) throw new Problem(409, "DELIVERY_LIMIT");
        var rows = subset(b, v.lines);
        var d = new Delivery();
        d.batchId = id;
        d.dispatcherId = actor.id;
        d.reference = AdminService.text(v.reference, 200);
        d.status = "SENT";
        d.note = note;
        d.createdAt = BusinessTime.now(clock);
        db.save(d);
        for (var r : rows.values()) {
          var l = db.get(BatchLine.class, r.lineId);
          int qty = QuantityPolicy.quantity(r.quantity);
          if (qty == 0 || qty > QuantityPolicy.available(l.goodQty, l.signedQty, reserved(b, l.id)))
            throw new Problem(409, "EXCESS_DELIVERY");
          var dl = new DeliveryLine();
          dl.deliveryId = d.id;
          dl.batchLineId = l.id;
          dl.sentQty = qty;
          db.save(dl);
        }
      }
      case "sign" -> {
        hotel(b);
        state(b, "READY");
        var d = delivery(b, v.reference);
        if (!d.status.equals("SENT")) throw new Problem(409, "INVALID_STATE");
        var rows = subset(b, v.lines);
        var shipped = deliveryLines(d.id);
        if (rows.size() != shipped.size()
            || !rows.keySet()
                .equals(new HashSet<>(shipped.stream().map(l -> l.batchLineId).toList())))
          throw new Problem(400, "INVALID_LINES");
        int refused = 0;
        for (var dl : shipped) {
          int qty = QuantityPolicy.quantity(rows.get(dl.batchLineId).quantity);
          if (qty > dl.sentQty) throw new Problem(400, "EXCESS_RECEIPT");
          dl.acceptedQty = qty;
          var l = db.get(BatchLine.class, dl.batchLineId);
          l.signedQty += qty;
          refused += dl.sentQty - qty;
        }
        d.signerId = actor.id;
        d.note = note;
        d.status = refused > 0 ? "AWAITING_RETURN" : "SIGNED";
        updateDeliveryState(b);
      }
      case "return" -> {
        catalog.staff();
        state(b, "READY");
        var d = delivery(b, v.reference);
        if (!d.status.equals("AWAITING_RETURN")) throw new Problem(409, "INVALID_STATE");
        if (d.dispatcherId.equals(actor.id)) throw new Problem(403, "INDEPENDENT_RETURN");
        var returned = subset(b, v.lines);
        var shipped = deliveryLines(d.id);
        if (returned.size() != shipped.size()) throw new Problem(400, "INVALID_LINES");
        for (var dl : shipped) {
          var rc = returned.get(dl.batchLineId);
          if (rc == null || QuantityPolicy.quantity(rc.quantity) != dl.sentQty - dl.acceptedQty)
            throw new Problem(400, "QUANTITY_BALANCE");
        }
        d.returnerId = actor.id;
        d.note = note;
        d.status = "RETURNED";
        updateDeliveryState(b);
      }
      case "complete" -> {
        hotel(b);
        state(b, "DELIVERED");
        b.status = "COMPLETED";
        b.completedAt = BusinessTime.now(clock);
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    b.version++;
    event(
        "BATCH",
        id,
        action,
        note,
        b.departmentId,
        Map.of("record", b, "lines", lines(id), "command", v));
    remember(v.requestKey, fp, id);
    return detail(id);
  }

  private Delivery delivery(LaundryBatch b, String ref) {
    var rows =
        db.query(
            Delivery.class,
            "from Delivery where batchId=?1 and reference=?2",
            b.id,
            AdminService.text(ref, 200));
    if (rows.isEmpty()) throw new Problem(404, "NOT_FOUND");
    return rows.getFirst();
  }

  private void updateDeliveryState(LaundryBatch b) {
    if (!Set.of("READY", "DELIVERED").contains(b.status)) return;
    boolean done =
        lines(b.id).stream().allMatch(l -> l.signedQty == l.goodQty)
            && deliveries(b.id).stream()
                .noneMatch(d -> Set.of("SENT", "AWAITING_RETURN").contains(d.status));
    b.status = done ? "DELIVERED" : "READY";
  }

  private Map<Long, Count> allCounts(LaundryBatch b, List<Count> rows) {
    var m = subset(b, rows);
    if (m.size() != lines(b.id).size()) throw new Problem(400, "INVALID_LINES");
    return m;
  }

  private Map<Long, Count> subset(LaundryBatch b, List<Count> rows) {
    if (rows == null || rows.isEmpty() || rows.size() > 50) throw new Problem(400, "INVALID_LINES");
    var ids = new HashSet<>(lines(b.id).stream().map(l -> l.id).toList());
    var m = new LinkedHashMap<Long, Count>();
    for (var row : rows)
      if (row == null || !ids.contains(row.lineId) || m.put(row.lineId, row) != null)
        throw new Problem(400, "INVALID_LINES");
    return m;
  }

  /** 酒店签收和确认必须由当前绑定账号完成，管理员也不能代签。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void hotel(LaundryBatch b) {
    if (!Objects.equals(access.current().customerId, b.customerId))
      throw new Problem(403, "HOTEL_ONLY");
  }

  /** 可复用的受控状态校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void state(LaundryBatch b, String... states) {
    if (!Arrays.asList(states).contains(b.status)) throw new Problem(409, "INVALID_STATE");
  }

  private void version(LaundryBatch b, Long version) {
    if (version == null || version != b.version) throw new Problem(409, "STALE_VERSION");
  }

  /** UUID绑定账号、命令和精确载荷；不能挪用于别的请求。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public String fingerprint(String action, Long id, Object body) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      (access.current().id
                              + ":"
                              + action
                              + ":"
                              + id
                              + ":"
                              + mapper.writeValueAsString(body))
                          .getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** 重试返回已有资源，不执行第二次业务更新。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Long retry(String key, String fp) {
    if (key == null
        || !key.matches(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
      throw new Problem(400, "INVALID_REQUEST_KEY");
    var rows = db.query(CommandRecord.class, "from CommandRecord where requestKey=?1", key);
    if (rows.isEmpty()) return null;
    if (!rows.getFirst().fingerprint.equals(fp)) throw new Problem(409, "REQUEST_KEY_REUSED");
    return rows.getFirst().resultId;
  }

  /** 记住成功命令，与业务原子提交。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void remember(String key, String fp, Long id) {
    var r = new CommandRecord();
    r.requestKey = key;
    r.fingerprint = fp;
    r.resultId = id;
    db.save(r);
  }

  /** 追加含实际数量的业务事件，运行日志只保存安全动作信息。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void event(String type, Long id, String action, String note, Long dep, Object snapshot) {
    if (db.query(
                BusinessEvent.class,
                "from BusinessEvent where objectType=?1 and objectId=?2",
                type,
                id)
            .size()
        >= 1000) throw new Problem(409, "EVENT_LIMIT");
    var e = new BusinessEvent();
    e.objectType = type;
    e.objectId = id;
    e.action = action;
    e.actorId = access.current().id;
    e.note = optional(note, 1000);
    e.snapshot = mapper.writeValueAsString(snapshot);
    e.createdAt = BusinessTime.now(clock);
    db.save(e);
    access.audit(type + "_" + action, id, dep);
  }

  /** 可选文本仍受长度上限保护。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String optional(String v, int max) {
    if (v == null) return "";
    if (v.length() > max) throw new Problem(400, "INVALID_INPUT");
    return v.trim();
  }
}

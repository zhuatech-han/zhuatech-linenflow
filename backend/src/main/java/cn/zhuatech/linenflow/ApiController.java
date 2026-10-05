// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.linenflow;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** 有身份、权限与数据范围校验的业务及管理HTTP入口。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final LaundryService laundry;
  final InvoiceService invoices;
  final CatalogService catalog;
  final AdminService admin;
  final Store db;
  final AccessService access;

  public ApiController(
      LaundryService laundry,
      InvoiceService invoices,
      CatalogService catalog,
      AdminService admin,
      Store db,
      AccessService access) {
    this.laundry = laundry;
    this.invoices = invoices;
    this.catalog = catalog;
    this.admin = admin;
    this.db = db;
    this.access = access;
  }

  /** 当前酒店或工厂范围内目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/options")
  public Object options() {
    return catalog.options();
  }

  /** 受限主数据增改。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/catalog/{type}")
  public Object createCatalog(@PathVariable String type, @RequestBody CatalogService.Input v) {
    return catalog.save(type, null, v);
  }

  /** 历史引用不随主数据改价变化。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/catalog/{type}/{id}")
  public Object editCatalog(
      @PathVariable String type, @PathVariable Long id, @RequestBody CatalogService.Input v) {
    return catalog.save(type, id, v);
  }

  /** 未引用目录删除。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/catalog/{type}/{id}")
  public Object deleteCatalog(@PathVariable String type, @PathVariable Long id) {
    catalog.delete(type, id);
    return Map.of("ok", true);
  }

  /** 批次搜索和分页。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/batches")
  public Object batches(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "newest") String sort) {
    return laundry.list(search, status, page, size, sort);
  }

  /** 批次详情。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/batches/{id}")
  public Object batch(@PathVariable Long id) {
    return laundry.detail(id);
  }

  /** 创建送洗草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/batches")
  public Object createBatch(@RequestBody LaundryService.Input v) {
    return laundry.save(null, v);
  }

  /** 编辑草稿并校验版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/batches/{id}")
  public Object editBatch(@PathVariable Long id, @RequestBody LaundryService.Input v) {
    return laundry.save(id, v);
  }

  /** 仅删除未送洗草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/batches/{id}")
  public Object deleteBatch(@PathVariable Long id, @RequestBody LaundryService.Command v) {
    return laundry.delete(id, v);
  }

  /** 完整批次流转。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/batches/{id}/commands/{action}")
  public Object batchCommand(
      @PathVariable Long id, @PathVariable String action, @RequestBody LaundryService.Command v) {
    return laundry.command(id, action, v);
  }

  /** 账单列表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/invoices")
  public Object invoices(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "newest") String sort) {
    return invoices.list(search, status, page, size, sort);
  }

  /** 完整账单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/invoices/{id}")
  public Object invoice(@PathVariable Long id) {
    return invoices.detail(id);
  }

  /** 汇集一个客户一个月份未计费的完成批次。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/invoices")
  public Object createInvoice(@RequestBody InvoiceService.Input v) {
    return invoices.create(v);
  }

  /** 账单、款项及独立冲正。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/invoices/{id}/commands/{action}")
  public Object invoiceCommand(
      @PathVariable Long id, @PathVariable String action, @RequestBody InvoiceService.Command v) {
    return invoices.command(id, action, v);
  }

  /** 仅导出有读取权限的当前记录，不向业务载荷添加品牌广告。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/{type}/{id}/report.json")
  public ResponseEntity<Object> report(@PathVariable String type, @PathVariable Long id) {
    access.require("export");
    Object result =
        switch (type) {
          case "batches" -> laundry.detail(id);
          case "invoices" -> invoices.detail(id);
          default -> throw new Problem(404, "NOT_FOUND");
        };
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + type + "-" + id + ".json")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(result);
  }

  /** 指标只使用当前账号有读取权限的真实业务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  @Transactional(readOnly = true)
  public Object dashboard() {
    access.require("dashboard");
    var batches =
        access.role().permissions.contains("batch.read")
            ? laundry.visibleBatches()
            : List.<LaundryBatch>of();
    var inv =
        access.role().permissions.contains("invoice.read")
            ? invoices.visibleInvoices()
            : List.<MonthlyInvoice>of();
    var states = new TreeMap<String, Long>();
    long received = 0, signed = 0, discard = 0, rewash = 0;
    for (var b : batches) {
      states.merge(b.status, 1L, Long::sum);
      for (var l : laundry.lines(b.id)) {
        received += l.receivedQty;
        signed += l.signedQty;
        discard += l.discardQty;
        rewash += l.rewashQty;
      }
    }
    var billed =
        inv.stream()
            .filter(i -> Set.of("CONFIRMED", "PAID").contains(i.status))
            .map(i -> i.amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    var paid =
        inv.stream()
            .filter(i -> Set.of("CONFIRMED", "PAID").contains(i.status))
            .map(i -> invoices.paid(i.id))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return Map.of(
        "states",
        states,
        "batches",
        batches.size(),
        "received",
        received,
        "signed",
        signed,
        "discard",
        discard,
        "rewash",
        rewash,
        "confirmedAmount",
        billed,
        "paid",
        paid,
        "balance",
        billed.subtract(paid));
  }

  /** 非客户岗位的部门操作日志，绑定客户不能读取工厂审计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    catalog.staff();
    return db.all(AuditEvent.class).stream()
        .filter(
            e ->
                access.visible(e.departmentId)
                    && (!access.role().scope.equals("SELF")
                        || e.actor.equals(access.current().username)))
        .sorted(Comparator.comparing((AuditEvent e) -> e.id).reversed())
        .limit(500)
        .toList();
  }

  /** ALL管理员目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{type}")
  public Object adminList(@PathVariable String type) {
    return admin.list(type);
  }

  /** 创建管理资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object adminCreate(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 编辑管理资源并实时生效。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object adminSave(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 引用和最后管理员保护。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object adminDelete(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }
}

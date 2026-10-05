// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.linenflow;

import java.math.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 维护客户自有布草品类及客户约定价，客户账号只见本人目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class CatalogService {
  final Store db;
  final AccessService access;

  public CatalogService(Store db, AccessService access) {
    this.db = db;
    this.access = access;
  }

  /** 受限主数据输入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Input(
      String name,
      Long departmentId,
      Boolean enabled,
      String category,
      Long customerId,
      Long itemId,
      BigDecimal unitPrice,
      String reference) {}

  /** 客户绑定优先于角色范围，ALL也不能跨酒店。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean visible(Customer c) {
    var a = access.current();
    return a.customerId != null
        ? Objects.equals(a.customerId, c.id)
        : access.visible(c.departmentId);
  }

  /** 获取当前目录，绑定酒店账号不接触其他客户或账号目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object options() {
    if (Collections.disjoint(
        access.role().permissions, Set.of("batch.read", "invoice.read", "catalog.write", "admin")))
      throw new Problem(403, "FORBIDDEN");
    var customers = db.all(Customer.class).stream().filter(this::visible).toList();
    var ids = customers.stream().map(c -> c.id).toList();
    return Map.of(
        "customers",
        customers,
        "items",
        db.all(LinenItem.class),
        "rates",
        db.all(RateCard.class).stream().filter(r -> ids.contains(r.customerId)).toList(),
        "categories",
        db.all(DictionaryEntry.class).stream().filter(d -> d.type.equals("linen")).toList(),
        "departments",
        db.all(Department.class).stream().filter(d -> access.visible(d.id)).toList());
  }

  /** 锁后重读身份；与管理变更和业务写入共享授权边界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void mutation() {
    db.lock(Department.class, 1L);
    var a = access.current();
    db.refresh(a);
    db.refresh(db.get(AccessRole.class, a.roleId));
    access.current();
  }

  /** 创建或更新未冻结主数据，历史批次保持快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object save(String type, Long id, Input v) {
    mutation();
    access.require("catalog.write");
    staff();
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    Object result;
    Long dep = access.current().departmentId;
    switch (type) {
      case "customers" -> {
        var c = id == null ? new Customer() : db.get(Customer.class, id);
        if (id != null && !visible(c)) throw new Problem(403, "OUT_OF_SCOPE");
        access.department(v.departmentId);
        if (id != null
            && !Objects.equals(c.departmentId, v.departmentId)
            && !db.query(LaundryBatch.class, "from LaundryBatch where customerId=?1", id).isEmpty())
          throw new Problem(409, "IMMUTABLE_REFERENCE");
        c.name = AdminService.text(v.name, 120);
        c.departmentId = db.get(Department.class, v.departmentId).id;
        c.enabled = Boolean.TRUE.equals(v.enabled);
        if (id != null
            && !db.query(
                    Account.class,
                    "from Account where customerId=?1 and departmentId<>?2",
                    id,
                    c.departmentId)
                .isEmpty()) throw new Problem(409, "CUSTOMER_BOUND");
        result = id == null ? db.save(c) : c;
        dep = c.departmentId;
      }
      case "items" -> {
        if (!access.role().scope.equals("ALL")) throw new Problem(403, "OUT_OF_SCOPE");
        var i = id == null ? new LinenItem() : db.get(LinenItem.class, id);
        i.name = AdminService.text(v.name, 120);
        i.category = AdminService.text(v.category, 60);
        if (db.query(
                DictionaryEntry.class,
                "from DictionaryEntry where type='linen' and code=?1",
                i.category)
            .isEmpty()) throw new Problem(400, "INVALID_CATEGORY");
        i.enabled = Boolean.TRUE.equals(v.enabled);
        result = id == null ? db.save(i) : i;
      }
      case "rates" -> {
        var c = db.get(Customer.class, v.customerId);
        if (!visible(c)) throw new Problem(403, "OUT_OF_SCOPE");
        var i = db.get(LinenItem.class, v.itemId);
        var r = id == null ? new RateCard() : db.get(RateCard.class, id);
        if (id != null && (!Objects.equals(r.customerId, c.id) || !Objects.equals(r.itemId, i.id)))
          throw new Problem(409, "IMMUTABLE_REFERENCE");
        r.customerId = c.id;
        r.itemId = i.id;
        r.unitPrice = money(v.unitPrice);
        r.reference = AdminService.text(v.reference, 200);
        r.enabled = Boolean.TRUE.equals(v.enabled);
        result = id == null ? db.save(r) : r;
        dep = c.departmentId;
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    db.flush();
    access.audit("CATALOG_SAVE_" + type, id == null ? "NEW" : id, dep);
    return result;
  }

  /** 删除仅未被引用的主数据，账号和业务外键保护历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void delete(String type, Long id) {
    mutation();
    access.require("catalog.write");
    staff();
    Object entity;
    Long dep = access.current().departmentId;
    switch (type) {
      case "customers" -> {
        var c = db.get(Customer.class, id);
        if (!visible(c)) throw new Problem(403, "OUT_OF_SCOPE");
        dep = c.departmentId;
        entity = c;
      }
      case "items" -> {
        if (!access.role().scope.equals("ALL")) throw new Problem(403, "OUT_OF_SCOPE");
        entity = db.get(LinenItem.class, id);
      }
      case "rates" -> {
        var r = db.get(RateCard.class, id);
        var c = db.get(Customer.class, r.customerId);
        if (!visible(c)) throw new Problem(403, "OUT_OF_SCOPE");
        dep = c.departmentId;
        entity = r;
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    db.delete(entity);
    access.audit("CATALOG_DELETE_" + type, id, dep);
  }

  /** 绑定客户的账号不得承担工厂职责。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void staff() {
    if (access.current().customerId != null) throw new Problem(403, "STAFF_ONLY");
  }

  /** 金额精确到分，拒绝隐式截断和非正单价。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal money(BigDecimal n) {
    if (n == null || n.signum() <= 0 || n.compareTo(new BigDecimal("9999999999.99")) > 0)
      throw new Problem(400, "INVALID_AMOUNT");
    try {
      return n.setScale(2, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException e) {
      throw new Problem(400, "INVALID_AMOUNT");
    }
  }
}

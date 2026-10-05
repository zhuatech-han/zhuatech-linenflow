// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.linenflow;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库创建岗位、权限、品类和管理员，不生成客户或洗涤事实。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String password;

  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${linenflow.admin-password}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.password = password;
  }

  /** 仅首次初始化，已有库保留全部账号和业务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    var d = new Department();
    d.name = "总部";
    db.save(d);
    Map<String, String> permissions = new LinkedHashMap<>();
    String[][] names = {
      {"batch.read", "查看送洗"},
      {"batch.write", "编制送洗"},
      {"batch.receive", "收货及退回核验"},
      {"batch.process", "洗涤执行"},
      {"batch.inspect", "独立质检"},
      {"batch.dispatch", "配送交接"},
      {"batch.confirm", "酒店确认"},
      {"invoice.read", "查看对账"},
      {"invoice.write", "编制月度对账"},
      {"invoice.confirm", "酒店确认账单"},
      {"payment.write", "收款与独立冲正"},
      {"catalog.write", "维护酒店与单价"},
      {"dashboard", "经营统计"},
      {"export", "导出业务"},
      {"audit", "操作审计"},
      {"admin", "系统管理"}
    };
    for (var row : names) {
      permissions.put(row[0], row[1]);
      var p = new Permission();
      p.code = row[0];
      p.name = row[1];
      db.save(p);
    }
    var admin = role("管理员", "ALL", permissions.keySet());
    role(
        "收货与生产",
        "DEPARTMENT",
        Set.of(
            "batch.read",
            "batch.write",
            "batch.receive",
            "batch.process",
            "dashboard",
            "export",
            "audit"));
    role("质检员", "DEPARTMENT", Set.of("batch.read", "batch.inspect", "dashboard", "export"));
    role("配送员", "DEPARTMENT", Set.of("batch.read", "batch.dispatch", "dashboard", "export"));
    role(
        "财务",
        "DEPARTMENT",
        Set.of(
            "batch.read",
            "invoice.read",
            "invoice.write",
            "payment.write",
            "catalog.write",
            "dashboard",
            "export",
            "audit"));
    role(
        "酒店客户",
        "SELF",
        Set.of(
            "batch.read",
            "batch.write",
            "batch.confirm",
            "invoice.read",
            "invoice.confirm",
            "dashboard",
            "export"));
    var a = new Account();
    a.username = "admin";
    a.displayName = "管理员";
    a.roleId = admin.id;
    a.departmentId = d.id;
    a.enabled = true;
    a.passwordHash = encoder.encode(password);
    db.save(a);
    String[][] menus = {
      {"workbench", "交接工作台", "Workbench", "batch.read"},
      {"batches", "送洗批次", "Laundry batches", "batch.read"},
      {"invoices", "月度对账", "Statements", "invoice.read"},
      {"catalog", "酒店与约定价", "Customers & rates", "catalog.write"},
      {"dashboard", "经营统计", "Statistics", "dashboard"},
      {"audit", "操作审计", "Audit", "audit"},
      {"users", "账号管理", "Accounts", "admin"},
      {"roles", "角色与权限", "Roles", "admin"},
      {"departments", "部门管理", "Departments", "admin"},
      {"menus", "导航管理", "Navigation", "admin"},
      {"permissions", "权限目录", "Permissions", "admin"},
      {"dictionaries", "布草品类", "Categories", "admin"},
      {"settings", "系统参数", "Settings", "admin"}
    };
    for (int i = 0; i < menus.length; i++) {
      var m = new NavMenu();
      m.code = menus[i][0];
      m.name = menus[i][1];
      m.nameEn = menus[i][2];
      m.permissionCode = menus[i][3];
      m.enabled = true;
      m.position = i;
      db.save(m);
    }
    Map.of("timezone", "Asia/Shanghai", "companyName", "LinenFlow 布草协作", "maxBatches", "1000")
        .forEach(
            (k, v) -> {
              var s = new SystemSetting();
              s.code = k;
              s.value = v;
              db.save(s);
            });
    for (var row :
        new String[][] {
          {"ROOM", "客房布草", "Room linen"},
          {"BATH", "卫浴布草", "Bath linen"},
          {"DINING", "餐饮布草", "Table linen"}
        }) {
      var e = new DictionaryEntry();
      e.type = "linen";
      e.code = row[0];
      e.name = row[1];
      e.nameEn = row[2];
      db.save(e);
    }
  }

  private AccessRole role(String name, String scope, Set<String> permissions) {
    var r = new AccessRole();
    r.name = name;
    r.scope = scope;
    r.permissions = new HashSet<>(permissions);
    return db.save(r);
  }
}

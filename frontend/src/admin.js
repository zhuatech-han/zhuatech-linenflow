// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 管理目录表单，注册权限和菜单不允许任意创建。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const adminFields = {
  users: [
    ["username", "登录名", "Username"],
    ["displayName", "姓名", "Name"],
    [
      "password",
      "新密码（编辑时可留空）",
      "New password (optional when editing)",
      "password",
    ],
    ["roleId", "角色", "Role", "roles"],
    ["departmentId", "部门", "Department", "departments"],
    [
      "customerId",
      "绑定酒店（工厂人员留空）",
      "Hotel binding (staff leave blank)",
      "customers",
    ],
    ["enabled", "启用", "Enabled", "boolean"],
  ],
  roles: [
    ["name", "角色名称", "Role name"],
    ["scope", "数据范围", "Data scope", "scope"],
    ["permissions", "接口权限", "Permissions", "permissions"],
  ],
  departments: [["name", "部门名称", "Department name"]],
  menus: [
    ["name", "中文名称", "Chinese name"],
    ["nameEn", "英文名称", "English name"],
    ["permissionCode", "所需权限", "Required permission", "permissionCode"],
    ["position", "顺序", "Order", "number"],
    ["enabled", "启用", "Enabled", "boolean"],
  ],
  permissions: [["name", "权限说明", "Permission name"]],
  dictionaries: [
    ["type", "字典类型", "Dictionary type"],
    ["code", "编码", "Code"],
    ["name", "中文名称", "Chinese name"],
    ["nameEn", "英文名称", "English name"],
  ],
  settings: [["value", "参数值", "Value"]],
};
/** 可新增的管理资源目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const creatable = ["users", "roles", "departments", "dictionaries"];
/** 客户、布草和约定价的可编辑字段。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const catalogFields = {
  customers: [
    ["name", "酒店名称", "Hotel name"],
    ["departmentId", "负责部门", "Department", "departments"],
    ["enabled", "启用", "Enabled", "boolean"],
  ],
  items: [
    ["name", "布草名称", "Linen name"],
    ["category", "品类", "Category", "categories"],
    ["enabled", "启用", "Enabled", "boolean"],
  ],
  rates: [
    ["customerId", "酒店", "Hotel", "customers"],
    ["itemId", "布草", "Linen", "items"],
    ["unitPrice", "约定单价（元/件）", "Rate (CNY/piece)", "money"],
    ["reference", "合同／约定凭据", "Contract reference"],
    ["enabled", "启用", "Enabled", "boolean"],
  ],
};

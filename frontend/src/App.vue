<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted } from "vue";
import {
  LayoutDashboard,
  PackageCheck,
  Truck,
  ReceiptText,
  Users,
  Settings,
  ChevronRight,
  Plus,
  Search,
  ArrowLeft,
  LogOut,
  Download,
  X,
  Check,
  AlertCircle,
  Shirt,
  Menu,
  Globe,
} from "@lucide/vue";
import { api, resetCsrf } from "./api.js";
import { statuses, balanced, dispatchRows, downloadJson } from "./domain.js";
import { adminFields, catalogFields, creatable } from "./admin.js";
const lang = ref(localStorage.getItem("linenflow-language") || "zh");
const tr = (zh, en) => (lang.value === "zh" ? zh : en);
const status = (s) => statuses[s]?.[lang.value === "zh" ? 0 : 1] || s;
const money = (n) =>
  Number(n || 0).toLocaleString(lang.value === "zh" ? "zh-CN" : "en-US", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
const profile = ref(null),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  page = ref("workbench"),
  mobileMenu = ref(false);
const login = ref({ username: "", password: "" });
const options = ref({
    customers: [],
    items: [],
    rates: [],
    departments: [],
    categories: [],
  }),
  directories = ref({}),
  rows = ref([]),
  total = ref(0),
  pageIndex = ref(0),
  search = ref(""),
  filter = ref(""),
  sort = ref("newest"),
  detail = ref(null),
  stats = ref(null),
  sheet = ref(null),
  form = ref({}),
  catalogType = ref("customers");
let pendingKey = null,
  pendingBody = null;
const can = (p) => profile.value?.permissions.includes(p);
const hotel = computed(() => profile.value?.customerId != null);
const menus = computed(() => profile.value?.menus || []);
const title = computed(() => {
  const m = menus.value.find((m) => m.code === page.value);
  return m ? tr(m.name, m.nameEn) : "LinenFlow";
});
const icon = (c) =>
  ({
    workbench: PackageCheck,
    batches: Shirt,
    invoices: ReceiptText,
    catalog: Users,
    dashboard: LayoutDashboard,
    audit: Settings,
  })[c] || Settings;
const nowDate = () =>
  new Date().toLocaleDateString("sv-SE", { timeZone: "Asia/Shanghai" });
const names = (type, id) => {
  const r = (options.value[type] || directories.value[type] || []).find(
    (x) => x.id === id,
  );
  return r?.displayName || r?.name || "—";
};
const batchStates = Object.keys(statuses).slice(0, 10),
  invoiceStates = [
    "DRAFT",
    "ISSUED",
    "DISPUTED",
    "CONFIRMED",
    "PAID",
    "CANCELLED",
  ];
const adminPage = computed(() => !!adminFields[page.value]);
const listing = computed(() =>
  ["workbench", "batches", "invoices"].includes(page.value),
);
const fields = computed(() =>
  sheet.value?.kind === "catalog"
    ? catalogFields[sheet.value.type]
    : sheet.value?.kind === "admin"
      ? adminFields[sheet.value.type]
      : [],
);
const supportsNew = computed(() =>
  adminPage.value
    ? creatable.includes(page.value)
    : page.value === "catalog"
      ? can("catalog.write") &&
        (catalogType.value !== "items" || profile.value.scope === "ALL")
      : page.value === "invoices"
        ? can("invoice.write") && !hotel.value
        : can("batch.write"),
);
const fieldOptions = (type) =>
  type === "scope"
    ? [
        { id: "ALL", name: tr("全部", "All") },
        { id: "DEPARTMENT", name: tr("本部门", "Department") },
        { id: "SELF", name: tr("本人／绑定酒店", "Self / bound hotel") },
      ]
    : type === "permissionCode"
      ? (directories.value.permissions || []).map((p) => ({
          id: p.code,
          name: p.name,
        }))
      : type === "categories"
        ? options.value.categories.map((c) => ({
            id: c.code,
            name: tr(c.name, c.nameEn),
          }))
        : options.value[type] || directories.value[type] || [];
const errors = {
  UNAUTHENTICATED: [
    "登录已过期，请重新登录",
    "Session expired. Please sign in.",
  ],
  LOGIN_FAILED: ["登录名或密码不正确", "Incorrect username or password."],
  LOGIN_THROTTLED: [
    "登录失败次数过多，请五分钟后再试",
    "Too many attempts. Try again in five minutes.",
  ],
  FORBIDDEN: [
    "当前账号没有这项操作权限",
    "This account cannot perform this action.",
  ],
  OUT_OF_SCOPE: [
    "当前账号不能访问这份记录",
    "This record is outside your access scope.",
  ],
  STALE_VERSION: [
    "记录已被更新，请刷新后重新核对",
    "The record changed. Refresh and review it.",
  ],
  QUANTITY_BALANCE: [
    "数量不平衡，请核对实收和各去向件数",
    "Counts do not balance. Check all quantities.",
  ],
  EXCESS_DELIVERY: [
    "发出数量超过当前可用量",
    "Dispatch exceeds available quantity.",
  ],
  EXCESS_RECEIPT: [
    "签收数量超过发出数量",
    "Accepted quantity exceeds dispatch.",
  ],
  EXCESS_PAYMENT: [
    "收款超过账单剩余应收",
    "Payment exceeds the outstanding balance.",
  ],
  INDEPENDENT_INSPECTOR: [
    "质检必须由其他人员独立完成",
    "Another person must perform inspection.",
  ],
  INDEPENDENT_RETURN: [
    "退回实物须由发货人之外的人员核验",
    "Physical returns need an independent receiver.",
  ],
  INDEPENDENT_REVERSAL: [
    "冲正须由原收款登记人之外的人员完成",
    "Another finance user must reverse the payment.",
  ],
  HOTEL_ONLY: [
    "请由绑定这家酒店的账号确认",
    "A user bound to this hotel must confirm.",
  ],
  STAFF_ONLY: [
    "这项操作须由工厂工作人员完成",
    "This action is for laundry staff.",
  ],
  INVALID_STATE: [
    "当前状态不能执行这项操作",
    "The current state does not allow this action.",
  ],
  RATE_MISSING: [
    "这家酒店尚未配置启用的约定单价",
    "No active agreed rate exists for this hotel.",
  ],
  INVALID_AMOUNT: [
    "请输入大于零且最多两位小数的金额",
    "Enter a positive amount with up to two decimal places.",
  ],
  INVALID_QUANTITY: [
    "请输入 0 至 1000000 的整数件数",
    "Enter an integer from 0 to 1,000,000.",
  ],
  INVALID_LINES: [
    "请逐行核对品类和数量，不能重复或遗漏",
    "Review all rows; duplicates and missing rows are not allowed.",
  ],
  EMPTY_RECEIPT: [
    "实收总量不能为零",
    "Total received quantity must be positive.",
  ],
  INVALID_DATE: [
    "请核对送洗日期；送出不能使用未来日期",
    "Check the service date; submitted batches cannot be in the future.",
  ],
  INVALID_PERIOD: [
    "请填写有效的已到月份",
    "Enter a valid current or past month.",
  ],
  INVALID_INPUT: [
    "请检查必填内容及长度",
    "Check required fields and their lengths.",
  ],
  WEAK_PASSWORD: [
    "密码需12位以上，包含大小写字母及数字",
    "Use at least 12 characters with upper/lowercase letters and digits.",
  ],
  LAST_ADMIN: [
    "必须保留至少一个启用的全范围管理员",
    "Keep at least one enabled administrator with full scope.",
  ],
  CONFLICT: [
    "记录仍被引用或编码已存在，请核对",
    "A reference or duplicate code prevents this action.",
  ],
  NO_ELIGIBLE_BATCHES: [
    "这个月份没有未计费的已完成批次",
    "No completed, unbilled batches exist for this month.",
  ],
  DUPLICATE_REFERENCE: [
    "这份收款或冲正凭据已使用",
    "This payment reference has already been used.",
  ],
  REQUEST_KEY_REUSED: [
    "重试内容发生了变化，请重新发起操作",
    "The retry payload changed. Start a new operation.",
  ],
  INVALID_PAYMENT: ["这份款项无法冲正", "This payment cannot be reversed."],
  IMMUTABLE_REFERENCE: [
    "已关联的客户、品类或负责部门不能直接更改",
    "Referenced customers, items or departments cannot be reassigned.",
  ],
  CUSTOMER_DISABLED: [
    "酒店已停用或负责部门发生变化",
    "The hotel is disabled or its department changed.",
  ],
  ITEM_DISABLED: ["布草品类已停用", "This linen item is disabled."],
  BUILTIN_RESOURCE: [
    "系统内建或已引用资料不能删除或改编码",
    "Built-in or referenced records cannot be removed or recoded.",
  ],
  OLD_PASSWORD_INVALID: ["旧密码不正确", "Incorrect current password."],
  NETWORK_ERROR: [
    "连接失败，请检查网络后重试",
    "Connection failed. Check your connection and retry.",
  ],
};
async function run(fn) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    await fn();
  } catch (e) {
    const key = e.message;
    error.value = errors[key]
      ? tr(...errors[key])
      : tr(
          "操作未完成，请核对内容或重新连接后再试",
          "The action could not finish. Review the input or reconnect and retry.",
        );
    if (key === "UNAUTHENTICATED") {
      profile.value = null;
      resetCsrf();
    }
  } finally {
    busy.value = false;
  }
}
function language() {
  lang.value = lang.value === "zh" ? "en" : "zh";
  localStorage.setItem("linenflow-language", lang.value);
  document.documentElement.lang = lang.value === "zh" ? "zh-CN" : "en";
}
async function signIn() {
  await run(async () => {
    profile.value = await api("/auth/login", "POST", login.value);
    login.value.password = "";
    page.value = menus.value[0]?.code || "workbench";
    await load();
  });
}
async function load() {
  profile.value = await api("/auth/me");
  if (!menus.value.some((m) => m.code === page.value))
    page.value = menus.value[0]?.code || "workbench";
  options.value = await api("/options");
  if (adminPage.value) {
    for (const t of ["roles", "departments", "permissions"])
      directories.value[t] = await api("/admin/" + t);
    rows.value = await api("/admin/" + page.value);
    total.value = rows.value.length;
  } else if (page.value === "catalog") {
    rows.value = options.value[catalogType.value];
    total.value = rows.value.length;
  } else if (page.value === "dashboard") {
    stats.value = await api("/dashboard");
  } else if (page.value === "audit") {
    rows.value = await api("/audit");
    total.value = rows.value.length;
  } else if (listing.value) {
    const t = page.value === "invoices" ? "invoices" : "batches";
    const data = await api(
      "/" +
        t +
        "?" +
        new URLSearchParams({
          search: search.value,
          status: filter.value,
          page: pageIndex.value,
          size: 20,
          sort: sort.value,
        }),
    );
    rows.value = data.items;
    total.value = data.total;
    if (can("dashboard")) stats.value = await api("/dashboard");
  }
}
async function navigate(p) {
  page.value = p;
  detail.value = null;
  sheet.value = null;
  pageIndex.value = 0;
  search.value = "";
  filter.value = "";
  mobileMenu.value = false;
  await run(load);
}
async function refresh() {
  await run(async () => {
    await load();
    if (detail.value)
      detail.value = await api(
        "/" +
          (page.value === "invoices" ? "invoices" : "batches") +
          "/" +
          detail.value.record.id,
      );
  });
}
async function openRecord(row) {
  await run(async () => {
    detail.value = await api(
      "/" + (page.value === "invoices" ? "invoices" : "batches") + "/" + row.id,
    );
  });
}
function openSheet(kind, type = null, row = null) {
  error.value = "";
  pendingKey = null;
  pendingBody = null;
  sheet.value = { kind, type, id: row?.id || null };
  if (kind === "batch") {
    form.value = {
      customerId:
        row?.record.customerId ||
        profile.value.customerId ||
        options.value.customers.find((c) => c.enabled)?.id,
      serviceDate: row?.record.serviceDate || nowDate(),
      note: row?.record.note || "",
      lines: row?.lines.map((l) => ({
        itemId: l.itemId,
        quantity: l.declaredQty,
      })) || [{ itemId: null, quantity: 1 }],
    };
    sheet.value.id = row?.record.id;
    sheet.value.version = row?.record.version;
  } else if (kind === "invoice")
    form.value = {
      customerId: options.value.customers.find((c) => c.enabled)?.id,
      period: nowDate().slice(0, 7),
      note: "",
    };
  else if (kind === "password")
    form.value = { oldPassword: "", newPassword: "" };
  else {
    form.value = JSON.parse(JSON.stringify(row || {}));
    if (!row) {
      form.value.enabled = true;
      form.value.scope = "DEPARTMENT";
      form.value.permissions = [];
      if (type === "users")
        form.value.departmentId = profile.value.departmentId;
      if (type === "customers")
        form.value.departmentId = profile.value.departmentId;
      if (type === "dictionaries") form.value.type = "linen";
    }
    if (type === "users") form.value.password = "";
  }
}
function newRecord() {
  if (page.value === "catalog") openSheet("catalog", catalogType.value);
  else if (adminPage.value) openSheet("admin", page.value);
  else openSheet(page.value === "invoices" ? "invoice" : "batch");
}
function command(action, delivery = null, payment = null) {
  error.value = "";
  pendingKey = null;
  pendingBody = null;
  sheet.value = { kind: "command", action, delivery, payment };
  const ls = detail.value.lines || [];
  form.value = {
    note: "",
    reference: delivery?.record.reference || "",
    amount: "",
    lines: ls.map((l) => ({
      lineId: l.id,
      name: l.itemName,
      receivedQty: l.receivedQty,
      previousGood: l.goodQty,
      previousDiscard: l.discardQty,
      quantity:
        action === "count"
          ? l.declaredQty
          : action === "dispatch"
            ? detail.value.available[l.id]
            : 0,
      good: l.goodQty,
      rewash: l.rewashQty,
      discard: l.discardQty,
    })),
  };
  if (delivery)
    form.value.lines = delivery.lines.map((dl) => ({
      lineId: dl.batchLineId,
      name: ls.find((l) => l.id === dl.batchLineId)?.itemName,
      quantity: action === "sign" ? dl.sentQty : dl.sentQty - dl.acceptedQty,
      sentQty: dl.sentQty,
    }));
}
const actions = {
  submit: ["送出送洗单", "Submit batch"],
  cancel: ["取消", "Cancel"],
  count: ["登记工厂清点", "Record intake"],
  "accept-count": ["确认实收数量", "Accept intake"],
  "reject-count": ["退回重新清点", "Request recount"],
  wash: ["开始洗涤／返洗", "Start wash / rewash"],
  inspect: ["登记独立质检", "Record inspection"],
  dispatch: ["建立配送交接", "Dispatch linen"],
  sign: ["登记酒店签收", "Record hotel receipt"],
  return: ["核验实物退回", "Verify physical return"],
  complete: ["确认整批完成", "Confirm completion"],
  issue: ["发送账单", "Issue statement"],
  confirm: ["确认账单", "Confirm statement"],
  dispute: ["提出账单异议", "Dispute statement"],
  pay: ["登记已收款", "Record payment"],
  reverse: ["登记独立冲正", "Reverse payment"],
};
const availableActions = computed(() => {
  if (!detail.value) return [];
  const s = detail.value.record.status;
  if (page.value === "invoices")
    return [
      ["issue", "invoice.write", ["DRAFT"], false],
      ["cancel", "invoice.write", ["DRAFT", "ISSUED", "DISPUTED"], false],
      ["confirm", "invoice.confirm", ["ISSUED"], true],
      ["dispute", "invoice.confirm", ["ISSUED"], true],
      ["pay", "payment.write", ["CONFIRMED"], false],
    ]
      .filter(
        ([a, p, states, isHotel]) =>
          a &&
          can(p) &&
          states.includes(s) &&
          (isHotel ? hotel.value : !hotel.value),
      )
      .map((r) => r[0]);
  return [
    ["submit", "batch.write", ["DRAFT"], null],
    ["cancel", "batch.write", ["DRAFT", "DECLARED"], null],
    ["count", "batch.receive", ["DECLARED"], false],
    ["accept-count", "batch.confirm", ["COUNTED"], true],
    ["reject-count", "batch.confirm", ["COUNTED"], true],
    ["wash", "batch.process", ["ACCEPTED", "REWASH"], false],
    ["inspect", "batch.inspect", ["WASHING"], false],
    ["dispatch", "batch.dispatch", ["READY"], false],
    ["complete", "batch.confirm", ["DELIVERED"], true],
  ]
    .filter(
      ([a, p, states, isHotel]) =>
        a &&
        can(p) &&
        states.includes(s) &&
        (isHotel == null || isHotel === hotel.value),
    )
    .map((r) => r[0]);
});
const sheetTitle = computed(() =>
  sheet.value?.kind === "about"
    ? tr("关于 LinenFlow", "About LinenFlow")
    : sheet.value?.kind === "command"
      ? tr(...actions[sheet.value.action])
      : sheet.value?.kind === "batch"
        ? tr("送洗单", "Laundry batch")
        : sheet.value?.kind === "invoice"
          ? tr("生成月度对账", "Create monthly statement")
          : sheet.value?.kind === "password"
            ? tr("修改密码", "Change password")
            : tr(
                sheet.value?.id ? "编辑资料" : "新增资料",
                sheet.value?.id ? "Edit record" : "New record",
              ),
);
async function save() {
  await run(async () => {
    const s = sheet.value;
    if (s.kind === "password") {
      await api("/auth/password", "POST", form.value);
      profile.value = null;
      sheet.value = null;
      resetCsrf();
      return;
    }
    let path,
      method = "POST",
      body = JSON.parse(JSON.stringify(form.value));
    if (s.kind === "batch") {
      path = "/batches" + (s.id ? "/" + s.id : "");
      method = s.id ? "PUT" : "POST";
      body.version = s.version;
    } else if (s.kind === "invoice") path = "/invoices";
    else if (s.kind === "command") {
      const inv = page.value === "invoices";
      path =
        "/" +
        (inv ? "invoices" : "batches") +
        "/" +
        detail.value.record.id +
        "/commands/" +
        s.action;
      body.version = detail.value.record.version;
      if (s.payment) body.paymentId = s.payment.id;
      if (s.action === "dispatch") body.lines = dispatchRows(body.lines);
      else if (s.action === "inspect") {
        if (!body.lines.every(balanced)) throw new Error("QUANTITY_BALANCE");
        body.lines = body.lines.map((l) => ({
          lineId: l.lineId,
          good: l.good,
          rewash: l.rewash,
          discard: l.discard,
        }));
      } else
        body.lines = body.lines.map((l) => ({
          lineId: l.lineId,
          quantity: l.quantity,
        }));
    } else {
      path =
        "/" +
        (s.kind === "admin" ? "admin" : "catalog") +
        "/" +
        s.type +
        (s.id ? "/" + s.id : "");
      method = s.id ? "PUT" : "POST";
      if (s.type === "users" && body.customerId === "") body.customerId = null;
    }
    if (["batch", "invoice", "command"].includes(s.kind)) {
      const signature = JSON.stringify(body);
      if (signature !== pendingBody) {
        pendingKey = crypto.randomUUID();
        pendingBody = signature;
      }
      body.requestKey = pendingKey;
    }
    const result = await api(path, method, body);
    sheet.value = null;
    pendingKey = null;
    pendingBody = null;
    await load();
    if (result?.record && ["batch", "invoice", "command"].includes(s.kind))
      detail.value = result;
    notice.value = tr("已保存", "Saved");
  });
}
async function remove(row) {
  openSheet(
    "delete",
    page.value === "catalog" ? catalogType.value : page.value,
    row,
  );
}
async function confirmDelete() {
  await run(async () => {
    const s = sheet.value;
    const prefix = adminPage.value
      ? "admin"
      : page.value === "catalog"
        ? "catalog"
        : "batches";
    await api(
      "/" + prefix + (prefix === "batches" ? "" : "/" + s.type) + "/" + s.id,
      "DELETE",
      prefix === "batches"
        ? { version: detail.value.record.version }
        : undefined,
    );
    sheet.value = null;
    if (prefix === "batches") detail.value = null;
    await load();
    notice.value = tr("已删除", "Deleted");
  });
}
async function exportRecord() {
  await run(async () => {
    const type = page.value === "invoices" ? "invoices" : "batches";
    const value = await api(
      "/" + type + "/" + detail.value.record.id + "/report.json",
    );
    downloadJson(value, type + "-" + detail.value.record.code + ".json");
  });
}
async function logout() {
  await run(async () => {
    await api("/auth/logout", "POST");
    profile.value = null;
    detail.value = null;
    sheet.value = null;
    resetCsrf();
  });
}
onMounted(() =>
  run(async () => {
    try {
      profile.value = await api("/auth/me");
      page.value = menus.value[0]?.code || "workbench";
      await load();
    } catch (e) {
      if (e.message === "UNAUTHENTICATED") {
        profile.value = null;
        resetCsrf();
      } else throw e;
    }
  }),
);
</script>

<template>
  <div v-if="!profile" class="login-page">
    <section class="login-story">
      <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
        ><img src="/brand/logo.jpg" alt="知华科技"
      /></a>
      <div>
        <span class="eyebrow">LINENFLOW</span>
        <h1>
          {{
            tr(
              "每一次交接，\n都有清楚的记录。",
              "Every handover,\nclearly recorded.",
            )
          }}
        </h1>
        <p>
          {{
            tr(
              "送洗、清点、质检、签收与对账。",
              "Intake, inspection, delivery and reconciliation.",
            )
          }}
        </p>
        <div class="story-route">
          <span><Shirt />{{ tr("酒店", "Hotel") }}</span
          ><ChevronRight /><span
            ><PackageCheck />{{ tr("洗涤厂", "Laundry") }}</span
          ><ChevronRight /><span
            ><ReceiptText />{{ tr("对账", "Statement") }}</span
          >
        </div>
      </div>
      <small>上海如静知华信息科技有限公司</small>
    </section>
    <section class="login-panel">
      <button class="language" @click="language">
        <Globe :size="16" />{{ lang === "zh" ? "English" : "中文" }}
      </button>
      <form @submit.prevent="signIn">
        <span class="eyebrow">{{
          tr("知华布草协作", "ZHUA TECH LINEN OPERATIONS")
        }}</span>
        <h2>{{ tr("登录工作台", "Sign in") }}</h2>
        <p class="muted">
          {{
            tr(
              "使用分配给你的账号继续。",
              "Continue with your assigned account.",
            )
          }}
        </p>
        <label
          >{{ tr("登录名", "Username")
          }}<input
            v-model="login.username"
            autocomplete="username"
            required
            maxlength="60" /></label
        ><label
          >{{ tr("密码", "Password")
          }}<input
            v-model="login.password"
            type="password"
            autocomplete="current-password"
            required
            maxlength="128"
        /></label>
        <p v-if="error" class="error" role="alert">
          <AlertCircle :size="16" />{{ error }}
        </p>
        <button class="primary full" :disabled="busy">
          {{ busy ? tr("正在登录…", "Signing in…") : tr("登录", "Sign in")
          }}<ChevronRight :size="18" />
        </button>
        <div class="login-license">
          {{
            tr(
              "0.1.0 · 公开源码学习版／非商业源码版",
              "0.1.0 · Non-commercial source edition",
            )
          }}<br /><a
            href="https://www.zhuatech.cn/"
            target="_blank"
            rel="noopener"
            >{{ tr("知华科技官网", "ZhuaTech website") }}</a
          >
          · {{ tr("商业咨询", "Consulting") }} zhuatech / zhuatech2
        </div>
      </form>
    </section>
  </div>
  <div v-else class="app-shell">
    <aside :class="{ opened: mobileMenu }">
      <a
        class="brand"
        href="https://www.zhuatech.cn/"
        target="_blank"
        rel="noopener"
        ><img src="/brand/logo.jpg" alt="知华科技" />
        <div>
          LinenFlow<small>{{ tr("布草洗涤协作", "Linen operations") }}</small>
        </div></a
      >
      <div class="workspace-badge">
        <span class="dot"></span
        >{{
          hotel
            ? tr("酒店工作台", "Hotel workspace")
            : tr("工厂工作台", "Laundry workspace")
        }}
      </div>
      <nav>
        <button
          v-for="m in menus"
          :key="m.id"
          :class="{ selected: page === m.code }"
          @click="navigate(m.code)"
        >
          <component :is="icon(m.code)" :size="18" /><span>{{
            tr(m.name, m.nameEn)
          }}</span
          ><ChevronRight v-if="page === m.code" :size="14" />
        </button>
      </nav>
      <div class="sidebar-foot">
        <button @click="openSheet('about')">
          {{ tr("关于与商业咨询", "About & consulting") }}</button
        ><small
          >{{ tr("非商业源码版", "Non-commercial edition") }} · 0.1.0</small
        >
      </div>
    </aside>
    <main>
      <header>
        <button
          class="icon mobile-toggle"
          :aria-label="tr('打开菜单', 'Open menu')"
          @click="mobileMenu = !mobileMenu"
        >
          <Menu />
        </button>
        <div class="breadcrumb">
          LinenFlow <ChevronRight :size="14" /> {{ title }}
        </div>
        <div class="header-actions">
          <button class="quiet" @click="language">
            <Globe :size="15" />{{ lang === "zh" ? "English" : "中文" }}</button
          ><button class="quiet" @click="openSheet('password')">
            {{ profile.displayName }}</button
          ><button
            class="icon"
            :aria-label="tr('退出登录', 'Sign out')"
            @click="logout"
          >
            <LogOut :size="18" />
          </button>
        </div>
      </header>
      <section class="content">
        <p v-if="error" class="error" role="alert">
          <AlertCircle :size="18" />{{ error
          }}<button class="quiet" @click="refresh">
            {{ tr("刷新记录", "Refresh") }}
          </button>
        </p>
        <p v-if="notice" class="success" role="status">
          <Check :size="16" />{{ notice }}
        </p>
        <div v-if="!detail" class="page-heading">
          <div>
            <span class="eyebrow">{{
              hotel
                ? tr("客户协作", "CUSTOMER WORKSPACE")
                : tr("洗涤运营", "LAUNDRY OPERATIONS")
            }}</span>
            <h1>{{ title }}</h1>
          </div>
          <div class="toolbar">
            <button class="secondary" :disabled="busy" @click="refresh">
              {{ tr("刷新", "Refresh") }}</button
            ><button
              v-if="supportsNew && (listing || adminPage || page === 'catalog')"
              class="primary"
              :disabled="busy"
              @click="newRecord"
            >
              <Plus :size="17" />{{
                page === "invoices"
                  ? tr("生成对账单", "Create statement")
                  : tr("新增", "New")
              }}
            </button>
          </div>
        </div>
        <template v-if="!detail && page === 'workbench' && stats"
          ><div class="metrics">
            <article>
              <span>{{ tr("送洗批次", "Laundry batches") }}</span
              ><strong>{{ stats.batches }}</strong
              ><small>{{ tr("当前授权范围", "Current access scope") }}</small>
            </article>
            <article>
              <span>{{ tr("实收布草", "Received linen") }}</span
              ><strong
                >{{ stats.received }}<em>{{ tr("件", "pcs") }}</em></strong
              ><small>{{
                tr("工厂已登记清点", "Counted at the laundry")
              }}</small>
            </article>
            <article>
              <span>{{ tr("酒店已签收", "Accepted by hotels") }}</span
              ><strong
                >{{ stats.signed }}<em>{{ tr("件", "pcs") }}</em></strong
              ><small>{{
                tr("按实际签收数量", "Actual accepted quantity")
              }}</small>
            </article>
            <article>
              <span>{{ tr("待返洗布草", "Rewash pending") }}</span
              ><strong
                >{{ stats.rewash }}<em>{{ tr("件", "pcs") }}</em></strong
              ><small>{{
                tr("质检后的当前待返洗量", "Current inspection disposition")
              }}</small>
            </article>
          </div>
          <div class="section-caption">
            <h2>{{ tr("交接进度", "Handover progress") }}</h2>
            <span>{{
              tr(
                "按状态筛选下面的记录",
                "Filter records by their current state",
              )
            }}</span>
          </div></template
        >
        <template v-if="!detail && listing"
          ><form
            class="filters"
            @submit.prevent="
              pageIndex = 0;
              run(load);
            "
          >
            <div class="search">
              <Search :size="17" /><input
                v-model="search"
                :placeholder="
                  tr('搜索编号、酒店或备注', 'Search code, hotel or note')
                "
                maxlength="160"
              />
            </div>
            <select
              v-model="filter"
              :aria-label="tr('状态筛选', 'Status filter')"
            >
              <option value="">{{ tr("全部状态", "All states") }}</option>
              <option
                v-for="s in page === 'invoices' ? invoiceStates : batchStates"
                :key="s"
                :value="s"
              >
                {{ status(s) }}
              </option></select
            ><select v-model="sort" :aria-label="tr('排序', 'Sort')">
              <option value="newest">{{ tr("最近创建", "Newest") }}</option>
              <option value="date">
                {{ tr("业务日期", "Business date") }}
              </option>
              <option value="code">{{ tr("编号", "Code") }}</option></select
            ><button class="secondary" :disabled="busy">
              {{ tr("查询", "Apply") }}
            </button>
          </form>
          <div class="table-panel">
            <table>
              <thead>
                <tr>
                  <th>{{ tr("编号", "Code") }}</th>
                  <th>{{ tr("酒店", "Hotel") }}</th>
                  <th>
                    {{
                      page === "invoices"
                        ? tr("完成月份", "Completion month")
                        : tr("送洗日期", "Service date")
                    }}
                  </th>
                  <th>{{ tr("状态", "State") }}</th>
                  <th>
                    {{
                      page === "invoices"
                        ? tr("账单金额", "Amount")
                        : tr("备注", "Note")
                    }}
                  </th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in rows" :key="r.id">
                  <td class="code">
                    <button class="text-button" @click="openRecord(r)">
                      {{ r.code }}
                    </button>
                  </td>
                  <td>{{ r.customerName }}</td>
                  <td>{{ r.period || r.serviceDate }}</td>
                  <td>
                    <span :class="['status', r.status.toLowerCase()]">{{
                      status(r.status)
                    }}</span>
                  </td>
                  <td class="ellipsis">
                    {{
                      page === "invoices"
                        ? "¥ " + money(r.amount)
                        : r.note || "—"
                    }}
                  </td>
                  <td>
                    <button class="text-button" @click="openRecord(r)">
                      {{ tr("查看", "Open") }}<ChevronRight :size="14" />
                    </button>
                  </td>
                </tr>
                <tr v-if="!rows.length">
                  <td colspan="6" class="empty">
                    {{ tr("没有符合条件的记录", "No matching records") }}
                  </td>
                </tr>
              </tbody>
            </table>
            <div class="pagination">
              <span
                >{{ tr("共", "Total") }} {{ total }}
                {{ tr("条", "records") }}</span
              ><button
                class="quiet"
                :disabled="pageIndex === 0 || busy"
                @click="
                  pageIndex--;
                  run(load);
                "
              >
                {{ tr("上一页", "Previous") }}</button
              ><span>{{ pageIndex + 1 }}</span
              ><button
                class="quiet"
                :disabled="(pageIndex + 1) * 20 >= total || busy"
                @click="
                  pageIndex++;
                  run(load);
                "
              >
                {{ tr("下一页", "Next") }}
              </button>
            </div>
          </div>
        </template>
        <template v-if="detail"
          ><button class="back" @click="detail = null">
            <ArrowLeft :size="16" />{{ tr("返回列表", "Back to list") }}
          </button>
          <div class="page-heading">
            <div>
              <span class="eyebrow">{{ detail.record.customerName }}</span>
              <h1 class="record-title">{{ detail.record.code }}</h1>
              <span :class="['status', detail.record.status.toLowerCase()]">{{
                status(detail.record.status)
              }}</span>
            </div>
            <div class="toolbar">
              <button class="secondary" :disabled="busy" @click="refresh">
                {{ tr("刷新", "Refresh") }}</button
              ><button
                v-if="can('export')"
                class="secondary"
                @click="exportRecord"
              >
                <Download :size="16" />{{ tr("导出", "Export") }}</button
              ><button
                v-if="
                  detail.record.status === 'DRAFT' &&
                  page !== 'invoices' &&
                  can('batch.write')
                "
                class="secondary"
                @click="openSheet('batch', null, detail)"
              >
                {{ tr("编辑草稿", "Edit draft") }}</button
              ><button
                v-if="
                  detail.record.status === 'DRAFT' &&
                  page !== 'invoices' &&
                  can('batch.write')
                "
                class="secondary danger"
                @click="remove(detail.record)"
              >
                {{ tr("删除草稿", "Delete draft") }}
              </button>
            </div>
          </div>
          <div class="record-summary">
            <div>
              <small>{{ tr("酒店", "Hotel") }}</small
              ><strong>{{ detail.record.customerName }}</strong>
            </div>
            <div>
              <small>{{
                page === "invoices"
                  ? tr("完成月份", "Completion month")
                  : tr("送洗日期", "Service date")
              }}</small
              ><strong>{{
                detail.record.period || detail.record.serviceDate
              }}</strong>
            </div>
            <div>
              <small>{{ tr("负责部门", "Department") }}</small
              ><strong>{{
                names("departments", detail.record.departmentId)
              }}</strong>
            </div>
            <div>
              <small>{{ tr("备注", "Note") }}</small
              ><strong>{{ detail.record.note || "—" }}</strong>
            </div>
          </div>
          <div class="action-bar">
            <button
              v-for="a in availableActions"
              :key="a"
              :class="
                a === 'cancel' || a === 'reject-count' || a === 'dispute'
                  ? 'secondary'
                  : 'primary'
              "
              :disabled="busy"
              @click="command(a)"
            >
              {{ tr(...actions[a]) }}
            </button>
          </div>
          <template v-if="page !== 'invoices'"
            ><div class="table-panel">
              <div class="panel-heading">
                <h2>
                  {{ tr("布草数量与约定价", "Linen counts & agreed rates") }}
                </h2>
                <span>{{
                  tr("单位：件；单价：元/件", "Unit: pieces; rate: CNY/piece")
                }}</span>
              </div>
              <table>
                <thead>
                  <tr>
                    <th>{{ tr("布草", "Linen") }}</th>
                    <th>{{ tr("酒店声明", "Declared") }}</th>
                    <th>{{ tr("工厂实收", "Received") }}</th>
                    <th>{{ tr("合格", "Good") }}</th>
                    <th>{{ tr("待返洗", "Rewash") }}</th>
                    <th>{{ tr("报损", "Discard") }}</th>
                    <th>{{ tr("已签收", "Accepted") }}</th>
                    <th>{{ tr("可发出", "Available") }}</th>
                    <th>{{ tr("约定单价", "Rate") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="l in detail.lines" :key="l.id">
                    <td>
                      <strong>{{ l.itemName }}</strong
                      ><small class="cell-note">{{ l.priceReference }}</small>
                    </td>
                    <td>{{ l.declaredQty }}</td>
                    <td>{{ l.receivedQty }}</td>
                    <td>{{ l.goodQty }}</td>
                    <td>{{ l.rewashQty }}</td>
                    <td>{{ l.discardQty }}</td>
                    <td>{{ l.signedQty }}</td>
                    <td>{{ detail.available[l.id] }}</td>
                    <td>¥ {{ money(l.unitPrice) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div
              v-if="detail.record.countNote || detail.record.qualityNote"
              class="notes"
            >
              <div>
                <small>{{ tr("清点记录", "Intake note") }}</small>
                <p>{{ detail.record.countNote || "—" }}</p>
              </div>
              <div>
                <small>{{ tr("质检记录", "Inspection note") }}</small>
                <p>{{ detail.record.qualityNote || "—" }}</p>
              </div>
            </div>
            <div class="section-caption">
              <h2>{{ tr("配送交接", "Delivery handovers") }}</h2>
              <span
                >{{ detail.deliveries.length }} {{ tr("笔", "records") }}</span
              >
            </div>
            <div v-if="!detail.deliveries.length" class="empty bordered">
              {{ tr("尚无配送记录", "No delivery handovers yet") }}
            </div>
            <article
              v-for="d in detail.deliveries"
              :key="d.record.id"
              class="delivery-card"
            >
              <div class="delivery-heading">
                <Truck :size="19" /><strong>{{ d.record.reference }}</strong
                ><span :class="['status', d.record.status.toLowerCase()]">{{
                  status(d.record.status)
                }}</span
                ><button
                  v-if="
                    d.record.status === 'SENT' && hotel && can('batch.confirm')
                  "
                  class="primary"
                  @click="command('sign', d)"
                >
                  {{ tr("核对签收", "Confirm receipt") }}</button
                ><button
                  v-if="
                    d.record.status === 'AWAITING_RETURN' &&
                    !hotel &&
                    can('batch.receive') &&
                    d.record.dispatcherId !== profile.id
                  "
                  class="secondary"
                  @click="command('return', d)"
                >
                  {{ tr("核验实物退回", "Verify return") }}
                </button>
              </div>
              <div class="delivery-quantities">
                <div v-for="dl in d.lines" :key="dl.id">
                  <strong>{{
                    detail.lines.find((l) => l.id === dl.batchLineId)?.itemName
                  }}</strong
                  ><span
                    >{{ tr("发出", "Sent") }} {{ dl.sentQty }} ·
                    {{ tr("签收", "Accepted") }} {{ dl.acceptedQty }}</span
                  >
                </div>
              </div>
              <p class="muted">{{ d.record.note }}</p>
            </article>
          </template>
          <template v-else
            ><div class="metrics invoice-metrics">
              <article>
                <span>{{ tr("账单金额", "Statement amount") }}</span
                ><strong>¥ {{ money(detail.record.amount) }}</strong>
              </article>
              <article>
                <span>{{ tr("实际净收款", "Net payments") }}</span
                ><strong>¥ {{ money(detail.paid) }}</strong>
              </article>
              <article>
                <span>{{ tr("剩余应收", "Balance") }}</span
                ><strong>¥ {{ money(detail.balance) }}</strong>
              </article>
            </div>
            <div class="table-panel">
              <div class="panel-heading">
                <h2>{{ tr("计费明细", "Billing details") }}</h2>
                <span>{{
                  tr(
                    "已完成批次的签收合格件数 × 冻结单价",
                    "Accepted good pieces × frozen rate",
                  )
                }}</span>
              </div>
              <table>
                <thead>
                  <tr>
                    <th>{{ tr("送洗编号", "Batch") }}</th>
                    <th>{{ tr("布草", "Linen") }}</th>
                    <th>{{ tr("签收件数", "Accepted pieces") }}</th>
                    <th>{{ tr("单价", "Rate") }}</th>
                    <th>{{ tr("金额", "Amount") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <template v-for="b in detail.batches" :key="b.record.id"
                    ><tr v-for="l in b.lines" :key="l.id">
                      <td class="code">{{ b.record.code }}</td>
                      <td>{{ l.itemName }}</td>
                      <td>{{ l.signedQty }}</td>
                      <td>¥ {{ money(l.unitPrice) }}</td>
                      <td>¥ {{ money(l.signedQty * l.unitPrice) }}</td>
                    </tr></template
                  >
                </tbody>
              </table>
            </div>
            <div class="section-caption">
              <h2>{{ tr("收款与冲正", "Payments & reversals") }}</h2>
            </div>
            <div class="table-panel">
              <table>
                <thead>
                  <tr>
                    <th>{{ tr("凭据", "Reference") }}</th>
                    <th>{{ tr("金额", "Amount") }}</th>
                    <th>{{ tr("登记时间", "Recorded") }}</th>
                    <th>{{ tr("状态", "State") }}</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="p in detail.payments" :key="p.id">
                    <td>
                      {{ p.reference
                      }}<small v-if="p.reversedBy" class="cell-note"
                        >{{ p.reversalReference }} · {{ p.reversalNote }}</small
                      >
                    </td>
                    <td>¥ {{ money(p.amount) }}</td>
                    <td>{{ new Date(p.createdAt).toLocaleString() }}</td>
                    <td>
                      {{
                        p.reversedBy
                          ? tr("已冲正", "Reversed")
                          : tr("有效", "Active")
                      }}
                    </td>
                    <td>
                      <button
                        v-if="
                          can('payment.write') &&
                          !hotel &&
                          !p.reversedBy &&
                          p.actorId !== profile.id
                        "
                        class="text-button"
                        @click="command('reverse', null, p)"
                      >
                        {{ tr("独立冲正", "Reverse") }}
                      </button>
                    </td>
                  </tr>
                  <tr v-if="!detail.payments.length">
                    <td colspan="5" class="empty">
                      {{ tr("尚无收款记录", "No payments recorded") }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div></template
          >
          <div class="section-caption">
            <h2>{{ tr("流转记录", "History") }}</h2>
            <span>{{
              tr(
                "记录保留原始时间和操作账号",
                "Original timestamps and actor IDs retained",
              )
            }}</span>
          </div>
          <div class="timeline">
            <article v-for="e in [...detail.events].reverse()" :key="e.id">
              <span class="timeline-dot"></span>
              <div>
                <strong>{{
                  actions[e.action]
                    ? tr(...actions[e.action])
                    : e.action === "SAVE"
                      ? tr("保存草稿", "Draft saved")
                      : tr("创建账单", "Statement created")
                }}</strong>
                <p>{{ e.note }}</p>
                <small
                  >{{ new Date(e.createdAt).toLocaleString() }} ·
                  {{ tr("账号编号", "Actor ID") }} {{ e.actorId }}</small
                >
              </div>
            </article>
          </div>
        </template>
        <template v-if="!detail && page === 'catalog'"
          ><div class="tabs">
            <button
              v-for="t in ['customers', 'items', 'rates']"
              :key="t"
              :class="{ active: catalogType === t }"
              @click="
                catalogType = t;
                run(load);
              "
            >
              {{
                {
                  customers: tr("酒店", "Hotels"),
                  items: tr("布草品类", "Linen items"),
                  rates: tr("约定单价", "Agreed rates"),
                }[t]
              }}
            </button>
          </div>
          <div class="table-panel">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>{{ tr("名称／酒店", "Name / Hotel") }}</th>
                  <th>{{ tr("部门／品类", "Department / Item") }}</th>
                  <th>
                    {{
                      catalogType === "rates"
                        ? tr("单价及凭据", "Rate & reference")
                        : tr("状态", "State")
                    }}
                  </th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in rows" :key="r.id">
                  <td>{{ r.id }}</td>
                  <td>{{ r.name || names("customers", r.customerId) }}</td>
                  <td>
                    {{
                      r.departmentId
                        ? names("departments", r.departmentId)
                        : r.itemId
                          ? names("items", r.itemId)
                          : r.category
                    }}
                  </td>
                  <td>
                    <span v-if="catalogType === 'rates'"
                      >¥ {{ money(r.unitPrice)
                      }}<small class="cell-note">{{ r.reference }}</small></span
                    ><span
                      v-else
                      :class="['status', r.enabled ? 'completed' : 'cancelled']"
                      >{{
                        r.enabled
                          ? tr("启用", "Active")
                          : tr("停用", "Inactive")
                      }}</span
                    >
                  </td>
                  <td>
                    <button
                      v-if="catalogType !== 'items' || profile.scope === 'ALL'"
                      class="text-button"
                      @click="openSheet('catalog', catalogType, r)"
                    >
                      {{ tr("编辑", "Edit") }}</button
                    ><button
                      v-if="catalogType !== 'items' || profile.scope === 'ALL'"
                      class="text-button danger"
                      @click="remove(r)"
                    >
                      {{ tr("删除", "Delete") }}
                    </button>
                  </td>
                </tr>
                <tr v-if="!rows.length">
                  <td colspan="5" class="empty">
                    {{ tr("尚无资料", "No records yet") }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div></template
        >
        <template v-if="!detail && adminPage"
          ><div class="table-panel">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>{{ tr("名称", "Name") }}</th>
                  <th>{{ tr("范围／配置", "Scope / Configuration") }}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in rows" :key="r.id">
                  <td>{{ r.id }}</td>
                  <td>
                    {{ r.displayName || r.name || r.code
                    }}<small class="cell-note">{{
                      r.username || r.code
                    }}</small>
                  </td>
                  <td>
                    <template v-if="page === 'users'"
                      >{{ names("departments", r.departmentId) }} ·
                      {{ names("roles", r.roleId)
                      }}<span v-if="r.customerId">
                        · {{ names("customers", r.customerId) }}</span
                      ><span
                        :class="[
                          'status',
                          r.enabled ? 'completed' : 'cancelled',
                        ]"
                        >{{
                          r.enabled
                            ? tr("启用", "Active")
                            : tr("停用", "Inactive")
                        }}</span
                      ></template
                    ><template v-else-if="page === 'roles'"
                      >{{ r.scope
                      }}<small class="cell-note">{{
                        r.permissions.join(" · ")
                      }}</small></template
                    ><template v-else>{{
                      r.value || r.nameEn || "—"
                    }}</template>
                  </td>
                  <td>
                    <button
                      class="text-button"
                      @click="openSheet('admin', page, r)"
                    >
                      {{ tr("编辑", "Edit") }}</button
                    ><button
                      v-if="creatable.includes(page)"
                      class="text-button danger"
                      @click="remove(r)"
                    >
                      {{ tr("删除", "Delete") }}
                    </button>
                  </td>
                </tr>
                <tr v-if="!rows.length">
                  <td colspan="4" class="empty">
                    {{ tr("尚无资料", "No records yet") }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div></template
        >
        <template v-if="!detail && page === 'audit'"
          ><div class="table-panel">
            <table>
              <thead>
                <tr>
                  <th>{{ tr("时间", "Time") }}</th>
                  <th>{{ tr("账号", "Actor") }}</th>
                  <th>{{ tr("动作", "Action") }}</th>
                  <th>{{ tr("对象编号", "Object") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in rows" :key="r.id">
                  <td>{{ new Date(r.createdAt).toLocaleString() }}</td>
                  <td>{{ r.actor }}</td>
                  <td>{{ r.action }}</td>
                  <td>{{ r.objectId }}</td>
                </tr>
              </tbody>
            </table>
          </div></template
        >
        <template v-if="!detail && page === 'dashboard' && stats"
          ><div class="metrics">
            <article>
              <span>{{ tr("实收件数", "Received") }}</span
              ><strong>{{ stats.received }}</strong>
            </article>
            <article>
              <span>{{ tr("已签收件数", "Accepted") }}</span
              ><strong>{{ stats.signed }}</strong>
            </article>
            <article>
              <span>{{ tr("当前待返洗", "Rewash pending") }}</span
              ><strong>{{ stats.rewash }}</strong>
            </article>
            <article>
              <span>{{ tr("累计报损", "Discarded") }}</span
              ><strong>{{ stats.discard }}</strong>
            </article>
          </div>
          <div class="dashboard-grid">
            <article class="chart-panel">
              <h2>{{ tr("批次状态分布", "Batch states") }}</h2>
              <div v-for="(n, s) in stats.states" :key="s" class="chart-row">
                <span>{{ status(s) }}</span>
                <div>
                  <i
                    :style="{
                      width:
                        Math.max(2, (n / Math.max(1, stats.batches)) * 100) +
                        '%',
                    }"
                  ></i>
                </div>
                <strong>{{ n }}</strong>
              </div>
              <p v-if="!stats.batches" class="empty">
                {{ tr("尚无送洗批次", "No laundry batches yet") }}
              </p>
            </article>
            <article class="chart-panel">
              <h2>
                {{ tr("确认账单与收款", "Confirmed statements & payments") }}
              </h2>
              <div class="finance-stat">
                <span>{{ tr("已确认账单金额", "Confirmed amount") }}</span
                ><strong>¥ {{ money(stats.confirmedAmount) }}</strong>
              </div>
              <div class="finance-stat">
                <span>{{ tr("实际净收款", "Net payments") }}</span
                ><strong>¥ {{ money(stats.paid) }}</strong>
              </div>
              <div class="finance-stat">
                <span>{{ tr("未收余额", "Outstanding") }}</span
                ><strong>¥ {{ money(stats.balance) }}</strong>
              </div>
              <p class="muted">
                {{
                  tr(
                    "只统计当前可访问的已确认账单，冲正不计入净收款。",
                    "Only accessible confirmed statements; reversed payments excluded.",
                  )
                }}
              </p>
            </article>
          </div></template
        >
      </section>
      <footer>
        知华科技 ·
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >zhuatech.cn</a
        >
        · {{ tr("商业咨询", "Consulting") }} zhuatech / zhuatech2
      </footer>
    </main>
  </div>
  <div
    v-if="sheet && profile"
    class="modal-backdrop"
    @click.self="!busy && (sheet = null)"
  >
    <section
      class="modal"
      :class="{ wide: ['batch', 'command'].includes(sheet.kind) }"
      role="dialog"
      aria-modal="true"
      :aria-label="sheetTitle"
    >
      <div class="modal-heading">
        <h2>
          {{
            sheet.kind === "about"
              ? tr("关于 LinenFlow", "About LinenFlow")
              : sheet.kind === "delete"
                ? tr("确认删除", "Confirm deletion")
                : sheetTitle
          }}
        </h2>
        <button
          class="icon"
          :disabled="busy"
          :aria-label="tr('关闭', 'Close')"
          @click="sheet = null"
        >
          <X :size="20" />
        </button>
      </div>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <template v-if="sheet.kind === 'about'"
        ><div class="about-brand">
          <img src="/brand/logo.jpg" alt="知华科技" /><strong
            >LinenFlow 0.1.0</strong
          >
        </div>
        <p>
          {{
            tr(
              "公开源码学习版／非商业源码版，未经书面授权不得商用。",
              "Non-commercial source edition. Written authorization is required for commercial use.",
            )
          }}
        </p>
        <p>上海如静知华信息科技有限公司</p>
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >https://www.zhuatech.cn/</a
        >
        <div class="qr-row">
          <figure>
            <img src="/brand/wechat-zhuatech.png" alt="微信 zhuatech" />
            <figcaption>zhuatech</figcaption>
          </figure>
          <figure>
            <img src="/brand/wechat-zhuatech2.png" alt="微信 zhuatech2" />
            <figcaption>zhuatech2</figcaption>
          </figure>
        </div>
        <p class="muted">
          {{
            tr("Vue 与 Lucide 版权声明", "Vue and Lucide license notices")
          }}：<a href="/third-party/vue.txt" target="_blank">Vue</a> ·
          <a href="/third-party/lucide.txt" target="_blank">Lucide</a>
        </p></template
      >
      <template v-else-if="sheet.kind === 'delete'"
        ><p>
          {{
            tr(
              "删除仅适用于未被引用的资料或未送出的草稿。",
              "Only unreferenced records or unsubmitted drafts can be deleted.",
            )
          }}
        </p>
        <div class="modal-actions">
          <button class="secondary" @click="sheet = null">
            {{ tr("返回", "Back") }}</button
          ><button
            class="primary danger-solid"
            :disabled="busy"
            @click="confirmDelete"
          >
            {{ tr("确认删除", "Delete") }}
          </button>
        </div></template
      >
      <form v-else @submit.prevent="save">
        <template v-if="sheet.kind === 'batch'"
          ><div class="form-grid">
            <label
              >{{ tr("酒店", "Hotel")
              }}<select
                v-model="form.customerId"
                :disabled="!!sheet.id || hotel"
                required
              >
                <option
                  v-for="c in options.customers.filter(
                    (c) => c.enabled || c.id === form.customerId,
                  )"
                  :key="c.id"
                  :value="c.id"
                >
                  {{ c.name }}
                </option>
              </select></label
            ><label
              >{{ tr("送洗日期", "Service date")
              }}<input v-model="form.serviceDate" type="date" required
            /></label>
          </div>
          <div class="form-lines">
            <div class="line-heading">
              <h3>{{ tr("声明送洗数量", "Declared linen") }}</h3>
              <button
                type="button"
                class="text-button"
                :disabled="form.lines.length >= 50"
                @click="form.lines.push({ itemId: null, quantity: 1 })"
              >
                <Plus :size="16" />{{ tr("添加布草", "Add item") }}
              </button>
            </div>
            <div v-for="(l, i) in form.lines" :key="i" class="draft-line">
              <label
                >{{ tr("布草", "Linen")
                }}<select v-model="l.itemId" required>
                  <option :value="null" disabled>
                    {{ tr("选择布草", "Select linen") }}
                  </option>
                  <option
                    v-for="item in options.items.filter((item) => item.enabled)"
                    :key="item.id"
                    :value="item.id"
                  >
                    {{ item.name }}
                  </option>
                </select></label
              ><label
                >{{ tr("件数", "Pieces")
                }}<input
                  v-model.number="l.quantity"
                  type="number"
                  min="1"
                  max="1000000"
                  step="1"
                  required /></label
              ><button
                type="button"
                class="icon"
                :disabled="form.lines.length === 1"
                :aria-label="tr('删除这一行', 'Remove line')"
                @click="form.lines.splice(i, 1)"
              >
                <X :size="17" />
              </button>
            </div>
          </div>
          <label
            >{{ tr("送洗备注", "Service note")
            }}<textarea v-model="form.note" maxlength="1000" rows="3" /></label
        ></template>
        <template v-else-if="sheet.kind === 'invoice'"
          ><label
            >{{ tr("酒店", "Hotel")
            }}<select v-model="form.customerId" required>
              <option v-for="c in options.customers" :key="c.id" :value="c.id">
                {{ c.name }}
              </option>
            </select></label
          ><label
            >{{ tr("批次完成月份", "Batch completion month")
            }}<input v-model="form.period" type="month" required
          /></label>
          <p class="muted">
            {{
              tr(
                "汇集该酒店这个月已确认完成、尚未计费的全部批次。",
                "Includes all completed, unbilled batches for this hotel and month.",
              )
            }}
          </p>
          <label
            >{{ tr("对账说明", "Statement note")
            }}<textarea
              v-model="form.note"
              required
              maxlength="1000"
              rows="3"
            /></label
        ></template>
        <template v-else-if="sheet.kind === 'command'"
          ><div
            v-if="
              ['count', 'inspect', 'dispatch', 'sign', 'return'].includes(
                sheet.action,
              )
            "
            class="command-lines"
          >
            <div v-for="l in form.lines" :key="l.lineId" class="command-line">
              <strong>{{ l.name }}</strong
              ><template v-if="sheet.action === 'inspect'"
                ><span class="muted"
                  >{{ tr("实收", "Received") }} {{ l.receivedQty }}</span
                >
                <div class="quality-inputs">
                  <label
                    >{{ tr("合格", "Good")
                    }}<input
                      v-model.number="l.good"
                      type="number"
                      :min="l.previousGood"
                      :max="l.receivedQty"
                      step="1"
                      required /></label
                  ><label
                    >{{ tr("返洗", "Rewash")
                    }}<input
                      v-model.number="l.rewash"
                      type="number"
                      min="0"
                      :max="l.receivedQty"
                      step="1"
                      required /></label
                  ><label
                    >{{ tr("报损", "Discard")
                    }}<input
                      v-model.number="l.discard"
                      type="number"
                      :min="l.previousDiscard"
                      :max="l.receivedQty"
                      step="1"
                      required
                  /></label>
                </div>
                <span :class="balanced(l) ? 'balanced' : 'unbalanced'">{{
                  balanced(l)
                    ? tr("数量平衡", "Balanced")
                    : tr("请核对三个去向的合计", "Check disposition totals")
                }}</span></template
              ><label v-else
                >{{
                  sheet.action === "count"
                    ? tr("工厂实收件数", "Received pieces")
                    : sheet.action === "sign"
                      ? tr("酒店实际签收件数", "Accepted pieces")
                      : sheet.action === "return"
                        ? tr(
                            "工厂实际收到的退回件数",
                            "Returned pieces physically received",
                          )
                        : tr("本次发出件数", "Dispatch pieces")
                }}<input
                  v-model.number="l.quantity"
                  type="number"
                  min="0"
                  :max="
                    sheet.action === 'sign'
                      ? l.sentQty
                      : sheet.action === 'dispatch'
                        ? detail.available[l.lineId]
                        : 1000000
                  "
                  step="1"
                  required
                  :readonly="sheet.action === 'return'"
              /></label>
            </div>
          </div>
          <label v-if="['dispatch', 'pay', 'reverse'].includes(sheet.action)"
            >{{
              sheet.action === "dispatch"
                ? tr("配送交接凭据", "Delivery reference")
                : tr(
                    "已发生的收款／冲正凭据",
                    "External payment / reversal reference",
                  )
            }}<input v-model="form.reference" required maxlength="200" /></label
          ><label v-if="sheet.action === 'pay'"
            >{{ tr("已收到金额（元）", "Payment received (CNY)")
            }}<input
              v-model.number="form.amount"
              type="number"
              min="0.01"
              step="0.01"
              :max="detail.balance"
              required
          /></label>
          <p v-if="sheet.action === 'return'" class="muted">
            {{
              tr(
                "核对以上拒收实物已全部退回工厂后登记，部分退回不能释放本笔预留。",
                "Record only after all refused items above physically return to the laundry. Partial returns do not release this reservation.",
              )
            }}
          </p>
          <p v-if="sheet.action === 'complete'" class="muted">
            {{
              tr(
                "请核对全部合格布草已签收，以及报损记录和处理结果。",
                "Review all accepted good linen, discarded quantities and disposition records.",
              )
            }}
          </p>
          <label
            >{{ tr("核对说明／处理凭据", "Review note / evidence")
            }}<textarea
              v-model="form.note"
              required
              maxlength="1000"
              rows="3"
            /></label
        ></template>
        <template v-else-if="sheet.kind === 'password'"
          ><label
            >{{ tr("旧密码", "Current password")
            }}<input
              v-model="form.oldPassword"
              type="password"
              required
              autocomplete="current-password" /></label
          ><label
            >{{ tr("新密码", "New password")
            }}<input
              v-model="form.newPassword"
              type="password"
              required
              minlength="12"
              autocomplete="new-password" /></label
        ></template>
        <template v-else
          ><div class="form-grid">
            <label
              v-for="f in fields"
              :key="f[0]"
              :class="{ 'span-all': f[3] === 'permissions' }"
              >{{ tr(f[1], f[2]) }}
              <div v-if="f[3] === 'permissions'" class="permission-list">
                <label v-for="p in directories.permissions" :key="p.id"
                  ><input
                    v-model="form.permissions"
                    type="checkbox"
                    :value="p.code"
                  />{{ p.name }}<small>{{ p.code }}</small></label
                >
              </div>
              <input
                v-else-if="f[3] === 'boolean'"
                v-model="form[f[0]]"
                type="checkbox" /><select
                v-else-if="
                  f[3] && !['password', 'number', 'money'].includes(f[3])
                "
                v-model="form[f[0]]"
                :required="f[0] !== 'customerId' || sheet.type !== 'users'"
              >
                <option
                  v-if="f[0] === 'customerId' && sheet.type === 'users'"
                  :value="null"
                >
                  {{ tr("工厂人员（不绑定）", "Laundry staff (unbound)") }}
                </option>
                <option
                  v-for="r in fieldOptions(f[3])"
                  :key="r.id"
                  :value="r.id"
                >
                  {{ r.name || r.displayName }}
                </option></select
              ><input
                v-else
                v-model="form[f[0]]"
                :type="
                  f[3] === 'password'
                    ? 'password'
                    : ['number', 'money'].includes(f[3])
                      ? 'number'
                      : 'text'
                "
                :step="f[3] === 'money' ? '0.01' : '1'"
                :required="f[0] !== 'password' || !sheet.id"
                :maxlength="
                  f[0] === 'value' ? 200 : f[0] === 'reference' ? 200 : 120
                "
                :disabled="
                  !!sheet.id &&
                  sheet.type === 'dictionaries' &&
                  ['type', 'code'].includes(f[0])
                "
                autocomplete="off"
            /></label></div
        ></template>
        <div class="modal-actions">
          <button
            type="button"
            class="secondary"
            :disabled="busy"
            @click="sheet = null"
          >
            {{ tr("返回", "Back") }}</button
          ><button class="primary" :disabled="busy">
            {{
              busy
                ? tr("正在保存…", "Saving…")
                : sheet.kind === "command"
                  ? tr(...actions[sheet.action])
                  : tr("保存", "Save")
            }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>

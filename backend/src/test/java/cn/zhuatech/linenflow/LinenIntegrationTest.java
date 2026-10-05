// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.linenflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 实际HTTP/JPA验证全流程、计价快照、并发、身份边界和冲正。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(LinenIntegrationTest.TimeConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LinenIntegrationTest {
  static final String password = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("linenflow.admin-password", () -> password);
  }

  /** 测试上海月份边界的可控时钟。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  static class MutableClock extends Clock {
    volatile Instant value = Instant.parse("2026-10-05T02:00:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId z) {
      return Clock.fixed(value, z);
    }

    public Instant instant() {
      return value;
    }
  }

  /** 隔离时钟。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  @Autowired MockMvc mvc;
  @Autowired MutableClock clock;
  final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
  MockHttpSession admin, ops, qc, driver, finance, finance2, hotel, otherHotel, outside;
  long item, customer, rate, id, hotelId, otherCustomer, otherDep, opsId, hotelRole;
  String suffix;
  Map<String, Long> roles = new HashMap<>();

  @BeforeAll
  void identity() throws Exception {
    suffix = key().substring(0, 8);
    admin = login("admin");
    for (var r : ok(admin, "GET", "/admin/roles", null))
      roles.put(r.path("name").asString(), r.path("id").asLong());
    otherDep =
        ok(admin, "POST", "/admin/departments", Map.of("name", "TEST outside " + suffix))
            .path("id")
            .asLong();
    otherCustomer =
        ok(
                admin,
                "POST",
                "/catalog/customers",
                Map.of("name", "TEST other hotel " + suffix, "departmentId", 1, "enabled", true))
            .path("id")
            .asLong();
    item =
        ok(
                admin,
                "POST",
                "/catalog/items",
                Map.of("name", "TEST towel " + suffix, "category", "BATH", "enabled", true))
            .path("id")
            .asLong();
    opsId = user("ops", "收货与生产", 1, null);
    user("qc", "质检员", 1, null);
    user("driver", "配送员", 1, null);
    user("finance", "财务", 1, null);
    user("finance2", "财务", 1, null);
    hotelId = user("hotel", "酒店客户", 1, otherCustomer);
    user("otherhotel", "酒店客户", 1, otherCustomer);
    user("outside", "收货与生产", otherDep, null);
    hotelRole = roles.get("酒店客户");
    ops = login("ops-" + suffix);
    qc = login("qc-" + suffix);
    driver = login("driver-" + suffix);
    finance = login("finance-" + suffix);
    finance2 = login("finance2-" + suffix);
    hotel = login("hotel-" + suffix);
    otherHotel = login("otherhotel-" + suffix);
    outside = login("outside-" + suffix);
  }

  @BeforeEach
  void batch() throws Exception {
    clock.value = Instant.parse("2026-10-05T02:00:00Z");
    customer =
        ok(
                admin,
                "POST",
                "/catalog/customers",
                Map.of("name", "TEST hotel " + key(), "departmentId", 1, "enabled", true))
            .path("id")
            .asLong();
    rate =
        ok(
                admin,
                "POST",
                "/catalog/rates",
                Map.of(
                    "customerId",
                    customer,
                    "itemId",
                    item,
                    "unitPrice",
                    "2.50",
                    "reference",
                    "TEST agreed rate",
                    "enabled",
                    true))
            .path("id")
            .asLong();
    editUser(hotelId, Map.of("customerId", customer));
    id = ok(hotel, "POST", "/batches", input(10)).path("record").path("id").asLong();
  }

  String key() {
    return UUID.randomUUID().toString();
  }

  long user(String name, String role, long dep, Long binding) throws Exception {
    var data =
        new HashMap<String, Object>(
            Map.of(
                "username",
                name + "-" + suffix,
                "displayName",
                "TEST " + name,
                "password",
                password,
                "roleId",
                roles.get(role),
                "departmentId",
                dep,
                "enabled",
                true));
    if (binding != null) data.put("customerId", binding);
    return ok(admin, "POST", "/admin/users", data).path("id").asLong();
  }

  MockHttpSession login(String name) throws Exception {
    var res =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(Map.of("username", name, "password", password))))
            .andReturn();
    assertEquals(200, res.getResponse().getStatus(), res.getResponse().getContentAsString());
    return (MockHttpSession) res.getRequest().getSession(false);
  }

  MvcResult request(MockHttpSession who, String method, String path, Object body) throws Exception {
    var builder =
        switch (method) {
          case "POST" -> post("/api" + path);
          case "PUT" -> put("/api" + path);
          case "DELETE" -> delete("/api" + path);
          default -> get("/api" + path);
        };
    builder.session(who).with(csrf());
    if (body != null)
      builder.contentType("application/json").content(json.writeValueAsString(body));
    return mvc.perform(builder).andReturn();
  }

  JsonNode ok(MockHttpSession who, String method, String path, Object body) throws Exception {
    var r = request(who, method, path, body);
    assertEquals(
        200, r.getResponse().getStatus(), path + " " + r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  void fails(MockHttpSession who, String method, String path, Object body, int status, String code)
      throws Exception {
    var r = request(who, method, path, body);
    assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    assertEquals(code, json.readTree(r.getResponse().getContentAsString()).path("code").asString());
  }

  void editUser(long userId, Map<String, Object> changes) throws Exception {
    JsonNode u = null;
    for (var r : ok(admin, "GET", "/admin/users", null)) if (r.path("id").asLong() == userId) u = r;
    assertNotNull(u);
    var m = json.convertValue(u, Map.class);
    var data = new HashMap<String, Object>();
    m.forEach((k, v) -> data.put(k.toString(), v));
    data.putAll(changes);
    ok(admin, "PUT", "/admin/users/" + userId, data);
  }

  Map<String, Object> input(int qty) {
    return new HashMap<>(
        Map.of(
            "requestKey",
            key(),
            "customerId",
            customer,
            "serviceDate",
            clock.instant().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().toString(),
            "note",
            "TEST hotel-owned linen",
            "lines",
            List.of(Map.of("itemId", item, "quantity", qty))));
  }

  JsonNode detail() throws Exception {
    return ok(admin, "GET", "/batches/" + id, null);
  }

  long line() throws Exception {
    return detail().path("lines").get(0).path("id").asLong();
  }

  Map<String, Object> cmd() throws Exception {
    return new HashMap<>(
        Map.of(
            "requestKey",
            key(),
            "version",
            detail().path("record").path("version").asLong(),
            "note",
            "TEST checked physical evidence"));
  }

  JsonNode action(MockHttpSession who, String action, Map<String, Object> extra) throws Exception {
    var m = cmd();
    m.putAll(extra);
    return ok(who, "POST", "/batches/" + id + "/commands/" + action, m);
  }

  JsonNode action(MockHttpSession who, String action) throws Exception {
    return action(who, action, Map.of());
  }

  void ready() throws Exception {
    action(hotel, "submit");
    action(ops, "count", Map.of("lines", List.of(Map.of("lineId", line(), "quantity", 10))));
    action(hotel, "accept-count");
    action(ops, "wash");
    action(
        qc,
        "inspect",
        Map.of("lines", List.of(Map.of("lineId", line(), "good", 10, "rewash", 0, "discard", 0))));
  }

  void completed() throws Exception {
    ready();
    action(
        driver,
        "dispatch",
        Map.of(
            "reference",
            "TEST delivery " + key(),
            "lines",
            List.of(Map.of("lineId", line(), "quantity", 10))));
    String ref = detail().path("deliveries").get(0).path("record").path("reference").asString();
    action(
        hotel,
        "sign",
        Map.of("reference", ref, "lines", List.of(Map.of("lineId", line(), "quantity", 10))));
    action(hotel, "complete");
  }

  long invoice() throws Exception {
    return ok(
            finance,
            "POST",
            "/invoices",
            Map.of(
                "requestKey",
                key(),
                "customerId",
                customer,
                "period",
                YearMonth.from(clock.instant().atZone(ZoneId.of("Asia/Shanghai"))).toString(),
                "note",
                "TEST monthly reconciliation"))
        .path("record")
        .path("id")
        .asLong();
  }

  Map<String, Object> invoiceCmd(long inv) throws Exception {
    return new HashMap<>(
        Map.of(
            "requestKey",
            key(),
            "version",
            ok(finance, "GET", "/invoices/" + inv, null).path("record").path("version").asLong(),
            "note",
            "TEST external payment fact"));
  }

  JsonNode invoiceAction(MockHttpSession who, long inv, String action, Map<String, Object> extra)
      throws Exception {
    var m = invoiceCmd(inv);
    m.putAll(extra);
    return ok(who, "POST", "/invoices/" + inv + "/commands/" + action, m);
  }

  @Test
  void fullRewashDeliveryBillingAndIndependentReversal() throws Exception {
    action(hotel, "submit");
    action(ops, "count", Map.of("lines", List.of(Map.of("lineId", line(), "quantity", 10))));
    action(hotel, "accept-count");
    action(ops, "wash");
    action(
        qc,
        "inspect",
        Map.of("lines", List.of(Map.of("lineId", line(), "good", 7, "rewash", 2, "discard", 1))));
    assertEquals("REWASH", detail().path("record").path("status").asString());
    action(ops, "wash");
    action(
        qc,
        "inspect",
        Map.of("lines", List.of(Map.of("lineId", line(), "good", 9, "rewash", 0, "discard", 1))));
    String ref = "TEST ship " + key();
    action(
        driver,
        "dispatch",
        Map.of("reference", ref, "lines", List.of(Map.of("lineId", line(), "quantity", 9))));
    action(
        hotel,
        "sign",
        Map.of("reference", ref, "lines", List.of(Map.of("lineId", line(), "quantity", 9))));
    action(hotel, "complete");
    long inv = invoice();
    assertEquals(
        22.50,
        ok(finance, "GET", "/invoices/" + inv, null).path("record").path("amount").asDouble());
    invoiceAction(finance, inv, "issue", Map.of());
    invoiceAction(hotel, inv, "confirm", Map.of());
    var paid =
        invoiceAction(
            finance, inv, "pay", Map.of("amount", 22.5, "reference", "TEST receipt " + key()));
    assertEquals("PAID", paid.path("record").path("status").asString());
    long payment = paid.path("payments").get(0).path("id").asLong();
    var body = invoiceCmd(inv);
    body.putAll(Map.of("paymentId", payment, "reference", "TEST reverse " + key()));
    fails(
        finance,
        "POST",
        "/invoices/" + inv + "/commands/reverse",
        body,
        403,
        "INDEPENDENT_REVERSAL");
    invoiceAction(
        finance2,
        inv,
        "reverse",
        Map.of("paymentId", payment, "reference", "TEST reversal " + key()));
    assertEquals(0, ok(finance, "GET", "/invoices/" + inv, null).path("paid").asDouble());
    invoiceAction(
        finance, inv, "pay", Map.of("amount", 22.5, "reference", "TEST replacement " + key()));
    assertEquals(2, ok(finance, "GET", "/invoices/" + inv, null).path("payments").size());
  }

  @Test
  void submissionFreezesRateAndItemName() throws Exception {
    action(hotel, "submit");
    ok(
        finance,
        "PUT",
        "/catalog/rates/" + rate,
        Map.of(
            "customerId",
            customer,
            "itemId",
            item,
            "unitPrice",
            9,
            "reference",
            "TEST later rate",
            "enabled",
            true));
    assertEquals(2.50, detail().path("lines").get(0).path("unitPrice").asDouble());
  }

  @Test
  void hotelAndDepartmentIsolationProtectDetailExportAndOptions() throws Exception {
    fails(otherHotel, "GET", "/batches/" + id, null, 403, "OUT_OF_SCOPE");
    fails(otherHotel, "GET", "/batches/" + id + "/report.json", null, 403, "OUT_OF_SCOPE");
    fails(outside, "GET", "/batches/" + id, null, 403, "OUT_OF_SCOPE");
    assertEquals(0, ok(otherHotel, "GET", "/batches", null).path("total").asLong());
    var opts = ok(otherHotel, "GET", "/options", null);
    assertEquals(1, opts.path("customers").size());
    assertEquals(otherCustomer, opts.path("customers").get(0).path("id").asLong());
  }

  @Test
  void boundHotelCannotUseFullScopeOrAdminToEscapeBinding() throws Exception {
    var custom =
        ok(
            admin,
            "POST",
            "/admin/roles",
            Map.of(
                "name",
                "TEST bound admin " + key(),
                "scope",
                "ALL",
                "permissions",
                List.of("batch.read", "admin", "catalog.write", "audit")));
    editUser(hotelId, Map.of("roleId", custom.path("id").asLong()));
    fails(hotel, "GET", "/admin/users", null, 403, "STAFF_ONLY");
    fails(hotel, "GET", "/audit", null, 403, "STAFF_ONLY");
    fails(hotel, "GET", "/batches/" + id + "/report.json", null, 403, "FORBIDDEN");
    assertEquals(1, ok(hotel, "GET", "/options", null).path("customers").size());
    editUser(hotelId, Map.of("roleId", hotelRole));
  }

  @Test
  void anonymousAndCsrfWritesAreRejected() throws Exception {
    assertEquals(401, mvc.perform(get("/api/batches")).andReturn().getResponse().getStatus());
    assertEquals(
        403,
        mvc.perform(
                post("/api/batches")
                    .session(hotel)
                    .contentType("application/json")
                    .content(json.writeValueAsString(input(10))))
            .andReturn()
            .getResponse()
            .getStatus());
    assertEquals(
        401,
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of("username", "admin", "password", "invalid"))))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void integerAndRowValidationRejectFractionDuplicateAndNegative() throws Exception {
    var fractional = input(10);
    fractional.put("lines", List.of(Map.of("itemId", item, "quantity", 1.5)));
    fails(hotel, "POST", "/batches", fractional, 400, "INVALID_INPUT");
    var duplicate = input(10);
    duplicate.put(
        "lines",
        List.of(Map.of("itemId", item, "quantity", 1), Map.of("itemId", item, "quantity", 1)));
    fails(hotel, "POST", "/batches", duplicate, 400, "INVALID_LINES");
    action(hotel, "submit");
    var m = cmd();
    m.put("lines", List.of(Map.of("lineId", line(), "quantity", -1)));
    fails(ops, "POST", "/batches/" + id + "/commands/count", m, 400, "INVALID_QUANTITY");
  }

  @Test
  void inspectorMustDifferFromCurrentWashOperator() throws Exception {
    action(hotel, "submit");
    action(ops, "count", Map.of("lines", List.of(Map.of("lineId", line(), "quantity", 10))));
    action(hotel, "accept-count");
    action(admin, "wash");
    var m = cmd();
    m.put("lines", List.of(Map.of("lineId", line(), "good", 10, "rewash", 0, "discard", 0)));
    fails(admin, "POST", "/batches/" + id + "/commands/inspect", m, 403, "INDEPENDENT_INSPECTOR");
  }

  @Test
  void independentHotelConfirmationCannotBeReplacedByAdministrator() throws Exception {
    action(hotel, "submit");
    action(ops, "count", Map.of("lines", List.of(Map.of("lineId", line(), "quantity", 9))));
    fails(admin, "POST", "/batches/" + id + "/commands/accept-count", cmd(), 403, "HOTEL_ONLY");
    action(hotel, "reject-count");
    assertEquals("DECLARED", detail().path("record").path("status").asString());
    fails(ops, "POST", "/batches/" + id + "/commands/wash", cmd(), 409, "INVALID_STATE");
  }

  @Test
  void persistedRetryResponseMatchesInitialMicrosecondTimestamp() throws Exception {
    clock.value = Instant.parse("2026-10-05T02:00:00.345678805Z");
    var command = cmd();
    var first = ok(hotel, "POST", "/batches/" + id + "/commands/submit", command);
    var again = ok(hotel, "POST", "/batches/" + id + "/commands/submit", command);
    assertEquals(first, again);
    var events = first.path("events");
    assertEquals(
        "2026-10-05T02:00:00.345678Z", events.get(events.size() - 1).path("createdAt").asString());
  }

  @Test
  void cancellationIsOnlyBeforeActualReceipt() throws Exception {
    action(hotel, "submit");
    action(ops, "count", Map.of("lines", List.of(Map.of("lineId", line(), "quantity", 10))));
    fails(hotel, "POST", "/batches/" + id + "/commands/cancel", cmd(), 409, "INVALID_STATE");
    fails(hotel, "DELETE", "/batches/" + id, cmd(), 409, "INVALID_STATE");
  }

  @Test
  void refusedDeliveryRemainsReservedUntilIndependentPhysicalReturn() throws Exception {
    ready();
    String ref = "TEST refused " + key();
    action(
        driver,
        "dispatch",
        Map.of("reference", ref, "lines", List.of(Map.of("lineId", line(), "quantity", 10))));
    action(
        hotel,
        "sign",
        Map.of("reference", ref, "lines", List.of(Map.of("lineId", line(), "quantity", 8))));
    assertEquals(0, detail().path("available").path(Long.toString(line())).asInt());
    var ship = cmd();
    ship.putAll(
        Map.of(
            "reference",
            "TEST extra " + key(),
            "lines",
            List.of(Map.of("lineId", line(), "quantity", 2))));
    fails(driver, "POST", "/batches/" + id + "/commands/dispatch", ship, 409, "EXCESS_DELIVERY");
    var wrong = cmd();
    wrong.putAll(
        Map.of("reference", ref, "lines", List.of(Map.of("lineId", line(), "quantity", 1))));
    fails(ops, "POST", "/batches/" + id + "/commands/return", wrong, 400, "QUANTITY_BALANCE");
    action(
        ops,
        "return",
        Map.of("reference", ref, "lines", List.of(Map.of("lineId", line(), "quantity", 2))));
    assertEquals(2, detail().path("available").path(Long.toString(line())).asInt());
  }

  @Test
  void duplicateUuidIsExactRetryAndStaleVersionCannotOverwrite() throws Exception {
    var body = cmd();
    var a = ok(hotel, "POST", "/batches/" + id + "/commands/submit", body);
    var b = ok(hotel, "POST", "/batches/" + id + "/commands/submit", body);
    assertEquals(a.path("record").path("version"), b.path("record").path("version"));
    body.put("note", "changed payload");
    fails(hotel, "POST", "/batches/" + id + "/commands/submit", body, 409, "REQUEST_KEY_REUSED");
    var stale = cmd();
    stale.put("version", 0);
    fails(hotel, "POST", "/batches/" + id + "/commands/cancel", stale, 409, "STALE_VERSION");
  }

  @Test
  void concurrentDispatchAllowsOnlyOneReservation() throws Exception {
    ready();
    var a = cmd();
    a.putAll(
        Map.of(
            "reference",
            "TEST concurrent A " + key(),
            "lines",
            List.of(Map.of("lineId", line(), "quantity", 10))));
    var b = new HashMap<>(a);
    b.put("requestKey", key());
    b.put("reference", "TEST concurrent B " + key());
    try (var pool = Executors.newFixedThreadPool(2)) {
      var f1 =
          pool.submit(
              () ->
                  request(driver, "POST", "/batches/" + id + "/commands/dispatch", a)
                      .getResponse()
                      .getStatus());
      var f2 =
          pool.submit(
              () ->
                  request(driver, "POST", "/batches/" + id + "/commands/dispatch", b)
                      .getResponse()
                      .getStatus());
      var results = new ArrayList<>(List.of(f1.get(), f2.get()));
      Collections.sort(results);
      assertEquals(List.of(200, 409), results);
    }
    assertEquals(1, detail().path("deliveries").size());
  }

  @Test
  void invoiceDisputeAndCancellationReleaseBatchesButRetainHistory() throws Exception {
    completed();
    long inv = invoice();
    invoiceAction(finance, inv, "issue", Map.of());
    invoiceAction(hotel, inv, "dispute", Map.of());
    fails(
        finance,
        "POST",
        "/invoices/" + inv + "/commands/pay",
        invoiceCmd(inv),
        409,
        "INVALID_STATE");
    invoiceAction(finance, inv, "cancel", Map.of());
    assertEquals(1, ok(finance, "GET", "/invoices/" + inv, null).path("batches").size());
    assertTrue(detail().path("record").path("invoiceId").isNull());
    long replacement = invoice();
    assertNotEquals(inv, replacement);
    assertEquals(
        25,
        ok(finance, "GET", "/invoices/" + replacement, null)
            .path("record")
            .path("amount")
            .asDouble());
  }

  @Test
  void concurrentPaymentAndOverpaymentCannotExceedBill() throws Exception {
    completed();
    long inv = invoice();
    invoiceAction(finance, inv, "issue", Map.of());
    invoiceAction(hotel, inv, "confirm", Map.of());
    var a = invoiceCmd(inv);
    a.putAll(Map.of("amount", 20, "reference", "TEST pay A " + key()));
    var b = new HashMap<>(a);
    b.put("requestKey", key());
    b.put("reference", "TEST pay B " + key());
    try (var pool = Executors.newFixedThreadPool(2)) {
      var f1 =
          pool.submit(
              () ->
                  request(finance, "POST", "/invoices/" + inv + "/commands/pay", a)
                      .getResponse()
                      .getStatus());
      var f2 =
          pool.submit(
              () ->
                  request(finance2, "POST", "/invoices/" + inv + "/commands/pay", b)
                      .getResponse()
                      .getStatus());
      var results = new ArrayList<>(List.of(f1.get(), f2.get()));
      Collections.sort(results);
      assertEquals(List.of(200, 409), results);
    }
    assertEquals(20, ok(finance, "GET", "/invoices/" + inv, null).path("paid").asDouble());
  }

  @Test
  void liveAccountRevocationTakesEffectInExistingSession() throws Exception {
    editUser(opsId, Map.of("enabled", false));
    fails(ops, "GET", "/batches", null, 401, "UNAUTHENTICATED");
    editUser(opsId, Map.of("enabled", true));
    assertTrue(ok(ops, "GET", "/batches", null).path("total").asLong() > 0);
  }

  @Test
  void passwordResetInvalidatesOldSession() throws Exception {
    long temp = user("reset" + key().substring(0, 4), "收货与生产", 1, null);
    String username = null;
    for (var u : ok(admin, "GET", "/admin/users", null))
      if (u.path("id").asLong() == temp) username = u.path("username").asString();
    var session = login(username);
    editUser(temp, Map.of("password", "Aa9" + key()));
    fails(session, "GET", "/auth/me", null, 401, "UNAUTHENTICATED");
  }

  @Test
  void lastAdministratorIsProtected() throws Exception {
    JsonNode a = null;
    for (var u : ok(admin, "GET", "/admin/users", null))
      if (u.path("username").asString().equals("admin")) a = u;
    var body = json.convertValue(a, Map.class);
    body.put("enabled", false);
    fails(admin, "PUT", "/admin/users/" + a.path("id").asLong(), body, 409, "LAST_ADMIN");
    assertEquals("admin", ok(admin, "GET", "/auth/me", null).path("username").asString());
  }

  @Test
  void monthUsesShanghaiCompletionTimeAndCannotDoubleBill() throws Exception {
    clock.value = Instant.parse("2026-09-30T16:01:00Z");
    var dated = input(10);
    dated.put("version", detail().path("record").path("version").asLong());
    ok(hotel, "PUT", "/batches/" + id, dated);
    completed();
    long inv = invoice();
    assertEquals(
        "2026-10",
        ok(finance, "GET", "/invoices/" + inv, null).path("record").path("period").asString());
    fails(
        finance,
        "POST",
        "/invoices",
        Map.of(
            "requestKey",
            key(),
            "customerId",
            customer,
            "period",
            "2026-10",
            "note",
            "TEST duplicate"),
        409,
        "NO_ELIGIBLE_BATCHES");
  }

  @Test
  void referencedCustomerAndDisabledRateAreProtected() throws Exception {
    fails(admin, "DELETE", "/catalog/customers/" + customer, null, 409, "CONFLICT");
    fails(
        admin,
        "PUT",
        "/catalog/customers/" + customer,
        Map.of("name", "TEST moved", "departmentId", otherDep, "enabled", true),
        409,
        "IMMUTABLE_REFERENCE");
    ok(
        finance,
        "PUT",
        "/catalog/rates/" + rate,
        Map.of(
            "customerId",
            customer,
            "itemId",
            item,
            "unitPrice",
            2.5,
            "reference",
            "TEST disabled rate",
            "enabled",
            false));
    fails(hotel, "POST", "/batches/" + id + "/commands/submit", cmd(), 409, "RATE_MISSING");
    assertEquals("DRAFT", detail().path("record").path("status").asString());
  }

  @Test
  void impossibleInspectionRollsBackCountsAndEvents() throws Exception {
    action(hotel, "submit");
    action(ops, "count", Map.of("lines", List.of(Map.of("lineId", line(), "quantity", 10))));
    action(hotel, "accept-count");
    action(ops, "wash");
    var before = detail();
    var body = cmd();
    body.put("lines", List.of(Map.of("lineId", line(), "good", 9, "rewash", 1, "discard", 1)));
    fails(qc, "POST", "/batches/" + id + "/commands/inspect", body, 400, "QUANTITY_BALANCE");
    assertEquals(before, detail());
  }
}

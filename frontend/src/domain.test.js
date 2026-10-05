// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { test } from "node:test";
import assert from "node:assert/strict";
import { validQuantity, balanced, dispatchRows } from "./domain.js";
test("piece counts reject fractions, negatives, blank and out-of-range quantities", () => {
  for (const x of [-1, 0.5, "", NaN, 1000001])
    assert.equal(validQuantity(x), false);
  for (const x of [0, 1, 1000000, "12"]) assert.equal(validQuantity(x), true);
});
test("inspection balances intake and retains previously accepted dispositions", () => {
  const l = {
    receivedQty: 100,
    good: 90,
    rewash: 8,
    discard: 2,
    previousGood: 0,
    previousDiscard: 0,
  };
  assert.equal(balanced(l), true);
  assert.equal(balanced({ ...l, good: 89 }), false);
  assert.equal(balanced({ ...l, previousGood: 91 }), false);
  assert.equal(balanced({ ...l, previousDiscard: 3 }), false);
});
test("dispatch strips UI-only metadata and omits zero lines", () => {
  assert.deepEqual(
    dispatchRows([
      { lineId: 1, quantity: 0, name: "sheet" },
      { lineId: 2, quantity: 4, name: "towel" },
    ]),
    [{ lineId: 2, quantity: 4 }],
  );
});

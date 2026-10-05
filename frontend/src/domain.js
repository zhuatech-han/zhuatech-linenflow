// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 业务状态名称和表单数量规则；最终授权及计算仍由服务端执行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const statuses = {
  DRAFT: ["草稿", "Draft"],
  DECLARED: ["待收货", "Awaiting intake"],
  COUNTED: ["待酒店确认清点", "Count confirmation"],
  ACCEPTED: ["待洗涤", "Ready to wash"],
  WASHING: ["洗涤中", "Washing"],
  REWASH: ["待返洗", "Rewash required"],
  READY: ["待交付", "Ready for delivery"],
  DELIVERED: ["待完成确认", "Completion confirmation"],
  COMPLETED: ["已完成", "Completed"],
  CANCELLED: ["已取消", "Cancelled"],
  SENT: ["配送中", "In transit"],
  AWAITING_RETURN: ["待实物退回", "Awaiting physical return"],
  SIGNED: ["已签收", "Signed"],
  RETURNED: ["已核验退回", "Returned"],
  ISSUED: ["待酒店对账", "Awaiting confirmation"],
  DISPUTED: ["账单有异议", "Disputed"],
  CONFIRMED: ["待收款", "Awaiting payment"],
  PAID: ["已结清", "Paid"],
};
/** 整数件数输入不能转为小数或NaN。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function validQuantity(value) {
  return (
    Number.isInteger(Number(value)) &&
    Number(value) >= 0 &&
    Number(value) <= 1000000 &&
    String(value).trim() !== ""
  );
}
/** 质检三个去向严格合计实收量，历史合格和报损数不倒退。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function balanced(line) {
  return (
    [line.good, line.rewash, line.discard].every(validQuantity) &&
    Number(line.good) + Number(line.rewash) + Number(line.discard) ===
      line.receivedQty &&
    Number(line.good) >= line.previousGood &&
    Number(line.discard) >= line.previousDiscard
  );
}
/** 只发送真正有数量的配送行，防止零行或重复计数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function dispatchRows(rows) {
  return rows
    .filter((r) => Number(r.quantity) > 0)
    .map((r) => ({ lineId: r.lineId, quantity: Number(r.quantity) }));
}
/** 浏览器下载文件，URL用完后释放；不往业务导出内容插入广告。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function downloadJson(value, name) {
  const url = URL.createObjectURL(
    new Blob([JSON.stringify(value, null, 2)], { type: "application/json" }),
  );
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

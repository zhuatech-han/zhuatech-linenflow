#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Exercise real HTTP/MySQL workflows with synthetic TEST records. Never displays credentials."""
from pathlib import Path
import argparse, concurrent.futures, http.cookiejar, json, os, secrets, urllib.request, urllib.error, uuid
from datetime import datetime
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[1]
STATE = ROOT / 'output/qa-state.json'
BASE = os.environ.get('TEST_URL', 'http://127.0.0.1:8124').rstrip('/')
checks = 0

def check(value, message):
    """Assert a business invariant; report neither credentials nor whole responses."""
    global checks
    checks += 1
    if not value:
        raise AssertionError(message)

def key():
    return str(uuid.uuid4())

class Client:
    """Separate cookie-backed session per test actor; CSRF header always included on writes."""
    def __init__(self, username, password):
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf = self.request('/auth/csrf')
        self.profile = self.request('/auth/login', 'POST', {'username': username, 'password': password})

    def request(self, path, method='GET', data=None, status=200, code=None, csrf=True):
        headers = {'Content-Type': 'application/json'}
        if method != 'GET' and csrf and hasattr(self, 'csrf'):
            headers[self.csrf['header']] = self.csrf['token']
        req = urllib.request.Request(BASE + '/api' + path, data=None if data is None else json.dumps(data).encode(), headers=headers, method=method)
        try:
            with self.opener.open(req, timeout=30) as res:
                actual = res.status
                value = json.load(res)
        except urllib.error.HTTPError as e:
            actual = e.code
            value = json.load(e)
        check(actual in status if isinstance(status, tuple) else actual == status, f'{method} {path}: expected {status}, got {actual}, error={value.get("code") if isinstance(value,dict) else None}')
        if code:
            check(value.get('code') == code, f'{path}: wrong business error')
        return value

def command(client, batch, action, extra=None, status=200, code=None, payload=None):
    body = payload or {'requestKey': key(), 'version': admin.request(f'/batches/{batch}')['record']['version'], 'note': 'TEST physical handover and records checked'}
    body.update(extra or {})
    return client.request(f'/batches/{batch}/commands/{action}', 'POST', body, status, code)

def invoice_command(client, invoice, action, extra=None, status=200, code=None):
    body = {'requestKey': key(), 'version': admin.request(f'/invoices/{invoice}')['record']['version'], 'note': 'TEST checked external fact; no real transfer'}
    body.update(extra or {})
    return client.request(f'/invoices/{invoice}/commands/{action}', 'POST', body, status, code)

def draft(client, customer, rows):
    return client.request('/batches', 'POST', {'requestKey': key(), 'customerId': customer, 'serviceDate': datetime.now(ZoneInfo('Asia/Shanghai')).date().isoformat(), 'note': 'TEST customer-owned linen; isolated QA', 'lines': rows})['record']['id']

def capture(admin_client, state):
    """Persist deterministic responses privately, then compare again after restart/restore."""
    paths = ['/admin/users', '/admin/roles', '/admin/departments', '/admin/permissions', '/admin/menus', '/admin/settings', '/admin/dictionaries', '/options', '/dashboard', '/batches', '/invoices']
    paths += [f'/batches/{i}' for i in state['batchIds']] + [f'/invoices/{i}' for i in state['invoiceIds']]
    return {path: admin_client.request(path) for path in paths}

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--allow-test-writes', action='store_true')
parser.add_argument('--verify', action='store_true')
parser.add_argument('--capture', action='store_true', help='Capture the current isolated QA checkpoint after intentional UI changes')
args = parser.parse_args()
env = dict(line.split('=', 1) for line in (ROOT / '.env').read_text().splitlines() if '=' in line and not line.startswith('#'))
admin = Client('admin', env['ADMIN_PASSWORD'])

if args.verify or args.capture:
    state = json.loads(STATE.read_text())
    current = capture(admin, state)
    if args.capture:
        state['batchIds'] = [b['id'] for b in admin.request('/batches')['items']]
        current = capture(admin, state)
        state['responses'] = current
        STATE.write_text(json.dumps(state, ensure_ascii=False))
        print(json.dumps({'mode':'capture','responses':len(current),'result':'PASS'}))
        raise SystemExit(0)
    for path, expected in state['responses'].items():
        check(current[path] == expected, 'Persistence mismatch at ' + path)
    for name, username in state['users'].items():
        actor = Client(username, state['password'])
        check(actor.request('/auth/me')['username'] == username, 'QA actor missing after restart: ' + name)
    print(json.dumps({'mode': 'persistence', 'assertions': checks, 'responsesMatched': len(current), 'result': 'PASS'}))
    raise SystemExit(0)

if not args.allow_test_writes:
    raise SystemExit('Use --allow-test-writes only in a disposable, isolated test database.')
if STATE.exists():
    raise SystemExit('QA state already exists. Use --verify or a new isolated database.')

suffix = secrets.token_hex(4)
password = 'Aa9' + secrets.token_urlsafe(24)
roles = {r['name']: r['id'] for r in admin.request('/admin/roles')}
department = admin.request('/admin/departments', 'POST', {'name': 'TEST LinenFlow QA ' + suffix})['id']
outside_dep = admin.request('/admin/departments', 'POST', {'name': 'TEST Other factory ' + suffix})['id']
customer = admin.request('/catalog/customers', 'POST', {'name': 'TEST 海岸酒店 ' + suffix, 'departmentId': department, 'enabled': True})['id']
other_customer = admin.request('/catalog/customers', 'POST', {'name': 'TEST 另一酒店 ' + suffix, 'departmentId': department, 'enabled': True})['id']
sheet = admin.request('/catalog/items', 'POST', {'name': 'TEST 床单 ' + suffix, 'category': 'ROOM', 'enabled': True})['id']
towel = admin.request('/catalog/items', 'POST', {'name': 'TEST 浴巾 ' + suffix, 'category': 'BATH', 'enabled': True})['id']
for item, price in [(sheet, '2.50'), (towel, '1.20')]:
    admin.request('/catalog/rates', 'POST', {'customerId': customer, 'itemId': item, 'unitPrice': price, 'reference': 'TEST agreed laundry contract', 'enabled': True})
users, clients, user_ids = {}, {}, {}
for name, role, dep, bound in [('ops', '收货与生产', department, None), ('quality', '质检员', department, None), ('driver', '配送员', department, None), ('finance', '财务', department, None), ('finance2', '财务', department, None), ('hotel', '酒店客户', department, customer), ('otherhotel', '酒店客户', department, other_customer), ('outside', '收货与生产', outside_dep, None)]:
    username = 'test-' + name + '-' + suffix
    users[name] = username
    data = {'username': username, 'displayName': 'TEST ' + name, 'password': password, 'roleId': roles[role], 'departmentId': dep, 'enabled': True, 'customerId': bound}
    user_ids[name] = admin.request('/admin/users', 'POST', data)['id']
    clients[name] = Client(username, password)

hotel, ops, quality, driver, finance, finance2 = [clients[n] for n in ['hotel', 'ops', 'quality', 'driver', 'finance', 'finance2']]
batch = draft(hotel, customer, [{'itemId': sheet, 'quantity': 100}, {'itemId': towel, 'quantity': 50}])
body = {'requestKey': key(), 'version': 1, 'note': 'TEST exact retry'}
first = command(hotel, batch, 'submit', payload=body)
retry = command(hotel, batch, 'submit', payload=body)
check(first == retry, 'Exact submit retry changed state')
command(hotel, batch, 'submit', status=409, code='REQUEST_KEY_REUSED', payload={**body, 'note': 'changed'})
for who in [clients['otherhotel'], clients['outside']]:
    who.request(f'/batches/{batch}', status=403, code='OUT_OF_SCOPE')
    who.request(f'/batches/{batch}/report.json', status=403, code='OUT_OF_SCOPE')
check(clients['otherhotel'].request('/batches')['total'] == 0, 'Other hotel sees a batch')
check(len(hotel.request('/options')['customers']) == 1, 'Hotel directory leaks another hotel')
hotel.request('/admin/users', status=403, code='FORBIDDEN')
ops.request('/batches', 'POST', {'requestKey': key(), 'customerId': customer, 'serviceDate': '2026-10-05', 'lines': [{'itemId': sheet, 'quantity': 1.5}]}, 400, 'INVALID_INPUT')
ops.request(f'/batches/{batch}/commands/count', 'POST', {'requestKey': key(), 'version': 2, 'note': 'TEST'}, 403, 'FORBIDDEN', csrf=False)
lines = admin.request(f'/batches/{batch}')['lines']
lid1, lid2 = lines[0]['id'], lines[1]['id']
command(ops, batch, 'count', {'lines': [{'lineId': lid1, 'quantity': 98}, {'lineId': lid2, 'quantity': 49}]})
command(admin, batch, 'accept-count', status=403, code='HOTEL_ONLY')
command(hotel, batch, 'accept-count')
command(ops, batch, 'wash')
command(quality, batch, 'inspect', {'lines': [{'lineId': lid1, 'good': 90, 'rewash': 6, 'discard': 2}, {'lineId': lid2, 'good': 49, 'rewash': 0, 'discard': 0}]})
check(admin.request(f'/batches/{batch}')['record']['status'] == 'REWASH', 'Rewash state missing')
command(ops, batch, 'wash')
before = admin.request(f'/batches/{batch}')
command(quality, batch, 'inspect', {'lines': [{'lineId': lid1, 'good': 91, 'rewash': 6, 'discard': 2}, {'lineId': lid2, 'good': 49, 'rewash': 0, 'discard': 0}]}, 400, 'QUANTITY_BALANCE')
check(before == admin.request(f'/batches/{batch}'), 'Failed inspection did not roll back')
command(quality, batch, 'inspect', {'lines': [{'lineId': lid1, 'good': 96, 'rewash': 0, 'discard': 2}, {'lineId': lid2, 'good': 49, 'rewash': 0, 'discard': 0}]})
ref1, ref2, ref3 = ['TEST delivery ' + key() for _ in range(3)]
command(driver, batch, 'dispatch', {'reference': ref1, 'lines': [{'lineId': lid1, 'quantity': 60}, {'lineId': lid2, 'quantity': 49}]})
command(hotel, batch, 'sign', {'reference': ref1, 'lines': [{'lineId': lid1, 'quantity': 55}, {'lineId': lid2, 'quantity': 49}]})
available = admin.request(f'/batches/{batch}')['available']
check(available[str(lid1)] == 36, 'Refused items released without physical return')
command(driver, batch, 'dispatch', {'reference': 'TEST forbidden ' + key(), 'lines': [{'lineId': lid1, 'quantity': 41}]}, 409, 'EXCESS_DELIVERY')
command(ops, batch, 'return', {'reference': ref1, 'lines': [{'lineId': lid1, 'quantity': 5}, {'lineId': lid2, 'quantity': 0}]})
check(admin.request(f'/batches/{batch}')['available'][str(lid1)] == 41, 'Returned quantity unavailable')
command(driver, batch, 'dispatch', {'reference': ref2, 'lines': [{'lineId': lid1, 'quantity': 36}]})
command(hotel, batch, 'sign', {'reference': ref2, 'lines': [{'lineId': lid1, 'quantity': 36}]})
command(driver, batch, 'dispatch', {'reference': ref3, 'lines': [{'lineId': lid1, 'quantity': 5}]})
command(hotel, batch, 'sign', {'reference': ref3, 'lines': [{'lineId': lid1, 'quantity': 5}]})
command(hotel, batch, 'complete')
check(admin.request(f'/batches/{batch}')['record']['status'] == 'COMPLETED', 'Completion missing')
period = datetime.now(ZoneInfo('Asia/Shanghai')).strftime('%Y-%m')
invoice = finance.request('/invoices', 'POST', {'requestKey': key(), 'customerId': customer, 'period': period, 'note': 'TEST monthly signed linen reconciliation'})['record']['id']
check(admin.request(f'/invoices/{invoice}')['record']['amount'] == 298.8, 'Invoice must use 96 x 2.50 + 49 x 1.20')
finance.request('/invoices', 'POST', {'requestKey': key(), 'customerId': customer, 'period': period, 'note': 'TEST duplicate'}, 409, 'NO_ELIGIBLE_BATCHES')
invoice_command(finance, invoice, 'issue')
clients['otherhotel'].request(f'/invoices/{invoice}', status=403, code='OUT_OF_SCOPE')
invoice_command(hotel, invoice, 'confirm')
receipt = 'TEST payment ' + key()
invoice_command(finance, invoice, 'pay', {'amount': 100, 'reference': receipt})
invoice_command(finance, invoice, 'pay', {'amount': 100, 'reference': receipt}, 409, 'DUPLICATE_REFERENCE')
invoice_command(finance, invoice, 'pay', {'amount': 199, 'reference': 'TEST excess ' + key()}, 409, 'EXCESS_PAYMENT')
paid = invoice_command(finance, invoice, 'pay', {'amount': 198.8, 'reference': 'TEST balance ' + key()})
check(paid['record']['status'] == 'PAID', 'Full payment does not close invoice')
payment_id = paid['payments'][0]['id']
invoice_command(finance, invoice, 'reverse', {'paymentId': payment_id, 'reference': 'TEST bad reversal ' + key()}, 403, 'INDEPENDENT_REVERSAL')
invoice_command(finance2, invoice, 'reverse', {'paymentId': payment_id, 'reference': 'TEST reversal ' + key()})
check(admin.request(f'/invoices/{invoice}')['balance'] == 100, 'Reversal did not reopen remaining amount')
invoice_command(finance, invoice, 'pay', {'amount': 100, 'reference': 'TEST replacement receipt ' + key()})

# A ready batch is reserved for actual browser acceptance; no screenshot is synthesized here.
ui_batch = draft(hotel, customer, [{'itemId': sheet, 'quantity': 40}, {'itemId': towel, 'quantity': 20}])
command(hotel, ui_batch, 'submit')
ui_lines = admin.request(f'/batches/{ui_batch}')['lines']
command(ops, ui_batch, 'count', {'lines': [{'lineId': l['id'], 'quantity': l['declaredQty']} for l in ui_lines]})
command(hotel, ui_batch, 'accept-count')
command(ops, ui_batch, 'wash')
command(quality, ui_batch, 'inspect', {'lines': [{'lineId': l['id'], 'good': l['declaredQty'], 'rewash': 0, 'discard': 0} for l in ui_lines]})

# Global lock plus live version protects concurrent dispatch against over-reservation.
version = admin.request(f'/batches/{ui_batch}')['record']['version']
def compete(n):
    client = Client(users['driver'], password)
    req = {'requestKey': key(), 'version': version, 'note': 'TEST concurrent reservation', 'reference': 'TEST concurrent ' + str(n) + key(), 'lines': [{'lineId': ui_lines[0]['id'], 'quantity': 40}]}
    result = client.request(f'/batches/{ui_batch}/commands/dispatch', 'POST', req, status=(200,409))
    if 'record' in result:
        return 200
    check(result.get('code') in {'STALE_VERSION','EXCESS_DELIVERY'}, 'Unexpected concurrent rejection')
    return 409
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    outcomes = sorted(pool.map(compete, [1, 2]))
check(outcomes == [200, 409], 'Concurrent dispatch must produce one success and one rejection')
check(len(admin.request(f'/batches/{ui_batch}')['deliveries']) == 1, 'Concurrent delivery duplicated')

# Revoke a live role and ensure the old session cannot continue.
user = next(u for u in admin.request('/admin/users') if u['id'] == user_ids['driver'])
admin.request('/admin/users/' + str(user['id']), 'PUT', {**user, 'enabled': False})
driver.request('/auth/me', status=401, code='UNAUTHENTICATED')
admin.request('/admin/users/' + str(user['id']), 'PUT', {**user, 'enabled': True})
check(driver.request('/auth/me')['username'] == users['driver'], 'Re-enabled driver missing')
report = hotel.request(f'/batches/{batch}/report.json')
check(report['record']['customerId'] == customer, 'Wrong report customer')
check('zhuatech' not in json.dumps(report), 'Brand promotion inserted into business export')
state = {'password': password, 'users': users, 'userIds': user_ids, 'customerId': customer, 'itemIds': [sheet, towel], 'batchIds': [batch, ui_batch], 'invoiceIds': [invoice], 'uiBatchId': ui_batch, 'result': 'PASS', 'assertions': checks}
state['responses'] = capture(admin, state)
state['assertions'] = checks
fd = os.open(STATE, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, 'w') as out:
    json.dump(state, out, ensure_ascii=False)
print(json.dumps({'mode': 'real-mysql', 'assertions': checks, 'capturedResponses': len(state['responses']), 'result': 'PASS'}))

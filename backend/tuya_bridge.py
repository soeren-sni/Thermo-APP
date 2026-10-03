"""Read-only Tuya bridge. Secrets stay server-side; no device command route exists."""
import csv
import hashlib
import hmac
import json
import math
import os
import re
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, quote
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parent
MAX_AGE = 15 * 60 * 1000

class TuyaError(Exception):
    def __init__(self, code):
        raw=str(code)
        self.code = raw if re.fullmatch("[A-Za-z0-9_-]{1,64}",raw) else "INVALID_ERROR_CODE"
        super().__init__('Tuya-Verbindung fehlgeschlagen (' + self.code + ')')

def signature(client, secret, token, timestamp, path):
    canonical = 'GET\n' + hashlib.sha256(b'').hexdigest() + '\n\n' + path
    return hmac.new(secret.encode(), (client + token + str(timestamp) + canonical).encode(), hashlib.sha256).hexdigest().upper()

class TuyaAPI:
    def __init__(self, client, secret, uid, opener=urlopen):
        if not client or not secret or not uid:
            raise ValueError('TUYA_ACCESS_ID, TUYA_ACCESS_SECRET und TUYA_ACCOUNT_UID am Backend konfigurieren')
        if not re.fullmatch('[A-Za-z0-9_-]+', uid):
            raise ValueError('Ungültige Account UID')
        self.client, self.secret, self.uid, self.opener = client, secret, uid, opener
        self.token, self.expiry = '', 0
        self.endpoint = 'https://openapi.tuyaeu.com'

    def request(self, path, token):
        t = str(int(time.time() * 1000))
        headers = {'client_id': self.client, 'sign_method': 'HMAC-SHA256', 't': t,
                   'sign': signature(self.client, self.secret, token, t, path)}
        if token: headers['access_token'] = token
        try:
            with self.opener(Request(self.endpoint + path, headers=headers, method='GET'), timeout=15) as response:
                raw = response.read(2_000_001)
                if len(raw) > 2_000_000: raise TuyaError('RESPONSE_TOO_LARGE')
                data = json.loads(raw)
        except HTTPError as e:
            raise TuyaError('HTTP_' + str(e.code)) from None
        except (URLError, TimeoutError, OSError, ValueError):
            raise TuyaError('NETWORK_OR_INVALID_RESPONSE') from None
        if not isinstance(data, dict) or data.get('success') is not True:
            raise TuyaError(data.get('code', 'INVALID_RESPONSE') if isinstance(data, dict) else 'INVALID_RESPONSE')
        return data.get('result')

    def authenticate(self):
        result = self.request('/v1.0/token?grant_type=1', '')
        if not isinstance(result, dict) or not result.get('access_token'): raise TuyaError('INVALID_TOKEN_RESPONSE')
        self.token = result['access_token']
        self.expiry = time.time() + float(result.get('expire_time', 0)) - 60

    def get(self, path):
        if time.time() >= self.expiry: self.authenticate()
        try: return self.request(path, self.token)
        except TuyaError as e:
            if e.code not in ('1010', '1011', 'TOKEN_EXPIRED'): raise
            self.authenticate()
            return self.request(path, self.token)  # one retry only


def schema_entries(spec):
    if not isinstance(spec,dict): raise TuyaError("INVALID_DEVICE_SCHEMA")
    rows = {}
    for collection, write in (('status', False), ('functions', True)):
        entries=spec.get(collection,[])
        if not isinstance(entries,list): raise TuyaError('INVALID_DEVICE_SCHEMA')
        for entry in entries:
            if not isinstance(entry,dict): continue
            code = entry.get('code')
            if not isinstance(code, str): continue
            existing = rows.setdefault(code, {'code': code, 'read': False, 'write': False})
            existing.update({'type': entry.get('type'), 'values': entry.get('values')})
            existing['write' if write else 'read'] = True
    return rows


def normalize(entry, value):
    """Only Integer DPs use explicit schema scale. Decimal/scaled strings are not divided."""
    try:
        limits = entry.get('values') or {}
        if isinstance(limits, str): limits = json.loads(limits)
        kind = entry.get('type')
        if kind in ('Integer', 'Value', 'integer'):
            if type(value) is not int or 'scale' not in limits: return None
            scale = limits['scale']
            if type(scale) is not int or not 0 <= scale <= 6: return None
            if 'min' in limits and value < limits['min']: return None
            if 'max' in limits and value > limits['max']: return None
            return value / (10 ** scale)
        if kind in ('Boolean', 'bool'): return value if type(value) is bool else None
        if kind in ('Enum', 'enum'): return value if isinstance(value, str) else None
        return None
    except (TypeError, ValueError): return None


def timestamp(value):
    if not isinstance(value, (int, float)) or not math.isfinite(value): return None
    ms = int(value * 1000) if value < 10**11 else int(value)
    return ms if 0 < ms <= time.time() * 1000 + 60_000 else None


def room_climate(devices, now):
    groups = {}
    for d in devices:
        if d['role'] != 'climate' or not d['verified'] or not d['online'] or d.get('error'): continue
        points = {p['code']: p for p in d['points']}
        t, rh = points.get('va_temperature'), points.get('va_humidity')
        if not t or not rh or not all(isinstance(p.get('normalized'), (float, int)) and not isinstance(p.get('normalized'),bool) for p in (t,rh)): continue
        at = min(t.get('at') or 0, rh.get('at') or 0)
        if not 0 <= now - at <= MAX_AGE: continue
        temp, humidity = t['normalized'], rh['normalized']
        if not -80 <= temp <= 80 or not 0 < humidity <= 100: continue  # RH 0: dewpoint undefined, keep raw diagnosis
        groups.setdefault(d['room'], []).append({'id': d['id'], 't': temp, 'rh': humidity, 'at': at})
    output = []
    for room, sensors in groups.items():
        # No arbitrary choice with two disagreeing sensors. Keep all individual readings in diagnosis.
        import statistics
        median_t = statistics.median(s['t'] for s in sensors)
        median_h = statistics.median(s['rh'] for s in sensors)
        if len(sensors)==2 and (abs(sensors[0]['t']-sensors[1]['t'])>3 or abs(sensors[0]['rh']-sensors[1]['rh'])>15): continue
        accepted = [s for s in sensors if abs(s['t']-median_t)<=3 and abs(s['rh']-median_h)<=15]
        if accepted:
            output.append({'room': room, 'temperature': sum(s['t'] for s in accepted)/len(accepted),
                           'humidity': sum(s['rh'] for s in accepted)/len(accepted),
                           'at': min(s['at'] for s in accepted), 'sensorIds': [s['id'] for s in accepted],
                           'excludedSensorIds': [s['id'] for s in sensors if s not in accepted]})
    return output

class Bridge:
    def __init__(self, api, inventory=None):
        self.api = api
        self.inventory = inventory if inventory is not None else json.loads((ROOT / 'devices.json').read_text())
        self.cache = {'devices': [], 'climate': [], 'error': None, 'fetchedAt': 0, 'source': 'TUYA LIVE'}
        self.last_attempt = float('-inf')
        self.lock = threading.Lock()
        self.schemas = {}

    def snapshot(self):
        with self.lock:
            if time.monotonic() - self.last_attempt < 300: return self.cache
            self.last_attempt = time.monotonic()
            try:
                deadline=time.monotonic()+90
                listing = self.api.get('/v1.0/users/' + quote(self.api.uid, safe='') + '/devices')
                if not isinstance(listing,list): raise TuyaError('INVALID_DEVICE_LIST')
                byid = {d['id']: d for d in listing if isinstance(d,dict) and isinstance(d.get('id'),str)}
                devices = []
                previous = {d['id']: d for d in self.cache['devices']}
                for wanted in self.inventory:
                    d = byid.get(wanted['id'])
                    if d is None:
                        old = previous.get(wanted['id'], {})
                        devices.append({**wanted, 'verified':False, 'online':False, 'points':old.get('points', []), 'error':'ID_NOT_IN_LINKED_ACCOUNT', 'lastContact':old.get('lastContact')})
                        continue
                    # Whitelist fields. Device API can contain local_key; it NEVER reaches response/log/file.
                    gateway_id=d.get('gateway_id') or d.get('parent_id')
                    gateway=byid.get(gateway_id) if isinstance(gateway_id,str) else None
                    gateway_offline=gateway is not None and gateway.get('online') is False
                    out = {**wanted, 'name': d.get('name') or wanted['name'], 'verified':True,
                           'productId':d.get('product_id'), 'category':d.get('category'),
                           'subDevice':d.get('sub'), 'online':d.get('online') is True and not gateway_offline,
                           'lastContact':previous.get(wanted['id'],{}).get('lastContact'), 'error':'GATEWAY_OFFLINE' if gateway_offline else None, 'points':[]}
                    if wanted['role']=='diagnostic':
                        out['points']=[]
                        devices.append(out)
                        continue
                    try:
                        if time.monotonic()>deadline: raise TuyaError('SYNC_BUDGET_EXCEEDED')
                        ident = quote(wanted['id'],safe='')
                        if ident not in self.schemas:
                            self.schemas[ident] = schema_entries(self.api.get('/v1.0/devices/'+ident+'/specifications'))
                        schema = self.schemas[ident]
                        states = {p['code']:p.get('value') for p in d.get('status',[]) if isinstance(p,dict) and isinstance(p.get('code'),str)}
                        # Per-DP measurement times. Device update_time and HTTP fetch time are NOT measurement times.
                        times = {}
                        shadow_error = None
                        try:
                            shadow = self.api.get('/v2.0/cloud/thing/'+ident+'/shadow/properties')
                            if not isinstance(shadow,dict) or not isinstance(shadow.get('properties'),list): raise TuyaError('INVALID_SHADOW_SCHEMA')
                            for p in shadow['properties']:
                                if not isinstance(p,dict): continue
                                if p.get('code') in schema:
                                    states[p['code']] = p.get('value')
                                    times[p['code']] = timestamp(p.get('time'))
                        except TuyaError as e: shadow_error = e.code
                        codes = sorted(set(schema)|set(states))
                        for code in codes:
                            if any(word in code.lower() for word in ('secret','token','local_key','password')): continue
                            row = schema.get(code, {'code':code, 'type':None, 'values':None, 'read':None, 'write':None})
                            value = states.get(code)
                            if not isinstance(value,(str,int,float,bool,type(None))): value=None
                            if isinstance(value,str): value=value[:300]
                            limits = row.get('values') or {}
                            if isinstance(limits,str):
                                try: limits=json.loads(limits)
                                except ValueError: limits={}
                            if not isinstance(limits,dict): limits={}
                            out['points'].append({'code':code,'value':value,'normalized':normalize(row,value),
                                'type':row.get('type'),'unit':limits.get('unit'),'scale':limits.get('scale'),
                                'read':row.get('read'),'write':row.get('write'),'at':times.get(code),
                                'enumValues':limits.get('range')})
                        contacts = [p['at'] for p in out['points'] if p['at']]
                        if contacts: out['lastContact']=max(contacts)
                        if shadow_error: out['error']='SHADOW_'+shadow_error+' · Messzeit unbekannt; keine Live-Freigabe'
                    except (TuyaError, TypeError, KeyError, ValueError) as e:
                        out['points']=previous.get(wanted['id'],{}).get('points',[])
                        out['error']=e.code if isinstance(e,TuyaError) else 'INVALID_DEVICE_SCHEMA'
                    devices.append(out)
                now = int(time.time()*1000)
                self.cache={'source':'TUYA LIVE','devices':devices,'climate':room_climate(devices,now),
                            'error':None,'fetchedAt':now,'accountDeviceCount':len(byid),
                            'unmappedDevices':[{'id':d['id'],'name':d.get('name'),'category':d.get('category')} for d in listing if d.get('id') not in {w['id'] for w in self.inventory}]}
            except (TuyaError, TypeError, ValueError, KeyError) as e:
                self.cache={**self.cache, 'error':e.code if isinstance(e,TuyaError) else 'INVALID_RESPONSE'}
            return self.cache

    def mapping_csv(self):
        import io
        stream=io.StringIO(); writer=csv.writer(stream)
        writer.writerow(['Gerät','Raum','Device ID','Product ID','Category','Funktion','DP Code','Datentyp','Einheit','Scale','Read','Write','API verifiziert'])
        for d in self.snapshot()['devices']:
            for p in d.get('points',[]) or [{}]:
                writer.writerow([d.get('name'),d.get('room'),d['id'],d.get('productId'),d.get('category'),d['role'],p.get('code'),p.get('type'),p.get('unit'),p.get('scale'),p.get('read'),p.get('write'),d['verified']])
        return stream.getvalue().encode()


def handler_class(bridge, app_token):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args): pass  # Never log request headers or response bodies.
        def do_GET(self):
            if not hmac.compare_digest(self.headers.get('Authorization',''), 'Bearer '+app_token):
                self.send_error(401); return
            if self.path not in ('/v1/snapshot','/v1/mapping.csv'):
                self.send_error(404); return
            payload = bridge.mapping_csv() if self.path.endswith('.csv') else json.dumps(bridge.snapshot(),ensure_ascii=False).encode()
            self.send_response(200)
            self.send_header('Content-Type','text/csv; charset=utf-8' if self.path.endswith('.csv') else 'application/json; charset=utf-8')
            self.send_header('Cache-Control','no-store')
            self.send_header('Content-Length',str(len(payload))); self.end_headers(); self.wfile.write(payload)
    return Handler

if __name__=='__main__':
    token=os.environ.get('THERMO_BRIDGE_TOKEN','')
    if len(token)<32: raise SystemExit('THERMO_BRIDGE_TOKEN: mindestens 32 zufällige Zeichen erforderlich')
    provider=os.environ.get('THERMO_PROVIDER','tuya')
    if provider=='homeassistant':
        from home_assistant_bridge import HomeAssistantBridge
        bridge=HomeAssistantBridge(os.environ.get('HA_URL','http://127.0.0.1:8123'),os.environ.get('HA_READ_TOKEN'),os.environ.get('HA_MAPPING_FILE',str(ROOT/'private/ha_entities.json')))
    elif provider=='tuya':
        api=TuyaAPI(os.environ.get('TUYA_ACCESS_ID'),os.environ.get('TUYA_ACCESS_SECRET'),os.environ.get('TUYA_ACCOUNT_UID'))
        bridge=Bridge(api)
    else:raise SystemExit('Unbekannte Datenquelle')
    server=ThreadingHTTPServer(('127.0.0.1',int(os.environ.get('THERMO_BRIDGE_PORT','8787'))),handler_class(bridge,token))
    print('Thermo Tuya-Lesedienst auf Loopback gestartet. HTTPS-Reverse-Proxy erforderlich.')
    server.serve_forever()

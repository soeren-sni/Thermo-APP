"""Optional local adapter. Only explicitly mapped local entities are accepted; read-only."""
from datetime import datetime
import json
import math
import time
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError
from tuya_bridge import TuyaError, MAX_AGE, room_climate

class HomeAssistantBridge:
    def __init__(self,url,token,mapping_path,opener=urlopen):
        uri=urlparse(url)
        if uri.scheme!='https' and not (uri.scheme=='http' and uri.hostname in ('localhost','127.0.0.1','::1')):
            raise ValueError('Home Assistant: HTTPS oder HTTP ausschließlich auf Loopback verwenden')
        if uri.username or uri.password or uri.query or uri.fragment:raise ValueError('Ungültige Home-Assistant-Adresse')
        if not token:raise ValueError('HA_READ_TOKEN am Backend konfigurieren')
        self.url=url.rstrip('/');self.token=token;self.opener=opener
        self.mapping=json.loads(Path(mapping_path).read_text())
        self.cache={'source':'HOME ASSISTANT LOCAL','devices':[],'climate':[],'fetchedAt':0,'error':None}
        self.last_attempt=float('-inf')
        import threading
        self.lock=threading.Lock()

    def snapshot(self):
        with self.lock:
            if time.monotonic()-self.last_attempt<60:return self.cache
            self.last_attempt=time.monotonic()
            try:
                request=Request(self.url+'/api/states',headers={'Authorization':'Bearer '+self.token,'Accept':'application/json'},method='GET')
                with self.opener(request,timeout=15) as response:
                    raw=response.read(2_000_001)
                    if len(raw)>2_000_000:raise TuyaError('RESPONSE_TOO_LARGE')
                    states=json.loads(raw)
                if not isinstance(states,list):raise TuyaError('HA_INVALID_RESPONSE')
                byid={s['entity_id']:s for s in states if isinstance(s,dict) and 'entity_id' in s}
                devices=[]
                for d in self.mapping:
                    points=[];valid=d.get('localConfirmed') is True;errors=[]
                    for code,mapping in d.get('entities',{}).items():
                        entity=mapping.get('entity') if isinstance(mapping,dict) else mapping
                        state=byid.get(entity)
                        if not state or state.get('state') in ('unknown','unavailable',None):
                            valid=False;errors.append('ENTITY_MISSING_OR_OFFLINE: '+entity);continue
                        attrs=state.get('attributes',{})
                        value=attrs.get(mapping['attribute']) if isinstance(mapping,dict) and 'attribute' in mapping else state['state']
                        try:at=int(datetime.fromisoformat(state.get('last_reported') or state.get('last_updated')).timestamp()*1000)
                        except (ValueError,TypeError):at=None
                        if at is not None and not 0<at<=time.time()*1000+60000:at=None
                        normalized=None;unit=attrs.get('unit_of_measurement') or attrs.get('temperature_unit') or (mapping.get('unit') if isinstance(mapping,dict) else None);kind='String'
                        if code in ('va_temperature','va_humidity','temp_set','temp_current','co_value','battery_percentage'):
                            kind='HA scaled numeric'
                            try:
                                number=float(value)
                                if math.isfinite(number):
                                    if code in ('va_temperature','temp_set','temp_current') and unit in ('°C','°F'):
                                        normalized=(number-32)*5/9 if unit=='°F' else number;unit='°C'
                                    elif code in ('va_humidity','battery_percentage') and unit=='%':normalized=number
                                    elif code=='co_value' and unit=='ppm':normalized=number
                            except (TypeError,ValueError):pass
                        elif code=='doorcontact_state':
                            kind='Boolean'
                            if attrs.get('device_class') in ('window','door','opening') and value in ('on','off'):normalized=value=='on'
                        elif code=='switch':
                            kind='Boolean';normalized={'on':True,'off':False}.get(value)
                        elif code=='work_state':
                            kind='Enum';normalized=value  # HA hvac_action, unknown values preserved
                        elif code=='co_state':
                            kind='Enum'
                            if attrs.get('device_class')=='carbon_monoxide':normalized={'on':'alarm','off':'normal'}.get(value)
                        points.append({'code':code,'value':value,'normalized':normalized,'type':kind,'unit':unit,'scale':None,'read':True,'write':False,'at':at})
                    devices.append({'id':d['id'],'name':d['name'],'room':d.get('room'),'role':d['role'],
                                    'productId':None,'category':'Home Assistant entities','verified':valid,'online':valid,
                                    'points':points,'lastContact':max((p['at'] for p in points if p['at']),default=None),
                                    'error':'; '.join(errors) if errors else None if valid else 'LOCAL_INTEGRATION_NOT_CONFIRMED'})
                now=int(time.time()*1000)
                self.cache={'source':'HOME ASSISTANT LOCAL','devices':devices,'climate':room_climate(devices,now),
                            'fetchedAt':now,'error':None,'accountDeviceCount':len(states)}
            except HTTPError as e:self.cache={**self.cache,'error':'HA_HTTP_'+str(e.code)}
            except (URLError,OSError,ValueError,KeyError,TypeError,TuyaError):self.cache={**self.cache,'error':'HA_UNREACHABLE_OR_INVALID_RESPONSE'}
            return self.cache

    def mapping_csv(self):
        import csv,io
        stream=io.StringIO();w=csv.writer(stream)
        w.writerow(['Gerät','Raum','ID','Funktion','Entity ID','Lokale Integration bestätigt'])
        for d in self.mapping:
            for code,entity in d.get('entities',{}).items():w.writerow([d['name'],d.get('room'),d['id'],code,entity,d.get('localConfirmed') is True])
        return stream.getvalue().encode()

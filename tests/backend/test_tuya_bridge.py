import sys, pathlib, unittest, json, time, io, threading
from http.server import ThreadingHTTPServer
from urllib.request import Request, urlopen
from urllib.error import HTTPError
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[2]/'backend'))
from tuya_bridge import *

class FakeAPI:
    uid='account'
    def __init__(self,devices,spec,shadow): self.devices=devices;self.spec=spec;self.shadow=shadow;self.calls=[];self.error=None
    def get(self,path):
        self.calls.append(path)
        if self.error:raise TuyaError(self.error)
        if path.endswith('/devices'):return self.devices
        if path.endswith('/specifications'):return self.spec
        return self.shadow

class BridgeTests(unittest.TestCase):
    def setUp(self):
        self.now=int(time.time()*1000)
        self.inventory=[{'id':'test','name':'Klimatest','room':'living','role':'climate','verified':False}]
        self.spec={'status':[{'code':'va_temperature','type':'Integer','values':'{"scale":1,"unit":"°C","min":-200,"max":600}'},{'code':'va_humidity','type':'Integer','values':'{"scale":0,"unit":"%","min":0,"max":100}'}]}
        self.shadow={'properties':[{'code':'va_temperature','value':237,'time':self.now},{'code':'va_humidity','value':52,'time':self.now}]}
        self.api=FakeAPI([{'id':'test','name':'T&H','online':True,'local_key':'DO_NOT_LEAK','status':[]}],self.spec,self.shadow)
    def test_verified_schema_and_times(self):
        snapshot=Bridge(self.api,self.inventory).snapshot()
        self.assertAlmostEqual(snapshot['climate'][0]['temperature'],23.7)
        self.assertEqual(snapshot['climate'][0]['humidity'],52)
        self.assertEqual(snapshot['climate'][0]['at'],self.now)
        self.assertNotIn('DO_NOT_LEAK',json.dumps(snapshot))
    def test_no_blind_scaling_or_booleans_as_numbers(self):
        row=self.spec['status'][0]
        self.assertIsNone(normalize(row,23.7))
        self.assertIsNone(normalize(row,'23.70'))
        self.assertIsNone(normalize(row,True))
        self.assertIsNone(normalize({'type':'Integer','values':'{}'},237))
        self.assertEqual(normalize({'type':'Integer','values':'{"scale":0}'},21),21)
    def test_no_fresh_timestamp_from_fetch_or_device_update(self):
        self.api.shadow={'properties':[]}
        self.api.devices[0]['status']=[{'code':'va_temperature','value':237},{'code':'va_humidity','value':52}]
        self.api.devices[0]['update_time']=self.now
        result=Bridge(self.api,self.inventory).snapshot()
        self.assertFalse(result['climate']);self.assertIsNone(result['devices'][0]['points'][0]['at'])
    def test_stale_measurement_is_not_live(self):
        for p in self.api.shadow['properties']:p['time']=self.now-MAX_AGE-1000
        self.assertFalse(Bridge(self.api,self.inventory).snapshot()['climate'])
    def test_offline_does_not_enter_aggregate(self):
        self.api.devices[0]['online']=False
        self.assertFalse(Bridge(self.api,self.inventory).snapshot()['climate'])
    def test_global_failure_retains_last_value_and_marks_error(self):
        bridge=Bridge(self.api,self.inventory);first=bridge.snapshot()
        self.api.error='28841105';bridge.last_attempt=0;second=bridge.snapshot()
        self.assertEqual(first['climate'],second['climate']);self.assertEqual(second['error'],'28841105')
        self.assertEqual(first['fetchedAt'],second['fetchedAt'])
    def test_ambiguous_id_is_not_guessed(self):
        self.inventory[0]['id']='tesI'
        d=Bridge(self.api,self.inventory).snapshot()['devices'][0]
        self.assertFalse(d['verified']);self.assertEqual(d['error'],'ID_NOT_IN_LINKED_ACCOUNT')
    def test_schema_read_write_is_actual_metadata(self):
        self.spec['functions']=[self.spec['status'][0]]
        schema=schema_entries(self.spec)
        self.assertTrue(schema['va_temperature']['read']);self.assertTrue(schema['va_temperature']['write'])
        self.assertFalse(schema['va_humidity']['write'])
    def test_unknown_dp_does_not_become_a_climate_value(self):
        self.api.devices[0]['status']=[{'code':'new_unknown','value':42}]
        self.api.shadow={'properties':[]}
        d=Bridge(self.api,self.inventory).snapshot()['devices'][0]
        p=next(p for p in d['points'] if p['code']=='new_unknown')
        self.assertIsNone(p['normalized']);self.assertIsNone(p['read'])
    def test_poll_interval_no_extra_requests(self):
        b=Bridge(self.api,self.inventory);b.snapshot();count=len(self.api.calls);b.snapshot()
        self.assertEqual(count,len(self.api.calls))
    def test_outside_is_separate_room(self):
        self.inventory[0]['room']='outside'
        self.assertEqual(Bridge(self.api,self.inventory).snapshot()['climate'][0]['room'],'outside')
    def test_rate_limit_no_retry_storm(self):
        self.api.error='HTTP_429';b=Bridge(self.api,self.inventory);b.snapshot();b.snapshot()
        self.assertEqual(len(self.api.calls),1)
    def test_multiple_sensors_exclude_outlier(self):
        ds=Bridge(self.api,self.inventory).snapshot()['devices']
        from copy import deepcopy
        normal=deepcopy(ds[0]);normal['id']='other'
        outlier=deepcopy(ds[0]);outlier['id']='bad'
        next(p for p in outlier['points'] if p['code']=='va_temperature')['normalized']=50
        climate=room_climate(ds+[normal,outlier],self.now)[0]
        self.assertEqual(climate['excludedSensorIds'],['bad']);self.assertAlmostEqual(climate['temperature'],23.7)
    def test_two_disagreeing_sensors_no_arbitrary_average(self):
        ds=Bridge(self.api,self.inventory).snapshot()['devices']
        from copy import deepcopy
        other=deepcopy(ds[0]);other['id']='other'
        next(p for p in other['points'] if p['code']=='va_temperature')['normalized']=50
        self.assertFalse(room_climate(ds+[other],self.now))
    def test_backend_http_auth_and_no_command_route(self):
        b=Bridge(self.api,self.inventory)
        server=ThreadingHTTPServer(('127.0.0.1',0),handler_class(b,'test-read-token'))
        thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        url='http://127.0.0.1:'+str(server.server_port)
        try:
            with self.assertRaises(HTTPError) as e:urlopen(url+'/v1/snapshot')
            self.assertEqual(e.exception.code,401)
            with urlopen(Request(url+'/v1/snapshot',headers={'Authorization':'Bearer test-read-token'})) as response:
                self.assertEqual(json.load(response)['source'],'TUYA LIVE')
            with self.assertRaises(HTTPError) as e:urlopen(Request(url+'/commands',headers={'Authorization':'Bearer test-read-token'}))
            self.assertEqual(e.exception.code,404)
            with self.assertRaises(HTTPError) as e:urlopen(Request(url+'/v1/snapshot',method='POST',headers={'Authorization':'Bearer test-read-token'}))
            self.assertEqual(e.exception.code,501)
        finally:server.shutdown();server.server_close()
    def test_inventory_contains_47_unique_ids_and_no_bad_heating_automation(self):
        ds=json.loads((ROOT/'devices.json').read_text())
        self.assertEqual(len(ds),47);self.assertEqual(len({d['id'] for d in ds}),47)
        self.assertEqual(next(d for d in ds if d['name']=='Heizung Bad')['role'],'diagnostic')
        self.assertEqual(next(d for d in ds if d['name']=='Ventilator Keller')['role'],'diagnostic')
        self.assertTrue(next(d for d in ds if d['room']=='pantry')['ambiguous'])

if __name__=='__main__':unittest.main()

class ProtocolTests(unittest.TestCase):
    def test_signature_matches_tuya_canonical_get(self):
        canonical='GET\n'+hashlib.sha256(b'').hexdigest()+'\n\n/v1.0/token?grant_type=1'
        expected=hmac.new(b'test-secret',('test-client'+'1700000000000'+canonical).encode(),hashlib.sha256).hexdigest().upper()
        self.assertEqual(signature('test-client','test-secret','','1700000000000','/v1.0/token?grant_type=1'),expected)
        self.assertNotEqual(signature('test-client','test-secret','access-token','1700000000000','/v1.0/token?grant_type=1'),expected)
    def test_token_expiry_retries_once_and_no_commands(self):
        from unittest.mock import patch
        api=TuyaAPI('test-client','test-secret','test-uid')
        with patch.object(api,'request',side_effect=[{'access_token':'first','expire_time':7200},TuyaError('1010'),{'access_token':'second','expire_time':7200},[]]) as request:
            self.assertEqual(api.get('/v1.0/users/test-uid/devices'),[])
            self.assertEqual(request.call_count,4)
            self.assertEqual(request.call_args_list[0].args,('/v1.0/token?grant_type=1',''))
            self.assertEqual(request.call_args_list[2].args,('/v1.0/token?grant_type=1',''))
    def test_permission_failure_does_not_retry(self):
        from unittest.mock import patch
        api=TuyaAPI('test-client','test-secret','test-uid');api.token='test-token';api.expiry=time.time()+1000
        with patch.object(api,'request',side_effect=TuyaError('PERMISSION_MISSING')) as request:
            with self.assertRaises(TuyaError):api.get('/v1.0/users/test-uid/devices')
            self.assertEqual(request.call_count,1)
    def test_failed_auth_contains_no_credentials(self):
        class Response(io.BytesIO):pass
        def opener(req,timeout):return Response(b'{"success":false,"code":"EXPIRED","msg":"private payload not exposed"}')
        api=TuyaAPI('fixture-client','fixture-secret','test-uid',opener)
        with self.assertRaises(TuyaError) as caught:api.authenticate()
        self.assertNotIn('fixture-secret',str(caught.exception));self.assertNotIn('private payload',str(caught.exception))
    def test_csv_reports_only_api_schema_not_assumed_product_ids(self):
        fixture=BridgeTests();fixture.setUp();bridge=Bridge(fixture.api,fixture.inventory)
        csv_data=bridge.mapping_csv().decode()
        self.assertIn('va_temperature',csv_data);self.assertNotIn('DO_NOT_LEAK',csv_data)

import unittest,sys,pathlib,json,tempfile,io
from datetime import datetime,timezone
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[2]/'backend'))
from home_assistant_bridge import HomeAssistantBridge

class LocalTests(unittest.TestCase):
    def bridge(self,states,mapping=None):
        states=json.dumps(states).encode()
        self.temp=tempfile.NamedTemporaryFile(mode='w',suffix='.json',delete=False)
        data=mapping or [{'id':'local','name':'Local climate','role':'climate','room':'outside','localConfirmed':True,'entities':{'va_temperature':'sensor.temp','va_humidity':'sensor.rh'}}]
        json.dump(data,self.temp);self.temp.close();self.addCleanup(lambda:pathlib.Path(self.temp.name).unlink(missing_ok=True))
        def opener(req,timeout):self.assertEqual(req.method,'GET');self.assertEqual(req.full_url,'http://127.0.0.1:8123/api/states');return io.BytesIO(states)
        return HomeAssistantBridge('http://127.0.0.1:8123','test-token',self.temp.name,opener)
    def states(self):
        at=datetime.now(timezone.utc).isoformat()
        return [{'entity_id':'sensor.temp','state':'23.7','last_reported':at,'attributes':{'unit_of_measurement':'°C'}},{'entity_id':'sensor.rh','state':'52','last_reported':at,'attributes':{'unit_of_measurement':'%'}}]
    def test_already_scaled_value_stays_23_7(self):
        c=self.bridge(self.states()).snapshot()['climate'][0];self.assertEqual(c['temperature'],23.7)
    def test_cloud_or_unknown_integration_not_claimed_local(self):
        mapping=[{'id':'local','name':'Local','room':'outside','role':'climate','localConfirmed':False,'entities':{'va_temperature':'sensor.temp','va_humidity':'sensor.rh'}}]
        self.assertFalse(self.bridge(self.states(),mapping).snapshot()['climate'])
    def test_missing_or_unavailable_entity_no_fake_live(self):
        states=self.states();states[0]['state']='unavailable';self.assertFalse(self.bridge(states).snapshot()['climate'])
    def test_fahrenheit_conversion(self):
        states=self.states();states[0]['state']='68';states[0]['attributes']['unit_of_measurement']='°F'
        self.assertEqual(self.bridge(states).snapshot()['climate'][0]['temperature'],20)
    def test_window_device_class_required(self):
        state={'entity_id':'binary_sensor.window','state':'on','last_reported':datetime.now(timezone.utc).isoformat(),'attributes':{'device_class':'window'}}
        mapping=[{'id':'w','name':'Window','room':'living','role':'window','localConfirmed':True,'entities':{'doorcontact_state':'binary_sensor.window'}}]
        self.assertTrue(self.bridge([state],mapping).snapshot()['devices'][0]['points'][0]['normalized'])
        state['attributes']['device_class']='motion';self.assertIsNone(self.bridge([state],mapping).snapshot()['devices'][0]['points'][0]['normalized'])
    def test_thermostat_attributes_separate_actual_heating(self):
        state={'entity_id':'climate.heater','state':'heat','last_reported':datetime.now(timezone.utc).isoformat(),'attributes':{'temperature':21,'current_temperature':22,'hvac_action':'idle'}}
        mapping=[{'id':'h','name':'Heater','room':'living','role':'heater','localConfirmed':True,'entities':{'temp_set':{'entity':'climate.heater','attribute':'temperature','unit':'°C'},'temp_current':{'entity':'climate.heater','attribute':'current_temperature','unit':'°C'},'work_state':{'entity':'climate.heater','attribute':'hvac_action'}}}]
        ps={p['code']:p['normalized'] for p in self.bridge([state],mapping).snapshot()['devices'][0]['points']}
        self.assertEqual(ps,{'temp_set':21.0,'temp_current':22.0,'work_state':'idle'})
    def test_remote_cleartext_rejected(self):
        with self.assertRaises(ValueError):HomeAssistantBridge('http://192.168.1.9:8123','test','unused')
    def test_timestamp_not_replaced_by_fetch_time(self):
        states=self.states()
        for state in states:state['last_reported']='2020-01-01T00:00:00+00:00'
        self.assertFalse(self.bridge(states).snapshot()['climate'])

if __name__=='__main__':unittest.main()

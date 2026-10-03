"""Load private configuration without shell commands or printing secret values."""
import json
import os
import runpy
from pathlib import Path
root=Path(__file__).resolve().parent
config=root/'private/config.json'
if not config.exists():raise SystemExit('backend/private/config.json fehlt. Siehe backend/README.md.')
if os.name!='nt' and config.stat().st_mode & 0o077:
    raise SystemExit('Private Konfiguration nur für den Besitzer lesbar machen: chmod 600 backend/private/config.json')
values=json.loads(config.read_text())
allowed={'THERMO_PROVIDER','TUYA_ACCESS_ID','TUYA_ACCESS_SECRET','TUYA_ACCOUNT_UID','THERMO_BRIDGE_TOKEN','THERMO_BRIDGE_PORT','HA_URL','HA_READ_TOKEN','HA_MAPPING_FILE'}
for key,value in values.items():
    if key not in allowed or not isinstance(value,str):raise SystemExit('Ungültiger privater Konfigurationsschlüssel')
    if value: os.environ[key]=value
runpy.run_path(str(root/'tuya_bridge.py'),run_name='__main__')

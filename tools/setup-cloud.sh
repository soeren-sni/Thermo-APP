#!/usr/bin/env bash
set -euo pipefail
# Existing isolated checkout; never create a worktree during environment setup.
cd /workspace/Thermo-APP
export ANDROID_HOME=/workspace/toolchains/android-sdk
export ANDROID_USER_HOME=/workspace/toolchains/android-user
export ANDROID_EMULATOR_HOME=$ANDROID_USER_HOME
export ANDROID_AVD_HOME=$ANDROID_USER_HOME/avd
export GRADLE_USER_HOME=/workspace/toolchains/gradle-home
mkdir -p /workspace/toolchains/downloads /workspace/toolchains/java-home "$ANDROID_USER_HOME" "$GRADLE_USER_HOME"
command -v java >/dev/null
command -v curl >/dev/null
command -v python >/dev/null
if [ ! -x /workspace/toolchains/gradle-8.9/bin/gradle ]; then
    curl -fsSL https://services.gradle.org/distributions/gradle-8.9-bin.zip -o /workspace/toolchains/downloads/gradle-8.9-bin.zip
    curl -fsSL https://services.gradle.org/distributions/gradle-8.9-bin.zip.sha256 -o /workspace/toolchains/downloads/gradle-8.9-bin.zip.sha256
    python - <<'PY'
import hashlib, pathlib, zipfile
p = pathlib.Path('/workspace/toolchains/downloads/gradle-8.9-bin.zip')
assert hashlib.sha256(p.read_bytes()).hexdigest() == p.with_suffix('.zip.sha256').read_text().strip()
with zipfile.ZipFile(p) as z: z.extractall('/workspace/toolchains')
PY
    chmod +x /workspace/toolchains/gradle-8.9/bin/gradle
fi
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
    curl -fsSL https://dl.google.com/android/repository/repository2-1.xml -o /workspace/toolchains/downloads/android-repository.xml
    curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /workspace/toolchains/downloads/android-commandline.zip
    python - <<'PY'
import hashlib, pathlib, xml.etree.ElementTree as E, zipfile
root = E.parse('/workspace/toolchains/downloads/android-repository.xml').getroot()
for a in root.findall('.//archive'):
    if a.findtext('complete/url') == 'commandlinetools-linux-11076708_latest.zip':
        c = a.find('complete/checksum')
        p = pathlib.Path('/workspace/toolchains/downloads/android-commandline.zip')
        assert hashlib.new(c.get('type', 'sha1'), p.read_bytes()).hexdigest() == c.text
        with zipfile.ZipFile(p) as z: z.extractall('/workspace/toolchains/android-sdk/cmdline-tools')
        break
else: raise RuntimeError('Pinned Android tools no longer present in authoritative metadata; do not skip checksum validation.')
PY
    mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
    chmod +x "$ANDROID_HOME"/cmdline-tools/latest/bin/*
fi
# Java tools do not consume HTTPS_PROXY automatically. Never disable TLS checks.
python - <<'PY'
import os, pathlib, urllib.parse
p = urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY', ''))
h = pathlib.Path('/workspace/toolchains/gradle-home/gradle.properties')
content = 'org.gradle.workers.max=4\n'
if p.hostname:
    content += f'systemProp.https.proxyHost={p.hostname}\nsystemProp.https.proxyPort={p.port or 80}\nsystemProp.http.proxyHost={p.hostname}\nsystemProp.http.proxyPort={p.port or 80}\nsystemProp.http.nonProxyHosts=localhost|127.*\n'
h.write_text(content)
PY
cat > /workspace/toolchains/run-java-tool.py <<'PY'
import os, sys, urllib.parse, subprocess
p = urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY', ''))
opts = f'-Dhttps.proxyHost={p.hostname} -Dhttps.proxyPort={p.port or 80} -Dhttp.proxyHost={p.hostname} -Dhttp.proxyPort={p.port or 80}' if p.hostname else ''
e = dict(os.environ, ANDROID_HOME='/workspace/toolchains/android-sdk', ANDROID_USER_HOME='/workspace/toolchains/android-user', GRADLE_USER_HOME='/workspace/toolchains/gradle-home', JAVA_OPTS=opts + ' -Duser.home=/workspace/toolchains/java-home')
raise SystemExit(subprocess.run(sys.argv[1:], env=e).returncode)
PY
python - <<'PY'
import subprocess
r = subprocess.run(['python', '/workspace/toolchains/run-java-tool.py', '/workspace/toolchains/android-sdk/cmdline-tools/latest/bin/sdkmanager', '--licenses'], input='y\n' * 100, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
print(r.stdout[-1000:])
raise SystemExit(r.returncode)
PY
python /workspace/toolchains/run-java-tool.py "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" 'platform-tools' 'platforms;android-35' 'build-tools;34.0.0' 'build-tools;35.0.0' 'emulator' 'system-images;android-35;google_apis;x86_64' 'system-images;android-28;default;x86_64'
if [ ! -f "$ANDROID_AVD_HOME/Thermo_API_35.ini" ]; then
    python - <<'PY'
import subprocess
r = subprocess.run(['python', '/workspace/toolchains/run-java-tool.py', '/workspace/toolchains/android-sdk/cmdline-tools/latest/bin/avdmanager', 'create', 'avd', '--name', 'Thermo_API_35', '--package', 'system-images;android-35;google_apis;x86_64', '--device', 'pixel_2', '--path', '/workspace/toolchains/avd/Thermo_API_35'], input='no\n', text=True)
raise SystemExit(r.returncode)
PY
fi
# API 28 AOSP is a lighter functional fallback when /dev/kvm is unavailable.
if [ ! -f "$ANDROID_AVD_HOME/Thermo_API_28.ini" ]; then
    python - <<'PYAVD'
import subprocess
r = subprocess.run(['python', '/workspace/toolchains/run-java-tool.py', '/workspace/toolchains/android-sdk/cmdline-tools/latest/bin/avdmanager', 'create', 'avd', '--name', 'Thermo_API_28', '--package', 'system-images;android-28;default;x86_64', '--device', 'pixel_2', '--path', '/workspace/toolchains/avd/Thermo_API_28'], input='no\n', text=True)
raise SystemExit(r.returncode)
PYAVD
fi
python - <<'PYDISPLAY'
from pathlib import Path
for name in ['Thermo_API_28', 'Thermo_API_35']:
    p = Path('/workspace/toolchains/avd') / name / 'config.ini'
    rows = p.read_text().splitlines()
    values = {'hw.lcd.width': '360', 'hw.lcd.height': '840', 'hw.lcd.density': '160'}
    rows = [row for row in rows if row.partition('=')[0].strip() not in values]
    p.write_text('\n'.join(rows) + '\n' + ''.join(f'{key}={value}\n' for key, value in values.items()))
PYDISPLAY
python /workspace/toolchains/run-java-tool.py ./gradlew --no-daemon --max-workers=4 -Dorg.gradle.jvmargs='-Xmx3072m -Dfile.encoding=UTF-8 -Duser.home=/workspace/toolchains/java-home' :app:assembleDebug :app:lintDebug :app:testDebugUnitTest

"""Independent source checks; these do not compile Kotlin or replace Android/JUnit tests."""
from pathlib import Path
import re
import sqlite3
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).parent
MAIN = ROOT / 'onemoney/app/src/main'
KOTLIN = MAIN / 'java/org/pixelrush/moneyiq'

for path in (MAIN / 'res').rglob('*.xml'):
    ET.parse(path)
print('PASS: Android resource XML is well formed')
for name in subprocess.check_output(['git', 'ls-files', '*.apk'], cwd=ROOT, text=True).splitlines():
    assert not (ROOT / name).exists(), name
assert '*.apk' in (ROOT / '.gitignore').read_text()
assert not list((MAIN / 'res').glob('mipmap-*/ic_launcher_foreground.png'))
for name in ['ic_launcher', 'ic_launcher_round']:
    xml = (MAIN / f'res/mipmap-anydpi-v26/{name}.xml').read_text()
    assert '@drawable/ic_launcher_foreground' in xml and '<monochrome' in xml
assert '<string name="app_name">Zerno</string>' in (MAIN / 'res/values/strings.xml').read_text()
print('PASS: Zerno launcher, app name, APK deletion and ignore rule')

# Execute the SQL from all recorded migration objects against a populated v1 fixture.
fixture = (ROOT / 'onemoney/app/src/test/java/org/pixelrush/moneyiq/data/db/MigrationChainTest.kt').read_text()
db = sqlite3.connect(':memory:')
for sql in re.findall(r'db\.execSQL\("""(.*?)"""\)', fixture, re.S):
    db.execute(sql)
for column in ['accountId', 'toAccountId', 'categoryId', 'date']:
    db.execute(f'CREATE INDEX index_transactions_{column} ON transactions ({column})')
for sql in re.findall(r'db\.execSQL\("(INSERT INTO .*?)"\)', fixture):
    db.execute(sql)
source = (KOTLIN / 'data/db/AppDatabase.kt').read_text()
parts = re.findall(r'val MIGRATION_(\d+)_(\d+) = object : Migration\(.*?\{(.*?)(?=\nval MIGRATION_|\nval ALL_MIGRATIONS)', source, re.S)
assert len(parts) == 33, len(parts)
for expected, (start, end, body) in enumerate(sorted(parts, key=lambda part: int(part[0])), 1):
    assert (int(start), int(end)) == (expected, expected + 1)
    statements = re.findall(r'database\.execSQL\(\s*(?:"""(.*?)"""|"((?:[^"\\]|\\.)*)")', body, re.S)
    assert statements, start
    for multiline, single in statements:
        sql = multiline or single
        db.execute(sql)
assert db.execute('SELECT note, amount, toAmount FROM transactions WHERE id = 1').fetchone() == ('history', 25.0, None)
assert db.execute('SELECT balance FROM accounts WHERE id = 1').fetchone() == (75.0,)
assert db.execute('SELECT icon FROM categories WHERE id = 1').fetchone() == ('grocery',)
print('PASS: SQL of all 33 migrations executes; fixture history and balance preserved')

def luminance(value):
    rgb = [int(value[i:i+2], 16) / 255 for i in (0, 2, 4)]
    rgb = [x / 12.92 if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4 for x in rgb]
    return sum(x * weight for x, weight in zip(rgb, [0.2126, 0.7152, 0.0722]))
for surface, colors in [('FFF8F4', ['1B6B3A','B3261E','1F5FA6','8A3FA0']),
                        ('17130D', ['6FD08C','FFB4A9','A8C8FF','E9B3F5'])]:
    for color in colors:
        a, b = sorted([luminance(surface), luminance(color)])
        assert (b + 0.05) / (a + 0.05) >= 4.5, (color, surface)
print('PASS: all 8 specified money tokens meet 4.5:1 contrast on specified surfaces')

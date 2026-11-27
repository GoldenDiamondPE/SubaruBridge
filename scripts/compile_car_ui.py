"""Compile editable, script-free car UI assets; also emit fast desktop previews."""
import argparse
import hashlib
import html
import json
from pathlib import Path
import re
import shutil
import xml.etree.ElementTree as ET

TOKEN = re.compile(r'\{\{([A-Za-z][A-Za-z0-9_]*)\}\}')
PALETTE = {'label', 'background', 'glow', 'surface', 'surfaceRaised', 'text', 'muted', 'accent', 'onAccent', 'border', 'dock'}
ICONS = ('home', 'display', 'touch', 'phone', 'results', 'settings', 'tools', 'arrow', 'music', 'navigation', 'calls', 'messages', 'notifications')


def expand(source, values):
    def value(match):
        if match[1] not in values:
            raise ValueError('Missing UI token: ' + match[1])
        return str(values[match[1]])
    return TOKEN.sub(value, source)


def validate(config, source):
    assert config['defaultTheme'] in config['themes']
    assert config['defaultLayout'] in ('grid', 'list')
    assert type(config['columnsWide']) is int and 2 <= config['columnsWide'] <= 5
    assert type(config['columnsNarrow']) is int and 1 <= config['columnsNarrow'] <= 2
    assert len(config['tiles']) == 4 and {v['id'] for v in config['tiles']} == {'display', 'touch', 'phone', 'results'}
    assert {v['screen'] for v in config['tiles']} == {1, 2, 3, 4}
    assert all(v['screen'] == {'display': 1, 'touch': 2, 'phone': 3, 'results': 4}[v['id']] for v in config['tiles'])
    assert len(config['apps']) == 5 and {v['id'] for v in config['apps']} == {'music', 'navigation', 'calls', 'messages', 'notifications'}
    for key in ('brand', 'eyebrow', 'homeTitle', 'homeSubtitle'):
        assert isinstance(config[key], str) and 0 < len(config[key]) <= 160 and '\n' not in config[key]
    for tile in config['tiles'] + config['apps']:
        assert tile['icon'] in ICONS and tile['tone'] in ('blue', 'green', 'purple', 'orange')
        assert all(isinstance(tile[k], str) and 0 < len(tile[k]) <= 100 and '\n' not in tile[k] for k in ('title', 'subtitle'))
    for key, theme in config['themes'].items():
        assert re.fullmatch('[a-z][a-z0-9-]{0,23}', key)
        assert set(theme) == PALETTE
        assert 0 < len(theme['label']) <= 32 and '\n' not in theme['label']
        assert all(re.fullmatch('#[0-9a-fA-F]{6}', v) for k, v in theme.items() if k != 'label')
    for icon in ICONS:
        text = (source / 'icons' / (icon + '.svg')).read_text(encoding='utf-8')
        assert len(text) <= 4096
        root = ET.fromstring(text)
        for element in root.iter():
            assert element.tag.rsplit('}', 1)[-1] in ('svg', 'g', 'path', 'rect', 'circle', 'ellipse', 'line', 'polyline', 'polygon')
            assert all(not key.lower().startswith('on') and 'href' not in key.lower() for key in element.attrib)
    for file in (source / 'templates').glob('*.html'):
        text = file.read_text(encoding='utf-8')
        assert not re.search(r'<script\b|\bon[a-z]+\s*=|javascript:', text, re.I), file


def prop(value):
    return str(value).replace('\\', '\\\\').replace('\n', '\\n').replace('=', '\\=').replace(':', '\\:')


def compile_ui(source, output, landing, previews=None):
    config = json.loads((source / 'dashboard.json').read_text(encoding='utf-8'))
    validate(config, source)
    output.mkdir(parents=True, exist_ok=True)
    for directory in ('templates', 'icons'):
        shutil.copytree(source / directory, output / directory, dirs_exist_ok=True)
    shutil.copy2(source / 'dashboard.json', output / 'dashboard.json')
    properties = {key: config[key] for key in ('brand', 'eyebrow', 'homeTitle', 'homeSubtitle', 'defaultTheme', 'defaultLayout')}
    properties['theme.ids'] = ','.join(config['themes'])
    for theme, values in config['themes'].items():
        properties['theme.' + theme + '.label'] = values['label']
        properties['theme.' + theme + '.swatch'] = values['glow']
        css = expand((source / 'dashboard.css').read_text(), dict(values, tile_width_wide=format(100 / config['columnsWide'], 'g'), tile_width_narrow=format(100 / config['columnsNarrow'], 'g')))
        (output / ('theme-' + theme + '.css')).write_text(css, encoding='utf-8')
    properties['tile.count'] = len(config['tiles'])
    for index, tile in enumerate(config['tiles']):
        for key, value in tile.items():
            properties['tile.' + str(index) + '.' + key] = value
    properties['app.count'] = len(config['apps'])
    for index, app in enumerate(config['apps']):
        for key, value in app.items():
            properties['app.' + str(index) + '.' + key] = value
    (output / 'ui.properties').write_text('\n'.join(key + '=' + prop(value) for key, value in properties.items()) + '\n', encoding='utf-8')
    def template(name, values): return expand((source / 'templates' / (name + '.html')).read_text(encoding='utf-8'), values)
    def icon(name): return (source / 'icons' / (name + '.svg')).read_text(encoding='utf-8')
    def home(theme, layout):
        basic = '/subarubridge-diag/basic?screen='
        tiles = ''.join(template('tile', dict(tile, title=html.escape(tile['title']), subtitle=html.escape(tile['subtitle']), url='/subarubridge-diag/apps/' + tile['id'], icon=icon(tile['icon']))) for tile in config['apps'])
        urls = {'homeUrl': '/subarubridge-diag/dashboard', 'settingsUrl': '/subarubridge-diag/settings', 'developerUrl': '/subarubridge-diag/developer', 'startUrl': basic + '1'}
        body = template('home', dict(urls, tiles=tiles, arrowIcon=icon('arrow'), clock='9:41 AM', battery='Preview', **{k: html.escape(config[k]) for k in ('eyebrow', 'homeTitle', 'homeSubtitle')}))
        dock = ''.join('<a id="dock-' + key + '" class="dock-link' + (' current' if key == 'home' else '') + '" href="' + url + '">' + icon(symbol) + '<span>' + text + '</span></a>' for key, symbol, text, url in (
            ('home', 'home', 'Home', urls['homeUrl']), ('test', 'results', 'Check', basic + '1'), ('settings', 'settings', 'Appearance', urls['settingsUrl'])))
        return template('shell', dict(urls, pageTitle='Home', brand=html.escape(config['brand']), css=(output / ('theme-' + theme + '.css')).read_text(), theme=theme, layout=layout, dock=dock, devCurrent='', toolsIcon=icon('tools'), body=body))
    landing.parent.mkdir(parents=True, exist_ok=True)
    landing.write_text(home(config['defaultTheme'], config['defaultLayout']), encoding='utf-8')
    if previews:
        previews.mkdir(parents=True, exist_ok=True)
        for theme in config['themes']:
            for layout in ('grid', 'list'):
                (previews / (theme + '-' + layout + '.html')).write_text(home(theme, layout), encoding='utf-8')
    return {str(p.relative_to(output)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(output.rglob('*')) if p.is_file()}


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source', type=Path, default=Path(__file__).resolve().parents[1] / 'android/headunit-ui')
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--landing', type=Path, required=True)
    p.add_argument('--previews', type=Path)
    args = p.parse_args()
    result = compile_ui(args.source, args.output, args.landing, args.previews)
    print(json.dumps({'assets': len(result), 'sha256': result}, indent=2))

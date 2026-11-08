import json, os, re, sys, glob
SRC, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
def load(p):
    t = open(p, encoding='utf-8').read()
    t = re.sub(r'^\s*//.*$', '', t, flags=re.M)
    return json.loads(t)
changed = 0
for path in sorted(glob.glob(f'{SRC}/*.json')):
    d = load(path)
    fs = d.get('minecraft:fog_settings', {})
    dist = fs.get('distance')
    if not isinstance(dist, dict):
        continue
    air = dist.get('air')
    if isinstance(air, dict) and air.get('render_distance_type') == 'render':
        air['fog_start'] = 0.99; air['fog_end'] = 1.0           # no fog, only hide chunk edges
    w = dist.get('weather')
    if isinstance(w, dict) and w.get('render_distance_type') == 'render':
        w['fog_start'] = max(float(w.get('fog_start', 0)), 0.6); w['fog_end'] = 1.0
    water = dist.get('water')
    if isinstance(water, dict) and water.get('render_distance_type') == 'fixed':
        water['fog_end'] = max(float(water.get('fog_end', 60)), 0) * 2   # clearer water
    json.dump(d, open(f'{OUT}/{os.path.basename(path)}', 'w'), indent=2)
    changed += 1
print('fog files written:', changed)

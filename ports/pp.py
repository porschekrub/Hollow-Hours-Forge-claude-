#!/usr/bin/env python3
"""Preprocessor for the multi-loader / multi-version builds.

The mods are written once, in hollow-hours/ and helper-buddy/ (Forge 1.20.1 - that project builds them as they are).
Every other target (NeoForge / Forge 1.21.1, ...) builds a copy made by this script:

  * conditional blocks in .java files:

        //#if MC>=12100 && NEOFORGE
        //$$ code for that target (commented out in the shared source)
        //#elif MC>=12100
        //$$ ...
        //#else
        code for Forge 1.20.1 (the shared source's own, active code)
        //#endif

    the active block is uncommented, the others become blank lines (so line numbers stay the same in errors).
    Flags: MC (12001, 12101), FORGE, NEOFORGE, FABRIC. Operators: && || ! and comparisons.
  * NeoForge: package / class renames (net.minecraftforge.* -> net.neoforged.*), see NEO_RENAMES.
  * 1.21: data folders are singular (recipes -> recipe, ...), recipe results use "id" instead of "item".
  * the target's own src/main is laid over the top (a file there replaces the shared one); a file listed in
    the target's pp-remove.txt is left out.

  python3 pp.py --src ../hollow-hours/src/main --overlay src/main --out build/pp --flags MC=12101,NEOFORGE
  python3 pp.py --check ../hollow-hours/src/main   (the shared source must be exactly the Forge 1.20.1 build)
"""
import argparse
import json
import os
import re
import shutil
import sys

NEO_RENAMES = [
    ('net.minecraftforge.api.distmarker.', 'net.neoforged.api.distmarker.'),
    ('net.minecraftforge.eventbus.api.', 'net.neoforged.bus.api.'),
    ('net.minecraftforge.fml.', 'net.neoforged.fml.'),
    ('net.minecraftforge.common.MinecraftForge', 'net.neoforged.neoforge.common.NeoForge'),
    ('MinecraftForge.EVENT_BUS', 'NeoForge.EVENT_BUS'),
    ('net.minecraftforge.common.ForgeConfigSpec', 'net.neoforged.neoforge.common.ModConfigSpec'),
    ('ForgeConfigSpec', 'ModConfigSpec'),
    ('@Mod.EventBusSubscriber(', '@net.neoforged.fml.common.EventBusSubscriber('),
    ('Mod.EventBusSubscriber.Bus.FORGE', 'net.neoforged.fml.common.EventBusSubscriber.Bus.GAME'),
    ('Mod.EventBusSubscriber.Bus.MOD', 'net.neoforged.fml.common.EventBusSubscriber.Bus.MOD'),
    # tick events: one class per phase, the mods only use the end of a tick
    ('TickEvent.ClientTickEvent e', 'net.neoforged.neoforge.client.event.ClientTickEvent.Post e'),
    ('TickEvent.ServerTickEvent e', 'net.neoforged.neoforge.event.tick.ServerTickEvent.Post e'),
    ('TickEvent.PlayerTickEvent e', 'net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e'),
    ('e.phase != TickEvent.Phase.END', 'false'),
    ('e.player instanceof', 'e.getEntity() instanceof'),
    ('import net.minecraftforge.event.TickEvent;\n', ''),
    # damage: the first damage event (before armour) has the amount and can be cancelled
    ('LivingHurtEvent', 'LivingIncomingDamageEvent'),
    ('LivingAttackEvent', 'LivingIncomingDamageEvent'),
    ('onAddedToWorld()', 'onAddedToLevel()'),
    ('onRemovedFromWorld()', 'onRemovedFromLevel()'),
    ('IEntityAdditionalSpawnData', 'IEntityWithComplexSpawn'),
    ('net.minecraftforge.', 'net.neoforged.neoforge.'),
]

# GeckoLib 4.5+ (Minecraft 1.20.5+) moved its "core" packages
RENAMES_121 = [
    ('software.bernie.geckolib.core.animatable.model.CoreGeoBone', 'software.bernie.geckolib.cache.object.GeoBone'),
    ('CoreGeoBone', 'GeoBone'),
    ('software.bernie.geckolib.core.animatable.', 'software.bernie.geckolib.animatable.'),
    ('software.bernie.geckolib.core.animation.', 'software.bernie.geckolib.animation.'),
    ('software.bernie.geckolib.core.object.Color', 'software.bernie.geckolib.util.Color'),
    ('software.bernie.geckolib.core.object.PlayState', 'software.bernie.geckolib.animation.PlayState'),
    ('software.bernie.geckolib.core.molang.MolangParser.INSTANCE.setValue(', 'software.bernie.geckolib.loading.math.MathParser.setVariable('),
    ('software.bernie.geckolib.util.RenderUtils', 'software.bernie.geckolib.util.RenderUtil'),
    ('RenderUtils.', 'RenderUtil.'),
]

DATA_DIRS_121 = {
    'recipes': 'recipe', 'loot_tables': 'loot_table', 'advancements': 'advancement', 'structures': 'structure',
    'functions': 'function', 'predicates': 'predicate', 'item_modifiers': 'item_modifier',
}
TAG_DIRS_121 = {'items': 'item', 'blocks': 'block', 'entity_types': 'entity_type', 'fluids': 'fluid',
                'game_events': 'game_event', 'functions': 'function'}

DIRECTIVE = re.compile(r'^\s*//#(if|elif|else|endif)\b\s*(.*)$')
COMMENTED = re.compile(r'^(\s*)//\$\$ ?(.*)$')


def evaluate(expr, flags):
    py = expr.replace('&&', ' and ').replace('||', ' or ')
    py = re.sub(r'!(?!=)', ' not ', py)
    names = {k: v for k, v in flags.items()}
    try:
        return bool(eval(py, {'__builtins__': {}}, names))
    except NameError:
        # an unknown flag (FABRIC on a Forge target, ...) is false
        for tok in re.findall(r'[A-Za-z_]\w*', py):
            if tok not in ('and', 'or', 'not') and tok not in names:
                names[tok] = False
        return bool(eval(py, {'__builtins__': {}}, names))


def process_java(text, flags, path):
    out = []
    stack = []  # entries: [parent_active, this_branch_active, any_branch_taken]
    for no, line in enumerate(text.split('\n'), 1):
        m = DIRECTIVE.match(line)
        if m:
            kind, expr = m.group(1), m.group(2).strip()
            active_now = all(e[1] for e in stack)
            if kind == 'if':
                val = evaluate(expr, flags)
                stack.append([active_now, val, val])
            elif kind == 'elif':
                if not stack:
                    raise SystemExit(f'{path}:{no}: //#elif without //#if')
                top = stack[-1]
                val = (not top[2]) and evaluate(expr, flags)
                top[1] = val
                top[2] = top[2] or val
            elif kind == 'else':
                if not stack:
                    raise SystemExit(f'{path}:{no}: //#else without //#if')
                top = stack[-1]
                top[1] = not top[2]
                top[2] = True
            else:
                if not stack:
                    raise SystemExit(f'{path}:{no}: //#endif without //#if')
                stack.pop()
            out.append('')
            continue
        if all(e[1] for e in stack):
            c = COMMENTED.match(line)
            out.append(c.group(1) + c.group(2) if c else line)
        else:
            out.append('')
    if stack:
        raise SystemExit(f'{path}: unclosed //#if')
    return '\n'.join(out)


def check_java(text, path):
    """In the shared source, Forge 1.20.1 blocks must be plain code and every other block commented out."""
    flags = {'MC': 12001, 'FORGE': True}
    stack = []
    bad = []
    for no, line in enumerate(text.split('\n'), 1):
        m = DIRECTIVE.match(line)
        if m:
            kind, expr = m.group(1), m.group(2).strip()
            active_now = all(e[1] for e in stack)
            if kind == 'if':
                val = evaluate(expr, flags)
                stack.append([active_now, val, val])
            elif kind == 'elif':
                top = stack[-1]
                val = (not top[2]) and evaluate(expr, flags)
                top[1], top[2] = val, top[2] or val
            elif kind == 'else':
                top = stack[-1]
                top[1], top[2] = not top[2], True
            else:
                stack.pop()
            continue
        if not stack or not line.strip():
            continue
        active = all(e[1] for e in stack)
        commented = COMMENTED.match(line) is not None
        if active and commented:
            bad.append(f'{path}:{no}: active for Forge 1.20.1 but commented out')
        if not active and not commented:
            bad.append(f'{path}:{no}: not for Forge 1.20.1 but not commented out (//$$ )')
    return bad


IMPORT = re.compile(r'^import\s+([\w.]+)\.(\w+)\s*;\s*$')


def drop_unused_imports(text):
    """Imports whose class the (preprocessed) file never names are dropped: code for another version may have been
    the only user, and the class may not exist on this one."""
    lines = text.split('\n')
    body = '\n'.join(l for l in lines if not l.startswith('import '))
    for i, l in enumerate(lines):
        m = IMPORT.match(l)
        if m and not re.search(r'\b' + re.escape(m.group(2)) + r'\b', body):
            lines[i] = ''
    return '\n'.join(lines)


def rename_neo(text):
    for a, b in NEO_RENAMES:
        text = text.replace(a, b)
    return text


def write_if_changed(dst, data, produced):
    produced.add(os.path.normpath(dst))
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    if isinstance(data, str):
        data = data.encode('utf-8')
    if os.path.exists(dst):
        with open(dst, 'rb') as f:
            if f.read() == data:
                return
    with open(dst, 'wb') as f:
        f.write(data)


def map_resource(rel, flags):
    parts = rel.split('/')
    if flags.get('MC', 0) >= 12100 and len(parts) >= 3 and parts[0] == 'data':
        if parts[2] in DATA_DIRS_121:
            parts[2] = DATA_DIRS_121[parts[2]]
        elif parts[2] == 'tags' and len(parts) >= 4 and parts[3] in TAG_DIRS_121:
            parts[3] = TAG_DIRS_121[parts[3]]
    return '/'.join(parts)


def transform_resource(rel, data, flags):
    if flags.get('MC', 0) >= 12100 and rel.startswith('data/') and rel.endswith('.json') and '/recipe' in rel:
        try:
            j = json.loads(data.decode('utf-8'))
        except ValueError:
            return data
        r = j.get('result')
        if isinstance(r, dict) and 'item' in r:
            r['id'] = r.pop('item')
        elif isinstance(r, str):
            j['result'] = {'id': r}
        return json.dumps(j, indent=2, ensure_ascii=False).encode('utf-8')
    return data


def walk(root):
    for dp, dn, fn in os.walk(root):
        for f in fn:
            full = os.path.join(dp, f)
            yield os.path.relpath(full, root).replace(os.sep, '/'), full


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--src')
    ap.add_argument('--overlay')
    ap.add_argument('--out')
    ap.add_argument('--flags', default='')
    ap.add_argument('--check')
    a = ap.parse_args()

    if a.check:
        bad = []
        for rel, full in walk(os.path.join(a.check, 'java')):
            if rel.endswith('.java'):
                bad += check_java(open(full, encoding='utf-8').read(), rel)
        print('\n'.join(bad) if bad else 'pp check OK')
        sys.exit(1 if bad else 0)

    flags = {}
    for f in filter(None, a.flags.split(',')):
        if '=' in f:
            k, v = f.split('=', 1)
            flags[k.strip()] = int(v)
        else:
            flags[f.strip()] = True
    neo = flags.get('NEOFORGE', False)

    removed = set()
    rm = os.path.join(a.overlay, 'pp-remove.txt') if a.overlay else None
    if rm and os.path.exists(rm):
        removed = {l.strip() for l in open(rm) if l.strip() and not l.startswith('#')}

    produced = set()
    for kind in ('java', 'resources'):
        sources = [os.path.join(a.src, kind)]
        if a.overlay:
            sources.append(os.path.join(a.overlay, kind))
        files = {}
        for base in sources:
            if not os.path.isdir(base):
                continue
            for rel, full in walk(base):
                files[rel] = full  # the overlay comes last and wins
        for rel, full in sorted(files.items()):
            if f'{kind}/{rel}' in removed:
                continue
            data = open(full, 'rb').read()
            if kind == 'java' and rel.endswith('.java'):
                text = process_java(data.decode('utf-8'), flags, rel)
                if flags.get('MC', 0) >= 12100:
                    for x, y in RENAMES_121:
                        text = text.replace(x, y)
                if neo:
                    text = rename_neo(text)
                text = drop_unused_imports(text)
                write_if_changed(os.path.join(a.out, kind, rel), text, produced)
            else:
                out_rel = map_resource(rel, flags) if kind == 'resources' else rel
                write_if_changed(os.path.join(a.out, kind, out_rel), transform_resource(out_rel, data, flags), produced)
    # stale files from an older run
    for kind in ('java', 'resources'):
        base = os.path.join(a.out, kind)
        if os.path.isdir(base):
            for rel, full in list(walk(base)):
                if os.path.normpath(full) not in produced:
                    os.remove(full)
    print(f'pp: {len(produced)} files -> {a.out} ({a.flags})')


if __name__ == '__main__':
    main()

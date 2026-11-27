"""Fail closed on private evidence, proprietary artifacts and unexpected binaries."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

ALLOWED={'.rs','.h','.c','.java','.mk','.py','.md','.html','.css','.json','.toml','.lock','.xml','.gradle','.properties','.yml','.yaml','.svg','.swift','.plist','.pbxproj','.xcscheme','.sh','.xcprivacy'}
DENIED={'.apk','.aab','.dex','.odex','.vdex','.smali','.so','.a','.jar','.zip','.7z','.img','.bin','.pcap','.pcapng','.btsnoop','.log','.keystore','.jks','.key','.pem','.db','.sqlite','.jpg','.jpeg','.pdf'}
PRIVATE_PARTS={'bluetooth-private','captures','firmware','decompiled','smali','jadx','apktool','vendor','jniLibs','standalone-capture'}
SKIP={'.git','.gradle','.cxx','build','target','__pycache__','.local'}
PNG=Path('android/src/main/assets/subarubridge-lab/pattern.png')
PNG_SHA256='4bd97df4c282b97fba8a280df9e3504c4de0c38eb5cd10bacbe1ca0b0a60a6f9'


def files(root):
    result=subprocess.run(['git','-C',str(root),'rev-parse','--show-toplevel'],capture_output=True,text=True)
    if result.returncode==0 and Path(result.stdout.strip()).resolve()==root:
        names=set()
        for args in (['--cached'],['--others','--exclude-standard']):
            raw=subprocess.check_output(['git','-C',str(root),'ls-files','-z',*args])
            names.update(p.decode('utf-8') for p in raw.split(b'\0') if p)
        return sorted(root/name for name in names)
    return sorted(p for p in root.rglob('*') if p.is_file() and not (set(p.relative_to(root).parts)&SKIP))


def audit(root):
    root=Path(root).resolve();inventory={};errors=[]
    for file in files(root):
        relative=file.relative_to(root);name=relative.as_posix();parts=set(relative.parts)
        if file.is_symlink() or not file.resolve().is_relative_to(root):errors.append(name+': links are not allowed');continue
        if parts&PRIVATE_PARTS or any(p.lower().endswith('-private') for p in parts):errors.append(name+': private/proprietary directory');continue
        if file.name=='local.properties' or file.suffix.lower() in DENIED:errors.append(name+': prohibited artifact');continue
        if file.suffix.lower()=='.png':
            if relative!=PNG:errors.append(name+': unreviewed image');continue
        elif file.suffix not in ALLOWED and file.name not in {'LICENSE','.gitignore','.gitattributes','.nojekyll'}:
            errors.append(name+': unreviewed file type');continue
        raw=file.read_bytes()
        if relative==PNG and hashlib.sha256(raw).hexdigest()!=PNG_SHA256:
            errors.append(name+': original pattern image hash changed; review provenance before updating');continue
        if len(raw)>1024*1024:errors.append(name+': unexpected large file');continue
        if relative!=PNG:
            try:text=raw.decode('utf-8')
            except UnicodeDecodeError:errors.append(name+': unexpected binary');continue
            patterns=[r'gh' + r'[pousr]_[A-Za-z0-9]{30,}',r'github' + r'_pat_[A-Za-z0-9_]{30,}',r'-----BEGIN ' + r'(?:RSA |OPENSSH |EC )?PRIVATE KEY-----',
                      r'C:[/\\]Users[/\\]Brandon',r'19231' + r'FDF600JVK',r'\b[A-Fa-f0-9]{2}(?::[A-Fa-f0-9]{2}){5}\b',r'(?m)^package com\.' + r'uievolution',r'(?m)^package com\.' + r'subaru\.global']
            if any(re.search(pattern,text) for pattern in patterns):errors.append(name+': credential, identifier or proprietary source marker');continue
        # The repository declares LF text; verify the Git representation even
        # when a Windows editor has temporarily written CRLF in the worktree.
        inventory[name]=hashlib.sha256(raw if relative==PNG else raw.replace(b'\r\n',b'\n')).hexdigest()
    if errors:raise ValueError('\n'.join(errors))
    return inventory


def verify_inventory(root,actual):
    expected=json.loads((Path(root)/'source-inventory.json').read_text(encoding='utf-8'))
    current={k:v for k,v in actual.items() if k!='source-inventory.json'}
    if current!=expected:raise ValueError('Source inventory differs; regenerate it after reviewing the changed source')


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('root',type=Path);p.add_argument('--inventory',type=Path);p.add_argument('--verify-inventory',action='store_true')
    args=p.parse_args()
    try:result=audit(args.root)
    except ValueError as error:raise SystemExit('Public source check FAILED:\n'+str(error))
    if args.inventory:
        result.pop('source-inventory.json',None)
        args.inventory.write_bytes((json.dumps(result,indent=2)+'\n').encode('utf-8'))
    if args.verify_inventory:
        try:verify_inventory(args.root,result)
        except ValueError as error:raise SystemExit('Public source check FAILED:\n'+str(error))
    print(json.dumps({'source_check':'PASS','files':len(result),'policy':'original source and explicitly reviewed assets only'}))

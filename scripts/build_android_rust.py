"""Build the shared Rust core using Android NDK Clang, on any host."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--sdk',type=Path,required=True)
    p.add_argument('--cargo',default=os.environ.get('SUBARUBRIDGE_CARGO') or shutil.which('cargo'))
    p.add_argument('--tests',action='store_true')
    p.add_argument('--abi',choices=['arm64-v8a','x86_64'],action='append')
    args=p.parse_args()
    if not args.cargo: p.error('Install the pinned Rust toolchain and set SUBARUBRIDGE_CARGO')
    host='windows-x86_64' if sys.platform=='win32' else 'darwin-x86_64' if sys.platform=='darwin' else 'linux-x86_64'
    ndk=args.sdk/'ndk/28.2.13676358/toolchains/llvm/prebuilt'/host
    for abi in args.abi or ['arm64-v8a','x86_64']:
        triple='aarch64-linux-android' if abi=='arm64-v8a' else 'x86_64-linux-android'
        env=os.environ.copy(); prefix='CARGO_TARGET_'+triple.upper().replace('-','_')
        env['RUSTUP_TOOLCHAIN']=env.get('SUBARUBRIDGE_RUST_TOOLCHAIN','1.90.0')
        env[prefix+'_LINKER']=str(ndk/'bin'/('clang.exe' if sys.platform=='win32' else 'clang'))
        env[prefix+'_RUSTFLAGS']='-C link-arg=--target='+triple+'30'
        command=[str(args.cargo),'test' if args.tests else 'build','--locked','--manifest-path',str(ROOT/'rust/Cargo.toml'),'--target',triple,'--release']
        if args.tests: command+=['--no-run','--message-format=json']
        result=subprocess.run(command,env=env,cwd=ROOT/'rust',text=True,capture_output=args.tests)
        if result.returncode:
            if args.tests:
                print(result.stdout,end=''); print(result.stderr,end='',file=sys.stderr)
            raise SystemExit(result.returncode)
        if args.tests:
            print(result.stderr,end='')
            for line in result.stdout.splitlines():
                value=json.loads(line)
                if value.get('executable'): print(value['executable'])
        else:
            destination=ROOT/'android/build/generated/rust'/abi; destination.mkdir(parents=True,exist_ok=True)
            shutil.copy2(ROOT/'rust/target'/triple/'release/libsubarubridge_core.a',destination/'libsubarubridge_core.a')
            print('Built Rust core for '+abi,flush=True)

if __name__=='__main__': main()

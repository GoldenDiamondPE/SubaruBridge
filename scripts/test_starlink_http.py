"""Exercise original local routes on a selected Android device using ADB forwarding."""
import argparse
import http.client
from html.parser import HTMLParser
import json
from pathlib import Path
import re
import subprocess
import time
from urllib.parse import urlencode


class Form(HTMLParser):
    def __init__(self,page):
        super().__init__();self.fields={};self.feed(page)
    def handle_starttag(self,tag,attrs):
        a=dict(attrs)
        if tag=='input' and a.get('type')=='hidden':self.fields[a['name']]=a.get('value','')


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--serial',required=True);p.add_argument('--adb',default='adb')
    p.add_argument('--device-port',type=int,default=8009);p.add_argument('--output',type=Path,required=True)
    args=p.parse_args();args.output.mkdir(parents=True,exist_ok=False)
    port=int(subprocess.check_output([args.adb,'-s',args.serial,'forward','tcp:0','tcp:'+str(args.device_port)],text=True))
    c=http.client.HTTPConnection('127.0.0.1',port,timeout=15);results=[]
    preview_token=None
    def request(name,method,path,status=200,form=None,raw=None):
        c.request(method,path,urlencode(form).encode() if form is not None else raw,
                  dict({'Host':'192.168.1.1','Content-Type':'application/x-www-form-urlencoded'},**({'X-Bridge-Preview':preview_token} if preview_token else {})))
        r=c.getresponse();data=r.read()
        assert r.status==status,(name,r.status,data[:200])
        assert int(r.getheader('Content-Length'))==len(data),name
        results.append({'case':name,'status':r.status,'bytes':len(data),'passed':True})
        return data.decode('utf-8')
    prefix='/subarubridge-diag/'
    try:
        # Activity launch returns before its loopback server is ready. Wait only
        # for availability; assertion failures below are never retried.
        deadline=time.monotonic()+10
        while True:
            try:
                c.request('GET','/version');ready=c.getresponse();ready.read()
                assert ready.status==200,'Preview health check failed'
                break
            except (OSError,http.client.RemoteDisconnected):
                c.close()
                if time.monotonic()>=deadline:raise
                time.sleep(0.1)
        request('personal-apps-require-session','GET',prefix+'apps/messages',403)
        preview_token=subprocess.check_output([args.adb,'-s',args.serial,'shell','run-as','org.subarubridge.app','cat','no_backup/preview-session'],text=True).strip()
        assert re.fullmatch('[0-9a-f]{48}',preview_token),'Debug preview session unavailable'
        v=json.loads(request('version','GET','/version'));assert v['build']=='0.53-dev' and v['transport']=='original-rust-rfcomm'
        assert request('launcher-lookup','GET','/kvs/launcher_url_vehicle/')=='http://192.168.1.1/smartaccess/launcher/subarubridge-demo.html'
        assert re.fullmatch(r'\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d[+-]\d{4}',request('timestamp-before-post','GET','/timestamp/'))
        # Same HTTP connection: the car may send timestamp and launcher POST in sequence.
        home=request('car-absolute-post','POST','http://192.168.1.1/smartaccess/launcher/subarubridge-demo.html',raw=b'pvr=1&pid=synthetic')
        assert '<script' not in home.lower() and all('id="tile-'+i+'"' in home for i in ('music','navigation','calls','messages','notifications'))
        for app in ('music','navigation','calls','messages','notifications'):
            page=request('app-'+app,'GET',prefix+'apps/'+app);assert '<script' not in page.lower()
        request('unknown-app','GET',prefix+'apps/unknown',404)
        request('actions-post-only','GET',prefix+'apps/action',405)
        request('missing-action-ticket','POST',prefix+'apps/action',409,form={'action':'dial','subject':'','ticket':'missing'})
        request('duplicate-action-fields','POST',prefix+'apps/action',400,raw=b'action=dial&action=reply&ticket=bad')
        request('remote-target','GET','http://example.invalid/',403)
        request('malformed-target','GET','/%ZZ',400)
        request('traversal-target','GET','/%2e%2e/private',400)
        request('unknown-path','GET','/does-not-exist',404)
        request('lookup-is-read-only','POST','/kvs/launcher_url_vehicle/',405,raw=b'overwrite')
        request('dashboard-method','POST',prefix+'dashboard',405,raw=b'')
        request('invalid-run','GET',prefix+'status?run=../private',400)
        request('missing-run','GET',prefix+'status',400)
        request('empty-event','POST',prefix+'event',400,raw=b'')
        request('oversize-event','POST',prefix+'event',413,raw=b'x'*1025)
        request('duplicate-fields','POST',prefix+'event',400,raw=b'run=a&run=b')
        request('bad-percent','POST',prefix+'event',400,raw=b'run=%ZZ')
        run='bridge-'+str(int(time.time()*1000))
        basic=prefix+'basic'
        request('display-page','GET',basic+'?'+urlencode({'run':run,'screen':1}))
        page=request('touch-page','GET',basic+'?'+urlencode({'run':run,'screen':2}))
        for i in range(1,4):
            form=Form(page).fields;assert form['action']=='touch'
            page=request('touch-'+str(i),'POST',basic,form=form)
            assert 'Touches received: '+str(i) in page
            replay=request('idempotent-'+str(i),'POST',basic,form=form)
            assert 'Touches received: '+str(i) in replay
            request('conflicting-'+str(i),'POST',basic,409,form=dict(form,action='data',screen=3))
        page=request('phone-page','GET',basic+'?'+urlencode({'run':run,'screen':3}))
        request('read-phone','POST',basic,form=Form(page).fields)
        page=request('result-page','GET',basic+'?'+urlencode({'run':run,'screen':4}))
        page=request('save-result','POST',basic,form=Form(page).fields)
        assert all(s in page for s in ('Screens checked: 4/4','Confirmed touches: 3','Data reads: 1','Saved: true'))
        s=json.loads(request('native-status','GET',prefix+'status?run='+run));assert s['touches']==3 and s['summarySaved'] and s['dataChecks']==1
        request('settings-empty','POST',prefix+'settings',400,raw=b'')
        request('settings-large','POST',prefix+'settings',413,raw=b'x'*513)
        request('settings-duplicate','POST',prefix+'settings',400,raw=b'theme=coast&theme=coast&layout=grid')
        request('settings-unknown','POST',prefix+'settings',400,form={'theme':'private','layout':'grid'})
        request('settings-extra','POST',prefix+'settings',400,form={'theme':'coast','layout':'grid','extra':'reject'})
        for theme in ('daylight','coast','midnight'):
            for layout in ('list','grid'):
                request(theme+'-'+layout,'POST',prefix+'settings',form={'theme':theme,'layout':layout})
                assert 'theme-'+theme+' layout-'+layout in request('persisted-'+theme+'-'+layout,'GET',prefix+'dashboard')
        developer=request('developer','GET',prefix+'developer');assert 'Original Rust core' in developer
        results.append({'case':'completed-four-screen-walkthrough','passed':True})
    finally:
        c.close();subprocess.run([args.adb,'-s',args.serial,'forward','--remove','tcp:'+str(port)],check=True,capture_output=True)
        (args.output/'results.json').write_text(json.dumps(results,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'passed':len(results),'cases':len(results)}))


if __name__=='__main__':main()

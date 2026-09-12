import os,subprocess,sys,re,xml.etree.ElementTree as E
from device_probe import adb_path,select_device
adb=adb_path()
base=[adb,'-s',select_device(adb,os.environ.get('POLDY_SERIAL')),'shell']
subprocess.run(base+['uiautomator','dump','/data/local/tmp/poldy-ui.xml'],check=True,stdout=subprocess.DEVNULL,timeout=15)
s=subprocess.check_output(base+['cat','/data/local/tmp/poldy-ui.xml'],text=True,encoding='utf-8')
nodes=list(E.fromstring(s).iter('node'))
if len(sys.argv)>1:
    matches=[n for n in nodes if n.get('text')==sys.argv[1]]
    if len(matches)!=1: raise SystemExit('Expected one exact UI text match; found '+str(len(matches)))
    coords=list(map(int,re.findall(r'\d+',matches[0].get('bounds'))))
    subprocess.run(base+['input','tap',str((coords[0]+coords[2])//2),str((coords[1]+coords[3])//2)],check=True)
    print('Tapped:',sys.argv[1])
else:
    for n in nodes:
        if n.get('text'): print(n.get('text'),n.get('bounds'))

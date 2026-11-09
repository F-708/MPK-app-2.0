import json, numpy as np
from PIL import Image
d=json.load(open(r'D:/MPK app/MPK-app-2.1/app/src/main/assets/college_map.json',encoding='utf-8'))
f=d['floors']['1']
img=Image.open(r'D:/MPK app/MPK-app-2.1/app/src/main/res/drawable/map_floor1.jpg').convert('L')
W,H=img.size
a=np.asarray(img,dtype=float)/255.0
dark=(a<0.72)
rooms=f['rooms']

def in_room(nx,ny):
    for r in rooms:
        if r['x']-0.002<=nx<=r['x']+r['w']+0.002 and r['y']-0.002<=ny<=r['y']+r['h']+0.002:
            return r['number']
    return None

def darkfrac_seg(p0,p1,n=200):
    xs=np.linspace(p0[0],p1[0],n); ys=np.linspace(p0[1],p1[1],n)
    px=(xs*W).astype(int).clip(0,W-1); py=(ys*H).astype(int).clip(0,H-1)
    dk=dark[py,px].mean()
    rm=sorted(set(x for x in (in_room(x,y) for x,y in zip(xs,ys)) if x))
    return dk, rm

# Sample horizontal scan across y=0.276 (upper corridor row) to find white gaps
def scan_row(y, x0=0.05, x1=0.95, n=900):
    xs=np.linspace(x0,x1,n)
    px=(xs*W).astype(int); py=int(y*H)
    row=dark[py,px]
    # report white runs
    runs=[]; start=None
    for i,v in enumerate(row):
        if not v and start is None: start=xs[i]
        elif v and start is not None:
            if xs[i]-start>0.012: runs.append((round(start,3),round(xs[i],3),round(xs[i]-start,3)))
            start=None
    if start is not None: runs.append((round(start,3),round(xs[-1],3),round(xs[-1]-start,3)))
    return runs

for y in [0.20,0.24,0.276,0.30,0.32,0.35,0.44,0.49,0.52]:
    print(f'y={y}: white runs (x0,x1,len):', scan_row(y))

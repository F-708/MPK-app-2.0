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
    hit=[]
    for r in rooms:
        if r['x']-0.001<=nx<=r['x']+r['w']+0.001 and r['y']-0.001<=ny<=r['y']+r['h']+0.001:
            hit.append(r['number'])
    return hit
def seg(p0,p1,n=300):
    xs=np.linspace(p0[0],p1[0],n); ys=np.linspace(p0[1],p1[1],n)
    px=(xs*W).astype(int).clip(0,W-1); py=(ys*H).astype(int).clip(0,H-1)
    dk=dark[py,px]
    rms=set()
    for x,y in zip(xs,ys):
        for rr in in_room(x,y): rms.add(rr)
    # longest dark run
    mx=0;cur=0
    for v in dk:
        if v: cur+=1; mx=max(mx,cur)
        else: cur=0
    return dk.mean(),mx/n,sorted(rms)

tests={
 'Bvert x0.146 y0.39->0.49':[(0.146,0.391),(0.146,0.49)],
 'hall y0.49 x0.146->0.529':[(0.146,0.49),(0.529,0.49)],
 'hall y0.475 x0.15->0.52':[(0.15,0.475),(0.52,0.475)],
 'hall y0.50 x0.15->0.52':[(0.15,0.50),(0.52,0.50)],
 'A vert x0.518 y0.312->0.49':[(0.518,0.312),(0.518,0.49)],
 'Bend to hall: x0.146 y0.39 -> x0.20 y0.46':[(0.146,0.391),(0.20,0.46)],
 'stairs138 x0.249 y0.313-> to B':[(0.249,0.313),(0.146,0.30)],
 'stairs147 x0.122 y0.229 -> x0.146 y0.26':[(0.122,0.229),(0.146,0.26)],
}
for k,s in tests.items():
    m,mx,rm=seg(s[0],s[1])
    print(f'{k}: darkmean={m:.2f} maxdarkrun={mx:.3f} rooms={rm}')

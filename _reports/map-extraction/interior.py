import json, numpy as np
from PIL import Image
from scipy import ndimage
from collections import deque
d=json.load(open(r'D:/MPK app/MPK-app-2.1/app/src/main/assets/college_map.json',encoding='utf-8'))
f=d['floors']['1']
img=Image.open(r'D:/MPK app/MPK-app-2.1/app/src/main/res/drawable/map_floor1.jpg').convert('L')
W,H=img.size
a=np.asarray(img,dtype=float)/255.0
dark=(a<0.72)
roommask=np.zeros((H,W),dtype=bool)
for r in f['rooms']:
    x0=int(r['x']*W); x1=int((r['x']+r['w'])*W)
    y0=int(r['y']*H); y1=int((r['y']+r['h'])*H)
    roommask[y0:y1+1,x0:x1+1]=True
open_=(~dark)&(~roommask)

# exterior: flood from borders over open_
ext=np.zeros_like(open_)
ext[0,:]=open_[0,:]; ext[-1,:]=open_[-1,:]; ext[:,0]=open_[:,0]; ext[:,-1]=open_[:,-1]
ext=ndimage.binary_propagation(ext, mask=open_, structure=np.ones((3,3)))
interior=open_&(~ext)
lab,n=ndimage.label(interior,structure=np.ones((3,3)))
print('interior open comps:',n)
sizes=ndimage.sum(interior,lab,range(1,n+1))
top=np.argsort(sizes)[::-1][:6]
for t in top: print(' comp',t+1,'size',int(sizes[t]))

def L(x,y): return lab[int(y*H),int(x*W)]
for k,(x,y) in {'A_central(0.55,0.49)':(0.55,0.49),'A_up(0.482,0.27)':(0.482,0.27),
  'B_end(0.312,0.276)':(0.312,0.276),'B_low(0.146,0.39)':(0.146,0.39),
  'hall(0.30,0.46)':(0.30,0.46),'hall2(0.22,0.46)':(0.22,0.46),'stairs138(0.249,0.313)':(0.249,0.313),
  'B_mid(0.146,0.30)':(0.146,0.30),'stairs147(0.122,0.229)':(0.122,0.229)}.items():
    print(f'  {k}: interior={interior[int(y*H),int(x*W)]} label={L(x,y)}')

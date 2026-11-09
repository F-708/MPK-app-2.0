import json, numpy as np
from PIL import Image
from scipy import ndimage
d=json.load(open(r'D:/MPK app/MPK-app-2.1/app/src/main/assets/college_map.json',encoding='utf-8'))
f=d['floors']['1']
img=Image.open(r'D:/MPK app/MPK-app-2.1/app/src/main/res/drawable/map_floor1.jpg').convert('L')
W,H=img.size
a=np.asarray(img,dtype=float)/255.0
dark=(a<0.72)

# mask rooms (interior = not traversable corridor for pathfinding the axes, but corridors run between)
roommask=np.zeros((H,W),dtype=bool)
for r in f['rooms']:
    x0=int(r['x']*W); x1=int((r['x']+r['w'])*W)
    y0=int(r['y']*H); y1=int((r['y']+r['h'])*H)
    roommask[y0:y1+1,x0:x1+1]=True

# open = not dark (white) and not room interior
open_=(~dark)&(~roommask)
# dilate dark slightly to close thin wall gaps
open_=ndimage.binary_opening(open_,np.ones((3,3)))
lab,n=ndimage.label(open_, structure=np.ones((3,3)))
print('open components:',n, 'open px frac', open_.mean().round(3))

# label at key points
def lab_at(nx,ny):
    return lab[int(ny*H),int(nx*W)]
pts={'A_left(0.442,0.494)':(0.442,0.494),'A_up(0.483,0.264)':(0.483,0.264),
     'A_node_wdlc(0.481,0.491)':(0.481,0.491),
     'B_right(0.313,0.276)':(0.313,0.276),'B_left_low(0.146,0.391)':(0.146,0.391),
     'stairs138(0.249,0.313)':(0.249,0.313),'stairs147(0.122,0.229)':(0.122,0.229),
     'stairs132(0.484,0.517)':(0.484,0.517),'stairs115(0.875,0.546)':(0.875,0.546),
     'stairs118(0.881,0.827)':(0.881,0.827),'main(0.481,0.203)':(0.481,0.203),
     'hall(0.40,0.30)':(0.40,0.30),'mid155(0.40,0.26)':(0.40,0.26)}
for k,(x,y) in pts.items():
    print(f'  {k}: open={open_[int(y*H),int(x*W)]} label={lab_at(x,y)}')

# component sizes of open regions
sizes=ndimage.sum(open_,lab,range(1,n+1))
order=np.argsort(sizes)[::-1][:8]
print('top open comps:',[(int(order[i])+1,int(sizes[order[i]])) for i in range(len(order))])

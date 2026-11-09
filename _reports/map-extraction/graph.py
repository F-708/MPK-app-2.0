import json, math
d=json.load(open(r'D:/MPK app/MPK-app-2.1/app/src/main/assets/college_map.json',encoding='utf-8'))
f=d['floors']['1']
JOIN=0.05; PROJ=0.05

verts=[]; adj=[]
def addV(x,y):
    for i,v in enumerate(verts):
        if abs(v[0]-x)<1e-4 and abs(v[1]-y)<1e-4: return i
    verts.append((x,y)); adj.append([]); return len(verts)-1
def link(a,b):
    if a==b: return
    dd=math.dist(verts[a],verts[b]); adj[a].append(b); adj[b].append(a)
def dist_pt_seg(p,a,b):
    dx=b[0]-a[0]; dy=b[1]-a[1]; L2=dx*dx+dy*dy
    if L2==0: return math.dist(p,a),(a[0],a[1])
    t=((p[0]-a[0])*dx+(p[1]-a[1])*dy)/L2
    tc=max(0,min(1,t)); q=(a[0]+tc*dx,a[1]+tc*dy); return math.dist(p,q),q

nodeIndex={}
for n in f['nodes']: nodeIndex[n['id']]=addV(n['x'],n['y'])
for n in f['nodes']:
    a=nodeIndex[n['id']]
    for o in n['links']:
        if o in nodeIndex: link(a,nodeIndex[o])

segments=[]
for c in f['corridors']:
    P=c['points']
    if len(P)<2: continue
    idx=[addV(p[0],p[1]) for p in P]
    for i in range(1,len(idx)):
        link(idx[i-1],idx[i]); segments.append((verts[idx[i-1]],verts[idx[i]]))
    for end in (idx[0],idx[-1]):
        best=-1; bd=JOIN
        for ni in nodeIndex.values():
            dd=math.dist(verts[end],verts[ni])
            if dd<bd: bd=dd; best=ni
        if best>=0: link(end,best)

snapshot=list(verts); snap_idx=list(range(len(verts)))
for vi,v in enumerate(snapshot):
    for (a,b) in segments:
        dq,q=dist_pt_seg(v,a,b)
        if dq<1e-4: continue
        if dq<=PROJ:
            qv=addV(q[0],q[1])
            link(vi,qv)
            ai=None;bi=None
            for i,vv in enumerate(verts):
                if vv==a and ai is None: ai=i
                if vv==b and bi is None: bi=i
            if ai is not None and verts[ai]!=q: link(qv,ai)
            if bi is not None and verts[bi]!=q: link(qv,bi)

# components
n=len(verts); seen=[-1]*n; comps=[]
for s in range(n):
    if seen[s]!=-1: continue
    c=len(comps); stack=[s]; seen[s]=c; comp=[s]
    while stack:
        u=stack.pop()
        for w in adj[u]:
            if seen[w]==-1: seen[w]=c; stack.append(w); comp.append(w)
    comps.append(comp)
print('total verts',n,'components',len(comps))
for ci,comp in enumerate(comps):
    xs=[verts[i][0] for i in comp]; ys=[verts[i][1] for i in comp]
    print(f'  comp {ci}: size={len(comp)} x[{min(xs):.3f},{max(xs):.3f}] y[{min(ys):.3f},{max(ys):.3f}]')

# which component each usable stair snaps to
def comp_of_stair(e):
    best=-1; bd=1e9
    for i,v in enumerate(verts):
        dd=(v[0]-e['x'])**2+(v[1]-e['y'])**2
        if dd<bd: bd=dd; best=i
    return seen[best], math.sqrt(bd)
print('stairs:')
for e in f['entrances']:
    if e['kind']=='stairs':
        c,dist=comp_of_stair(e)
        print(f"  {e['id']} near{e.get('nearRoom')} usable={e.get('usable')} -> comp {c} dist={dist:.3f}")
print('main entrance e_ovibvue nearest comp:', comp_of_stair({'x':0.4809,'y':0.2031}))

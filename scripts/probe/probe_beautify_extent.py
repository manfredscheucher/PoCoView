# Reviewer probe: Python port of Optimize.kt axisRange/beautifyStep to measure extent growth.
import random
L = 1 << 29
def orient(a,b,c):
    d=(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]); return (d>0)-(d<0)
def same(p,i,q):
    o=p[i]
    for j in range(len(p)):
        if j==i: continue
        for k in range(j+1,len(p)):
            if k==i: continue
            s=orient(o,p[j],p[k])
            if s==0 or orient(q,p[j],p[k])!=s: return False
    return True
def axis(p,i,xa):
    cur=p[i][0] if xa else p[i][1]
    at=lambda v:(v,p[i][1]) if xa else (p[i][0],v)
    ok=lambda v:-L<=v<=L and same(p,i,at(v))
    def reach(d):
        g,s=0,1
        while ok(cur+d*(g+s)): g+=s; s*=2
        b=g+s
        while b-g>1:
            m=g+(b-g)//2
            if ok(cur+d*m): g=m
            else: b=m
        return g
    return cur-reach(-1),cur+reach(1)
def beautify(p):
    p=list(p)
    for i in range(len(p)):
        for xa in (True,False):
            lo,hi=axis(p,i,xa); mid=lo+(hi-lo)//2
            p[i]=(mid,p[i][1]) if xa else (p[i][0],mid)
    return p
ext=lambda p:max(max(x for x,_ in p)-min(x for x,_ in p),max(y for _,y in p)-min(y for _,y in p))
print("triangle", beautify([(0,0),(10,0),(5,5)]))
random.seed(1)
for t in range(3):
    p=[]
    while len(p)<10:
        q=(random.randrange(1000),random.randrange(1000))
        if q in p or any(orient(p[a],p[b],q)==0 for a in range(len(p)) for b in range(a+1,len(p))): continue
        p.append(q)
    print("random10 extent", ext(p), "->", ext(beautify(p)))

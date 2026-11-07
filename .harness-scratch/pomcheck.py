import os, re, sys

root = os.getcwd()
problems = []
for dirpath, dirnames, filenames in os.walk(os.path.join(root, "project")):
    if "pom.xml" not in filenames:
        continue
    p = os.path.join(dirpath, "pom.xml")
    s = open(p).read()
    m = re.search(r"<parent>(.*?)</parent>", s, re.S)
    if not m:
        continue
    blk = m.group(1)
    g = re.search(r"<groupId>(.*?)</groupId>", blk).group(1)
    a = re.search(r"<artifactId>(.*?)</artifactId>", blk).group(1)
    v = re.search(r"<version>(.*?)</version>", blk).group(1)
    rp = re.search(r"<relativePath>(.*?)</relativePath>", blk)
    rel = rp.group(1) if rp else "../pom.xml"
    cand = os.path.normpath(os.path.join(dirpath, rel))
    if os.path.isdir(cand):
        cand = os.path.join(cand, "pom.xml")
    ok = os.path.isfile(cand)
    detail = ""
    if ok:
        ts = open(cand).read()
        if "</parent>" in ts:
            ts = ts.split("</parent>", 1)[1]
        pg = re.search(r"<groupId>(.*?)</groupId>", ts)
        pa = re.search(r"<artifactId>(.*?)</artifactId>", ts)
        pv = re.search(r"<version>(.*?)</version>", ts)
        tg = pg.group(1) if pg else "?"
        ta = pa.group(1) if pa else "?"
        tv = pv.group(1) if pv else "?"
        if (tg, ta, tv) != (g, a, v):
            ok = False
            detail = "coords mismatch: found %s:%s:%s, expected %s:%s:%s" % (tg, ta, tv, g, a, v)
    else:
        detail = "no pom.xml at " + os.path.relpath(cand, root)
    status = "OK " if ok else "BAD"
    print("%s %-48s parent %s:%s:%s rel=%s %s" % (status, os.path.relpath(p, root), g, a, v, rel, detail))
    if not ok:
        problems.append(p)
print()
print("checked, problems:", len(problems))

let p = {};

try {
    let q = req.readJson();
    p = q;
}catch (e) {
    p.e = e;
}
return p;
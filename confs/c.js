let p = {};

let v = {
    headers: {
        'x-user-info': req.getQuery('u'),
        [req.getQuery('k')]: "3",
    }
};
try {
    let q = req.readJson();
    p = q;
}catch (e) {
    p.e = e;
}
return {p, u:$req.uri, v};

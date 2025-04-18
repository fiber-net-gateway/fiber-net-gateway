
req.discardBody();

let p = req.getQuery("size");
if (!p) {
    p = 1024*1024*4;
} else {
    p = +p;
}

resp.setHeader("x-size", "" + p);

return strings.repeat("0", p);



let p = {
    host: "abc"
};

let q = {
    path: "pp"
};
return {
    headers: {
        host: p.host
    },
    path: "/" + q.path
};
let num = 1;
let txt = "this is string";
let bin = req.readBinary();
let boo = true;
let nul = null;
let obj = {n:num};
let mis = obj.cc;// 不存在 missing
let arr = [1,2,num];

let result = {num, txt, bin, nul, obj, boo, mis, arr};
result.rawBody = strings.toString(bin);
result.path = req.getPath();
let types = {};
for (let k,v of result) {
    types[k] = typeof v;
}

return {types, result};
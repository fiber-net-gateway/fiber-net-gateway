let num = 1;
let txt = "this is string";
let bin = req.readBinary();
let boo = true;
let nul = null;
let obj = {n:num};
let mis = obj.cc;// 不存在 missing
let arr = [1,2,num];

return {
    add: num + 3,
    add_txt: num + txt,
    and: num - 1 && "not return this, return 0",
    or: num - 1 || "return this",
    mod: (num + 10) % 3,
    "in": "n" in obj,
};
// aasss
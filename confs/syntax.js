directive bd from http "https://www.baidu.com";

req.discardBody();

let a = 100;
let b;
let c = [a, a + 100, b = {}];

let d = [...c];

for(let k,v of c) {
   arrays.push(d, k, v, ...c);
   for(let k,v of c) {
      arrays.push(d, k, v ,k, v);
   }
}

b.cc = "123";
try {
    b.json = req.readJson();
} catch (e) {
    b.jsonError = e;
}

let bdData = bd.request({headers: {"User-Agent": "Apache-HttpClient/4.5.14 (Java/17.0.6)"}});
bdData.body = strings.toString(bdData.body);
return {d, p: d[3]=="200", baidu: bdData};



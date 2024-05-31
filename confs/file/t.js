let b = req.readJson();
let r = 1+2;
for(let _,v of b){
    r = r + v;
}

return {d:"x"+length(b),r};
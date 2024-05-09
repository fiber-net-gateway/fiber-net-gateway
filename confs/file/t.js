directive du from dubbo "com.test.dubbo.DemoService";
let b = req.readJson();
let r = 1+2;
for(let _,v of b){
    r = r + v;
}

return {d:du.createUser("x"+length(b)),r};
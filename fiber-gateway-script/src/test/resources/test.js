let a = $.a + 10;

sleep(1 + a);

try {
    a = a/0;
} catch (e) {
    print(e);
}
let c = [1,2, sleep(1 + 3), a > 5? a+1: a+2];

try {
    for(let a,b of c) {
        if( a== 1){
            print(b/(b-2));
        }
    }
}catch(e){
  print("错误-》"+e);
}


for(let _,a of c) {
    print(a);
}


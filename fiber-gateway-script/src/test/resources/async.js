
let b;
try {
   let a = [1, sleep(0)];
   b = a[0] + a[1];
} catch(e) {
    print(e);
    panic("异常1");
}

try {
    b = b + sleep(-1);
    panic("异常2");
} catch(e) {
    print(e);
}

try {
   b = [1, sleep(100000), b];
   panic("异常3");
} catch(e) {
    print(e);
}


for(let _,c  of [1,2,3,4]) {
    try {
       b = b + sleep(c);
       print("结束了:"+b);
       if(b > 10) {
            print("结束了：...")
            break;
       }
    } catch(e) {
       print(e);
       panic("异常4");
    }
}

for(let _,a of [1,2,3]) {
    if(a > 5) {
        break;
    } else {
        return;
    }
}

return b;

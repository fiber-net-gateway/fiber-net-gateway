

//let a = 0;
//let b = 3;

let k = 0;
for(let a,b of [1,2,3]) {
    print(100*b);
    if(a>1) {
        if(a>1){
            print(a);
        }
        k =k + b;

        print(sleep(2));
        print(4);
        k =k + a;
        print("+++")
        continue;
    } else{
        k =k + b;
        print("===");
    }
    print(2+a);
    sleep(2);
    print(2+a);
    sleep(3);
    sleep(3);
    try {
        print(1/a);
        continue;
    } catch(e) {
        print(a>0?sleep(3)+"===":"=_="+sleep(3));
        print(e);
        k =k + a;
    }
    sleep(0);
    sleep(0);
    k =k + a;
    sleep(0);
    print("========");
}

return k;

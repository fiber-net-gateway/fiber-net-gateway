let b = sleep(0) + 1;
for(let _,c  of [1,2,3,4]) {
    try {
       b = b + sleep(c);
       print("结束了:"+b);
       if(b > 10) {
            print("结束了：...")
            break;
       }
       if(c == 2) {
           sleep(-c);
       }
    } catch(e) {
       print(e);
    }


    try {
        try {
            print(3 / (c - 3));
        }
        catch (e) {
            print("aaaa: "+e);
            print(3 / (c - 3));
        }
    } catch(e) {
        print("bbb: "+e);
        try {
            print("ccc: "+e);
            print(3 / (c - 3));
        }
        catch (e) {
            print("ddd: "+e);
        }
    }
}
return b;
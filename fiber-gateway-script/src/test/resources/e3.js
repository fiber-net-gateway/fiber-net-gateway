
try {
    try {
        print(1/0);
    } catch(e) {
        print(e+"=====");
        print(1/0);
    }
}catch(e) {
     print(e);
 }

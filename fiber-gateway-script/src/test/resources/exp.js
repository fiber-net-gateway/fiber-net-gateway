

let c;
if (c>0) {
   c = 3;
   print(c);
}

let a = print(c>=3?0:c,2,3, ...[1,2,3]);
print("========");

c = print(...[c, sleep(3+2), a, 2, 1]);

for(let _,c of [1,2,3]){
    print("iterate:" + c);
}


return [3, 1+(2+3)/2, c];
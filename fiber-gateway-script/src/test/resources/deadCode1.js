
let a=$.a+3;
if(a>3) {
    throw 3;
} else {
    return 5;
}
print(3);

//====================
let a=$.a+3;
if(a>3) {
    throw 3;
} else if(a>0) {
   throw 3;
} else {
   return 5;
}
print(3);

//====================

try{
    throw 3;
}catch(e){
    return e;
}
print(3);
//====================

for(let _,_ of [1,2,3]) {
    return 3;
}
print(3);
//====================

for(let _,a of [1,2,3]) {
    if (a>1) {
        break;
        print(3);
    }
    return 3;
}

//====================

for(let _,a of [1,2,3]) {
    if (a>1) {
        break;
    }else {
        continue;
    }
    return 3;
}
//====================
for(let _,a of [1,2,3]) {
    if (a>1) {
        break;
    }

    try {
        break;
    } catch (e){
        return;
    }
    return 3;
}


//====================
for(let _,a of [1,2,3]) {
    if (a>1) {
        break;
    }
    try {
    } catch (e){
        continue;
    }

    for (let _,_ of [1,2,3]) {
        return;
    }
    return 8;
}

//====================
for(let _,a of [1,2,3]) {
    if(a > 5) {
        break;
    }else {
        return;
    }
    print(a);
}
//====================
for(let _,a of [1,2,3]) {
    if(a > 5) {
        throw 1;
    }else {
        return;
    }
}
print(3);
//====================
for(let _,a of [1,2,3]) {
    if(a > 5) {
        break;
    }else {
        continue;
    }
    print(3);
}

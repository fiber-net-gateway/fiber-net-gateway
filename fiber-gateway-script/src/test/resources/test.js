let p = $.a + 10;

// 诸事
/**
 * 诸事
 */
print("开始答应000");

/*
 * 下面代码经过优化，直接变成:
 * print(ppp<200:74);
 */
let qqq = 123;
let ppp = qqq + 100;
if (ppp / 3 > 100) {
    print("ppp>200:" + (ppp / 3) + "=>>>" + sleep(1000 + ppp));
} else {
    print("ppp<200:" + (ppp / 3) + "=>>>" + sleep(1000 + ppp));
}

print("开始答应000=====");

if (p > 20) {
    print("开始答应111");
    sleep(1000);
    print("开始答应111===");
} else {
    print("开始答应2222");
    sleep(3000);
    print("开始答应2222===");
}

let m = {a: 1, b: p, c: 3};

for (let a, b of m) {
    print("KEY:" + a);
    print("VALUE:" + b);
}

print("=================");


directive serviceB from time "yyyy-MM-dd HH:mm:ss.SSS";

print(serviceB.get() + ": 今天是个好日子");

let sum = 0;
m = [1, 2, m, p, 7, 8, 9];// p == 11
for (let a, b of m) {

    if (b == 11) {
        continue;
    }

    if (typeof b == "number") {
        sum = sum + b;
    }

    try {

        if (a == 1) {
            print("在 try 中计算：" + (a / (b - 2)));
        } else if (a == 2) {
            print("continue 执行:" + a + b);
            continue;
        }
        print("在 try 中 打点!!!" + a + b);
    } catch (e) {
        print("在 catch 中 打点!!!:" + e + a + b);
    }

    print("KEY:" + a);
    print("VALUE:" + b);
    if (b == 8) {
        break;
    }
}

print("==========end...=======:" + sum);

if (sum == 18) {
    throw {message: "123123123====" + sum, name: "MY_ERROR"};
}

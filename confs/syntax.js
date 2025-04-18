// 变量定义，函数调用。req 不是变量，readJson 不是方法。req.readJson 是一个函数。
let jsonBody = req.readJson();
// 对象
let result = {jsonBody};
// 表达式，数组
jsonBody =  [...jsonBody, 1 + 2 - 3, 1 ,2];
// 迭代： idx 为 index 或者 key
for (let idx, item of jsonBody) {
    // if else
    if (idx > 0) {
        result.item = item;
        break;
    } else {
        result.idx = idx;
        // continue
        continue;
    }
}

// 指令，定义一个函数包
directive bd = http "https://www.baidu.com";

// try catch
try {
    /* 调用函数包 函数 */
    result.http = bd.request({path: "/"});
    if (length(jsonBody) > 3) {
        // throw
        throw "数组太长";
    }
} catch (e) {
    result.err = e;
    result.errorType = typeof e;
}

return result;
# 使用文档
本文介绍 fiber-gateway 脚本解释器 所支持的语法规则。

fiber-gateway 的语法设计类似于 javascript ，但与它相比更简单。它是纯面向过程的语法。


## 数据类型
他的运算对象为 com.fasterxml.jackson.databind.node.JsonNodeType
```java
package com.fasterxml.jackson.databind.node;

public enum JsonNodeType
{ ARRAY, BINARY, BOOLEAN, MISSING, NULL, NUMBER, OBJECT, POJO, STRING}
```
但并未使用 POJO 类型的数据。 所以实际支持 number text binary boolean null missing object array 8种数据类型
```javascript
let num = 1;
let txt = "this is string";
let bin = req.readBinary();
let boo = true;
let nul = null;
let obj = {n:num};
let mis = obj.cc;// 不存在 missing
let arr = [1,2,num];

let result = {num, txt, bin, nul, obj, boo, mis, arr};
let types = {};
for (let k,v of result) {
    types[k] = typeof v;
}
return {types, result};
```
```curl
HTTP/1.1 200 OK
content-type: application/json; charset=utf-8
date: Mon, 18 Dec 2023 12:55:01 GMT
server: fiber-net(fn)/1.0-SNAPSHOT/549a404dcaee9bc2
transfer-encoding: chunked

{"types":{"num":"number","txt":"string","bin":"binary","nul":"null","obj":"object","boo":"boolean","mis":"missing","arr":"array"},"result":{"num":1,"txt":"this is string","bin":"","nul":null,"obj":{"n":1},"boo":true,"mis":null,"arr":[1,2,1]}}
```

## 运算符
支持 一元、二元、三元运算符

### 二元
- 加 + ，减 - ，乘 * ，除 / ，模 %
- 匹配 ~ ，逻辑与 && ，逻辑或 ||
- 小于 < 、小于等于 <= ，大于 >，大于等于 >=，等于 == ，严格等于 ===，不等于 !=，严格不等于 !==
- 包含 in
```javascript
let num = 1;
let txt = "this is string";
let bin = req.readBinary();
let boo = true;
let nul = null;
let obj = {n:num};
let mis = obj.cc;// 不存在 missing
let arr = [1,2,num];

return {
    add: num + 3,
    add_txt: num + txt,
    and: num - 1 && "not return this, return 0",
    or: num - 1 || "return this",
    mod: (num + 10) % 3,
    "in": "n" in obj,
};
```
```curl
HTTP/1.1 200 OK
content-type: application/json; charset=utf-8
date: Mon, 18 Dec 2023 13:51:20 GMT
server: fiber-net(fn)/1.0-SNAPSHOT/549a404dcaee9bc2
transfer-encoding: chunked

{"add":4,"add_txt":"1this is string","and":0,"or":"return this","mod":2,"in":true}
```

### 一元
- 取类型 typeof
- 逻辑反 ! 
- 取正 +
- 取负 -
- 解构 ...
### 三元
- 选择 bool ? a : b 

## 语句
### if else
### for
### continue、break
### try catch
### return、throw

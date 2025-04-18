# 使用文档
本文介绍 fiber-gateway 脚本解释器 所支持的语法规则。

fiber-gateway 的语法设计类似于 javascript ，但与它相比更简单。它是纯面向过程的语法。

# 语法说明
写法上 模仿了 javascript，不过仅仅支持少部分语法。原因是 javascript 语法过于复杂，不适合在网关、FaaS 等场景下使用，
但是其强大的数据表示能力又是这类场景需要的。

除了 简单的表达式外，语句仅支持 变量定义，if else. for (只是用于迭代，不支持条件) continue break. 
try catch (没有finally)，throw，return，以及函数包指令。
不支持条件循环（可以迭代）,不支持函数定义，更没有闭包了。不支持方法。函数都是静态并且 native 定义的。
```javascript
// 变量定义，函数调用。req 不是变量，readJson 不是方法。req.readJson 是一个函数。
let jsonBody = req.readJson(); 
// 对象
let result = {};
// 表达式，数组
jsonBody =  [1 + 2 - 3, 1 ,2];
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
directive demoService from dubbo "com.test.dubbo.DemoService";

// try catch
try {
    /* 调用函数包 函数 */
    result.dubbo = demoService.createUser("This Name");
    if (length(jsonBody) > 3) {
        // throw
        throw "数组太长";
    }
} catch (e) {
    result.errorType = typeof e;
}

return result;
```

## 数据类型
他的运算对象为 io.fiber.net.common.json.JsonNodeType ，数据类型没有方法 （method）
```java
package io.fiber.net.common.json;

public enum JsonNodeType
{ ARRAY, BINARY, BOOLEAN, MISSING, NULL, NUMBER, OBJECT, 
    // for script
    EXCEPTION, ITERATOR}
```
MISSING, EXCEPTION，ITERATOR 是在脚本中产生的，无法通过 json 反序列化得到
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

# 标准库
标准库函数

#### 通用函数（无前缀）
- length 长度。 
```javascript
/* true */ 
length("abc") === 3 
length({a:1,b:2}) === 2
length([1,2,3]) === 3
length(1) === 0
```
- includes 包含
```javascript
// true
includes("abcabc", "cab")
includes(["aa","bb", "cc"], "aa")
// other false
includes({a:1}, "a") === false
```

#### 数组操作 array.XXX
- array.push 
```javascript
let a = [1,2];
let b = array.push(a, 3, 4);
// [1,2,3,4]
return a === b &&
    a[0] === 1 &&
    a[1] === 2 &&
    a[2] === 3 &&
    a[3] === 4; 
```
- array.pop 
```javascript
let a = [1,2,3];
let b = array.pop(a);
let c = array.pop(a);
// a -> [1]
return length(a) === 1
    && b === 3
    && c === 2;
```
- array.join 
```javascript
let a = [1,2,3];
let b = array.join(a, "-");
return b === "1-2-3";
```

#### 对象操作 Object.XXX
- Object.assign
```javascript
let a = {a:1,b:2};
let b = Object.assign(a, {c:3});
return a === b && a.a === 1 && a.b === 2 && a.c === 3;
```
- Object.keys
```javascript
let a = {a:1,b:2};
let b = Object.keys(a);
return typeof b === 'array' 
    && length(b) === 2 
    && b[0] === "a"
    && b[1] === "b";
```
- Object.values
```javascript
let a = {a:1,b:2};
let b = Object.values(a);
return typeof b === 'array' 
    && length(b) === 2 
    && b[0] === 1
    && b[1] === 2;
```
- Object.delete 删除 object 中的 key， 返回实际删除的个数
```javascript
let a = {a:1,b:2};
let b = Object.delete(a, "a", "c");
return b === 1
    && length(a) === 1 // a -> {b:2}
    && a.b === 2
    && typeof a.a === "missing"; // a.a is deleted
```
#### 字符串操作 strings.XXX
- strings.hasPrefix 前缀
```javascript
return strings.hasPrefix("abcdedf", "abc") === true;
```
- strings.hasSuffix 后缀
```javascript
return strings.hasSuffix("abcdedf", "edf") === true;
```
- strings.toLower
```javascript
return strings.toLower('abc123Abc') === 'abc123abc';
```
- strings.toUpper
```javascript
return strings.toUpper('abc123Abc') === 'ABC123ABC';
```
- strings.trim
```javascript
return strings.trim(' 	abc 	 ') === 'abc' && strings.trim('aaabc a', 'a') === 'bc ';
```
- strings.trimLeft
```javascript
return strings.trimLeft(' bc a ') === 'bc a '
       && strings.trimLeft('aa bc a', 'a') === ' bc a'
```
- strings.trimRight
```javascript
return strings.trimRight(' bc a ') === ' bc a' && strings.trimRight(' bc a aa', 'a') === ' bc a ';
```
- strings.split
```javascript
let arr = strings.split('abcecdf', 'c');
return length(arr) === 3
       && arr[0] === 'ab'
       && arr[1] === 'e' 
       && arr[2] === 'df';
```
- strings.findAll
```javascript
let arr = strings.findAll("abcd-effe-ssf-fd", "\\w+");
return length(arr) === 4
  && arr[0] === "abcd"
  && arr[1] === "effe"
  && arr[2] === "ssf"
  && arr[3] === "fd";
```
- strings.contains
```javascript
return strings.contains("abcd-effe-ssf-fd", "e-ssf") === true;
```
- strings.contains_any
```javascript
return strings.contains_any("abcd-effe-ssf-fd", "ccddeezzz") === true;
```
- strings.index
```javascript
return strings.index("aabbcc", "bcc") === 3;
```
- strings.indexAny
```javascript
return strings.indexAny('acsdfds', 'rss') === 2;
```
- strings.lastIndex
```javascript
return strings.lastIndex('cabcd', 'c') === 3;
```
- strings.lastIndexAny
```javascript
return strings.lastIndexAny('cabcd', 'dcz') === 4;
```
- strings.repeat
```javascript
return strings.repeat('acd', 3) === 'acdacdacd';
```
- strings.match
```javascript
return strings.match('aaabbbbccc', 'a+b+c+') === true;
```
- strings.substring
```javascript
return strings.substring('0123456789', 3) === '3456789' && strings.substring('0123456789', 3, 6) === '345';
```
- strings.toString
```javascript
return strings.toString(null) === "null" 
    && strings.toString({}) === "<ObjectNode>" 
    && strings.toString(3.5) === '3.5';
```
#### 二进制操作 binary.XXX
针对 binary 数据类型操作
- binary.base64Encode base64 编码
- binary.base64Decode base64 解码
- binary.hex 二进制转 hex 字符串

#### hash 函数 hash.XXX
- hash.crc32 crc32 编码
- hash.md5 32位 md5 编码
- hash.sha1 sha1 编码
- hash.sha256 sha256 编码

#### json操作 json.XXX
- json.parse 字符串或者二进制 json 反序列化
- json.stringify 运行时对象转 json 字符串

#### math.XXX
- math.floor
- math.abs

#### rand.XXX
- rand.random 随机
- rand.canary 根据参数转数字

#### time.XXX
- time.now 获取当前时间
- time.format 时间格式化

#### 文档完善中 敬请期待......
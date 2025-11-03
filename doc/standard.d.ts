/**
 * 获取长度
 * @param str 字符串|二进制|数组|对象
 * @return 长度
 * @example
 * ```javascript
 * let len = length('hello world');
 * len; // 11
 * len = length([1,2,3]);
 * len; // 3
 * len = length({A: 'a', B: 'b'});
 * len; // 2
 * len = length(binary.getUtf8Bytes("你好")); // 6
 * ```
 */
export function length(str: string | ArrayBuffer | any[] | Record<string, any>): number;

/**
 * 判断字符串是否包含某字符
 * @param str 字符串
 * @param search 字符串
 */
export function includes(str: string, search: string): boolean;
/**
 * 判断数组是否包含某元素
 * @param array
 * @param search
 */
export function includes(array: any[], search: any): boolean;

export namespace JSON {
    /**
     * Parses a JSON string, constructing the JavaScript value or object described by the string.
     * @param text A valid JSON string.
     */
    function parse(text: string): any;

    /**
     * Converts a JavaScript value to a JSON string
     * @param value json value
     */
    function stringify(value: any): string;

}

export namespace url {
    /**
     * 解析query 为对象
     *
     * @param query A=a&B=b&C=c
     * @return {
     *     A: 'a',
     *     B: 'b',
     *     C: 'c'
     * }
     */
    function parseQuery(query: string): Record<string, string>;

    /**
     * 构建 query 字符串
     * @example
     * ```javascript
     * let query = url.buildQuery({A:'a', B:'b', C:['c1','c2']});
     * query; // A=a&B=b&C=c1&C=c2
     * ```
     *
     * @param data
     */
    function buildQuery(data: Record<string, string | string[]>): string;

    /**
     * 编码 url 字符串
     * @param str  url
     */
    function encodeComponent(str: string): string;

    /**
     * 解码 url
     * @param str  url
     */
    function decodeComponent(str: string): string;
}

export namespace array {
    /**
     * 添加元素
     * @param arr 数组
     * @param items 元素
     */
    function push<T>(arr: T[], ...items: T[]): number;

    /**
     * 删除元素
     * @param arr 删除末尾元素
     */
    function pop<T>(arr: T[]): T;

    /**
     * 拼接字符串
     * @param arr 数组
     * @param separator 分割符号
     */
    function join<T>(arr: T[], separator?: string): string;
}

export namespace time {
    /**
     * 获取当前时间戳
     */
    function now(): number;
    /**
     * 获取当前时间并格式化
     * @param fmt YYYY-MM-DD HH:mm:ss
     */
    function now(fmt: string): string;

    /**
     * 时间格式化
     * @param fmt YYYY-MM-DD HH:mm:ss
     * @param time 时间戳: 不传取当前时间
     * @example
     * ```javascript
     * time.format('YYYY-MM-DD HH:mm:ss') === time.now('YYYY-MM-DD HH:mm:ss');
     * time.format('YYYY-MM-DD HH:mm:ss', time.now()) === time.now('YYYY-MM-DD HH:mm:ss');
     * ```
     */
    function format(fmt: string, time?: number): string;
}

export namespace rand {
    /**
     * 获取随机数, [0, bound)
     * @param bound 最大值. 不传则取 1000
     * @return 随机数 范围 [0, bound)
     */
    function random(bound?: number): number;

    /**
     * 根据概率返回 true、false
     * @param bound 概率. 0 - 100之间. 10 表示 10%
     * @param data 计算概率的数据。同样的数据会返回相同的结果
     */
    function canary(bound: number, ...data: any[]): boolean;
}

export namespace Object {
    /**
     * 合并对象，会改变 target 对象
     * @param target 目标对象
     * @param sources 源对象
     * @return target 对象
     * @example
     * ```javascript
     * let obj = {A: 'a', B: 'b'};
     * let ret = Object.assign(obj, {C: 'c'}, {D: 'd'});
     * ret === obj;
     * obj; // {A: 'a', B: 'b', C: 'c', D: 'd'}
     * ```
     */
    function assign<T = any>(target: Record<string, T>, ...sources: Record<string, T>[]): Record<string, T>;

    /**
     * 获取对象的 key 列表
     * @param obj 对象
     * @return key 列表
     * @example
     * ```javascript
     * let keys = Object.keys({A: 'a', B: 'b'});
     * keys; // ['A', 'B']
     * ```
     */
    function keys(obj: Record<string, any>): string[];

    /**
     * 获取对象的 value 列表
     * @param obj 对象
     * @return value 列表
     * @example
     * ```javascript
     * let values = Object.values({A: 'a', B: 'b'});
     * values; // ['a', 'b']
     * ```
     */
    function values<T = any>(obj: Record<string, T>): T[];

    /**
     * 删除对象的属性
     * @param obj 源对象
     * @param key 属性名
     * @return 删除后的对象
     * @example
     * ```javascript
     * let obj = {A: 'a', B: 'b', C: 'c'};
     * let ret = Object.deleteProperties(obj, 'A', 'C');
     * ret === obj;
     * obj; // {B: 'b'}
     * ```
     */
    function deleteProperties(obj: Record<string, any>, ...key: string[]): Record<string, any>;
}

export namespace math {
    /**
     * 获取整数
     * @param value
     * @return 整数
     */
    function floor(value: number): number;

    /**
     * 取绝对值
     * @param value
     * @return 绝对值
     */
    function abs(value: number): number;
}

export namespace binary {
    /**
     * 获取字符串的 UTF-8 编码
     * @param str
     * @return UTF-8 编码
     */
    function getUtf8Bytes(str: string): ArrayBuffer;

    /**
     * 获取字符串的 Base64 编码
     * @param binary
     * @return Base64 编码
     */
    function base64Encode(binary: ArrayBuffer): string;

    /**
     * 获取字符串的 Base64 解码
     * @param str
     * @return Base64 解码
     */
    function base64Decode(str: string): ArrayBuffer;

    /**
     * 获取字符串的 Hex 编码
     * @param binary
     */
    function hex(binary: ArrayBuffer): string;

    /**
     * 获取字符串的 Hex 解码
     * @param str
     */
    function fromHex(str: string): ArrayBuffer;
}

export namespace strings {

    /**
     * 判断字符串是否以 prefix 开头
     * @param str
     * @param prefix
     */
    function hasPrefix(str: string, prefix: string): boolean;

    /**
     * 字符串是否以 suffix 结尾
     * @param str
     * @param suffix
     */
    function hasSuffix(str: string, suffix: string): boolean;

    /**
     * 字符串转小写
     * @param str
     */
    function toLower(str: string): string;

    /**
     * 字符串转大写
     * @param str
     */
    function toUpper(str: string): string;

    /**
     * 去除字符串的首尾的目标字符串，如果没有指定目标字符串，则去除空格
     * @param str 字符串
     * @param trimStr 去除的字符串
     */
    function trim(str: string, trimStr?: string): string;

    /**
     * 去除字符串左侧的目标字符串，如果没有指定目标字符串，则去除空格
     * @param str 源字符串
     * @param trimStr 目标字符串
     */
    function trimLeft(str: string, trimStr?: string): string;

    /**
     * 去除字符串右侧的目标字符串，如果没有指定目标字符串，则去除空格
     * @param str 源字符串
     * @param trimStr 目标字符串
     */
    function trimRight(str: string, trimStr?: string): string;

    /**
     * 分割字符串
     * @param str 源字符串
     * @param sep 分割符
     */
    function split(str: string, sep: string): string[];

    /**
     * 查找字符串
     * @param str 源字符串
     * @param regexp 正则表达式
     */
    function findAll(str: string, regexp: string): string[];

    /**
     * 判断字符串是否包含 substr
     * @param str 源字符串
     * @param substr 子字符串
     */
    function contains(str: string, substr: string): boolean;

    /**
     * 判断字符串是否包含任何字符
     * @param str 源字符串
     * @param chars 字符列表
     */
    function containsAny(str: string, chars: string): boolean;

    /**
     * 获取字符串中首次出现的位置
     * @param str 源字符串
     * @param substr 子字符串
     */
    function index(str: string, substr: string): number;

    /**
     * 获取字符串中首次出现的位置
     * @param str 源字符串
     * @param chars 字符列表
     */
    function indexAny(str: string, chars: string): number;

    /**
     * 获取字符串中最后一次出现的位置
     * @param str 源字符串
     * @param substr 子字符串
     */
    function lastIndex(str: string, substr: string): number;

    /**
     * 获取字符串中 最后一次出现 chars 任意字符的位置
     * @param str 源字符串
     * @param chars 任意字符列表
     */
    function lastIndexAny(str: string, chars: string): number;

    /**
     * 重复字符串
     * @param str 源字符串
     * @param count 重复的次数
     */
    function repeat(str: string, count: number): string;

    /**
     * 匹配字符串
     * @param str 源字符串
     * @param regexp 正则表达式
     */
    function match(str: string, regexp: string): boolean;

    /**
     * 获取子字符串
     * @param str 源字符串
     * @param start 起始位置
     * @param end 结束位置
     */
    function substring(str: string, start: number, end?: number): string;

    /**
     * 字符串转字符串
     * @param str 源字符串
     */
    function toString(str: any): string;
}

export namespace hash {
    /**
     * 获取字符串的 CRC32 值
     * @param str
     * @return CRC32 值
     */
    function crc32(str: string): number;

    /**
     * 获取字符串 或者 二进制的 MD5 值
     * @param str
     * @return MD5 值
     */
    function md5(str: string | ArrayBuffer): string;

    /**
     * 获取字符串 或者 二进制的 SHA1 值
     * @param str
     */
    function sha1(str: string | ArrayBuffer): string;

    /**
     * 获取字符串 或者 二进制的 SHA256 值
     * @param str
     */
    function sha256(str: string | ArrayBuffer): string;
}
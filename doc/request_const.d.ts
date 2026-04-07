import {HttpMethod} from "../documents/service";

export declare const $req: {
    /**
     * 请求路径. /a/b
     */
    get path(): string;

    /**
     * 请求 query. a=1&b=2
     */
    get query(): string | null;

    /**
     * 请求路径 和 query. /a/b?A=1&b=2
     */
    get uri(): string;

    /**
     * 请求方法. GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS
     */
    get method(): HttpMethod;
};

/**
 * 通过 key 获取 header 值
 * @param key header key
 * @returns header value
 * @example
 * ```javascript
 * let key = $header.content_type;
 * // 只能通过上面的方式获取 header
 * // 不能通过这种方式获取 header let value = $header[“content-type”];// 错误 该编译器无法识别这种语法。
 * ```
 */
export type ReadonlyConstant = {
    /**
     * 通过 key 获取 header 值
     * @param key header key
     * @returns header value
     * @example
     * ```javascript
     * let key = $header.content_type;
     * // 只能通过上面的方式获取 header
     * // 不能通过这种方式获取 header let value = $header[“content-type”];// 错误 该编译器无法识别这种语法。
     * ```
     */
    readonly [key: string]: string | undefined;
};

/**
 * 通过 key 获取 header 值
 * @param key header key
 * @returns header value
 * @example
 * ```javascript
 * let key = $header.content_type;
 * // 只能通过上面的方式获取 header
 * // 不能通过这种方式获取 header let value = $header[“content-type”];// 错误 该编译器无法识别这种语法。
 * ```
 */
export declare const $header: ReadonlyConstant;

/**
 * 通过 key 获取 query 参数
 * @param key query 参数 key
 * @returns query 参数 value
 * @example
 * ```javascript
 * let key = $query.a;
 * // 错误 该编译器无法识别这种语法。let value = $query[“a”];
 * ```
 */
export declare const $query: ReadonlyConstant;

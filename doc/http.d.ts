import {HttpMethod} from "./server";

export interface RequestOptions {
    path?: string;
    query?: string | Record<string, string | string[]>,
    headers?: Record<string, string | string[]>,
    method?: HttpMethod;
    body?: any;
    timeout?: number;
    includeHeaders?: boolean;
}

export interface ProxyOptions extends Omit<RequestOptions, "includeHeaders"> {
    flush?: boolean;
    websocket_timeout?: number;
}

export interface RequestResult {
    status: number;
    headers?: Record<string, string>;
    body: ArrayBuffer;
}

/**
 * HTTP 指令实例
 * @example
 * ```javascript
 * directive h = http "https://www.baidu.com";
 * h.proxyPass();
 * ```
 */
export interface HttpDirective {

    /**
     * 发送 http 请求、获取请求结果
     * @param options 请求选项
     * @example
     * ```javascript
     * directive h = http "https://www.baidu.com";
     * let result = h.request({
     *   path: '/api', // 请求路径，默认是 /
     *   query: {A: 'a', B: 'b'} // A=a&B=b 默认没有
     *   headers: {
     *     'X-Uid': '123',
     *     'Content-Type': 'application/json',
     *   },
     *   body:  {
     *     filters: {name: "zhang*", age: "lt 18"}
     *   }, // body 会根据 content-type 进行序列化
     *   method: "POST" // 请求方法，默认是 GET，
     *   timeout: 3000,
     *   includeHeaders: true // 是否返回响应头，false 情况下只有 status 和 body 返回
     * });
     * result.status // 响应状态码
     * result.headers // 响应头 includeHeaders 不为 true 时，返回的 result 中没有 headers 属性
     * result.body // 响应体. 二进制
     *
     * for(let k,v of result.headers) {
     *   resp.setHeader(k, v);
     * }
     *
     * resp.write(result.status, result.body);
     *
     * ```
     */
    request(options?: RequestOptions): RequestResult;

    /**
     * 代理当前请求。
     * @param options 代理选项。可以通过 覆盖 http 内容
     *
     * @example
     * ```javascript
     * directive h = http "https://www.baidu.com";
     * let status = h.proxyPass({
     *   path: '/api',
     *   query: {A: 'a', B: 'b'} // A=a&B=b
     *   headers: {
     *     'X-Uid': '123',
     *     'Content-Type': 'application/json',
     *     'X-Pid': '' // discard request header from downstream
     *   },
     *   body:  {
     *     filters: {name: "zhang*", age: "lt 18"}
     *   }, // body 会根据 content-type 进行序列化
     *   method: "POST"
     *   responseHeaders: {
     *     X-Response-Time: "" // discard response header from upstream
     *   },
     *   timeout: 3000, // 响应时间 3s
     *   flush: true, // 开启及时刷写. 避免缓存 body 数据，适用于 server event stream
     *   websocket_timeout: 60000, // 如果客户端是 websocket，也能予以代理，并且设置超时。
     * });
     * // 执行完代理请求后，返回状态码。这个时候 其实已经返回了响应的。
     * // log(status); 可以记录日至
     * ```
     */
    proxyPass(options?: ProxyOptions): number;

    /**
     * 隧道代理，返回 状态码
     */
    tunnelProxy(): number;

}


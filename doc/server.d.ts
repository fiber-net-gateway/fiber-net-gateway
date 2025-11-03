export type HttpMethod = "GET" | "POST" | "PUT" | "DELETE" | "PATCH" | "HEAD" | "OPTIONS" | "TRACE" | "CONNECT";

export namespace req {

    /**
     * 获取请求路径
     * @returns 请求路径： 如 "/a/b"
     */
    function getPath(): string;

    /**
     * 获取请求 uri 包含 query
     * @returns /a/b?c=d&e=f
     */
    function getUri(): string;

    /**
     * 获取请求参数
     */
    function getQueryStr(): string | null;

    /**
     * 获取所有 cookie
     */
    function getCookie(): Record<string, string>;

    /**
     * 获取指定 cookie
     * @param key
     */
    function getCookie(key: string): string | undefined;

    /**
     * 获取请求方法
     */
    function getMethod(): HttpMethod;

    /**
     * 获取请求头
     */
    function getHeader(): Record<string, string>;

    /**
     * 获取指定请求头
     * @param key
     */
    function getHeader(key: string): string | undefined;


    /**
     * 获取所有解析后的请求参数
     */
    function getQuery(): Record<string, string>

    /**
     * 获取指定解析后的请求参数
     * @param key
     */
    function getQuery(key: string): string | undefined;

    /**
     * 丢弃请求体
     */
    function discardBody(): void;

    /**
     * 读取请求体,解析为 json
     */
    function readJson(): any;

    /**
     * 读取请求体 二进制
     */
    function readBinary(): ArrayBuffer;
}

export namespace resp {
    /**
     * 设置响应头
     * @param key
     * @param value
     */
    function setHeader(key: string, value: string): void;

    /**
     * 添加响应头
     * @param key
     * @param value
     */
    function addHeader(key: string, value: string): void;

    /**
     * set cookie object
     */
    interface Cookie {
        name: string;
        value: string;
        maxAge?: number;
        domain?: string;
        path?: string;
        httpOnly?: boolean;
        secure?: boolean;
        sameSite?: "Strict" | "Lax" | "None";
    }

    /**
     * 添加 cookie
     * @param cookie
     */
    function addCookie(cookie: Cookie): void;

    /**
     * 响应 json
     * @param status http 状态码
     * @param data 将被转为 json
     */
    function sendJson(status: number, data: any): void;

    /**
     * 响应数据。如果未设置 content-type .则根据 data 类型 返回 content-type
     * - string: text/plain; charset=utf-8;
     * - ArrayBuffer: application/octet-stream;
     *
     * @param status http 状态码
     * @param data 响应数据 字符串或者 二进制. 未提供则发送 content-length: 0 空 body。
     */
    function send(status: number, data?: string | ArrayBuffer): void;
}
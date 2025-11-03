/**
 * 单机 限流，令牌
 * @example
 * ```javascript
 * directive rl = rate_limiter "10/3s";
 * if (rl.acquire(100)) {
 *   return "OK";
 * } else {
 *   throw "请求被限制";
 * }
 * ```
 */
export interface RateLimiter {
    /**
     * 获取一个令牌，返回 是否获取成功
     * @param timeout 获取令牌超时时间，单位毫秒。 此函数阻塞
     * @return true 获取成功，false 获取失败
     */
    acquire(timeout?: number): boolean;

    /**
     * 获取一个令牌，失败将抛出异常
     * @param timeout 获取令牌最大等待时间，单位毫秒。此函数阻塞
     */
    mustAcquire(timeout?: number): true;
}


# fiber-gateway-support 模块

## 模块职责
`fiber-gateway-support` 提供限流与熔断等治理组件，可在脚本或 Java 扩展中直接使用，实现对下游依赖的弹性保护。模块主要包含 RateLimiter 与 CircuitBreaker 两大抽象及其默认实现。 【F:fiber-gateway-support/src/main/java/io/fiber/net/support/RateLimiter.java†L1-L200】【F:fiber-gateway-support/src/main/java/io/fiber/net/support/CircuitBreaker.java†L1-L37】

## 核心组件
- **RateLimiter 接口**：定义动态修改限流阈值、异步/阻塞获取令牌、读取剩余令牌数等操作。 【F:fiber-gateway-support/src/main/java/io/fiber/net/support/RateLimiter.java†L1-L200】
- **AtomicRateLimiter**：基于原子字段与周期窗口实现的限流器，支持动态限流、阻塞等待、立即返回等待时长等能力，适合单进程高并发场景。 【F:fiber-gateway-support/src/main/java/io/fiber/net/support/AtomicRateLimiter.java†L9-L190】
- **CircuitBreaker 接口**：提供创建熔断器的静态工厂，可配置失败率阈值、窗口大小、半开请求数及等待时间。 【F:fiber-gateway-support/src/main/java/io/fiber/net/support/CircuitBreaker.java†L7-L37】
- **CircuitBreakerStateMachine**：通过原子状态与滑动窗口记录成功/失败情况，实现 CLOSED、OPEN、HALF_OPEN 三态切换，并支持半开期间的配额控制。 【F:fiber-gateway-support/src/main/java/io/fiber/net/support/CircuitBreakerStateMachine.java†L8-L200】

## 使用指引
1. **限流**：在脚本库或 Java Handler 中创建 `AtomicRateLimiter`，调用 `acquirePermission()` 获取令牌，返回值为等待时长（纳秒）；若超出最大等待时间可选择降级。 【F:fiber-gateway-support/src/main/java/io/fiber/net/support/AtomicRateLimiter.java†L24-L190】
2. **阻塞获取**：使用 `blockAcquirePermission()` 并传入最大等待时长，若返回 -1 表示超时，适合治理脚本中精确控制超时逻辑。 【F:fiber-gateway-support/src/main/java/io/fiber/net/support/AtomicRateLimiter.java†L57-L68】
3. **熔断**：通过 `CircuitBreaker.of(...)` 创建实例，在下游调用成功后调用 `voteSuccess()`，失败则调用 `voteError()`，熔断器会根据窗口统计自动切换状态。 【F:fiber-gateway-support/src/main/java/io/fiber/net/support/CircuitBreaker.java†L9-L37】【F:fiber-gateway-support/src/main/java/io/fiber/net/support/CircuitBreakerStateMachine.java†L128-L177】
4. **半开恢复**：当处于 OPEN 状态达到等待时间后会自动转换为 HALF_OPEN，允许限定次数的请求试探，若成功率满足阈值则恢复 CLOSED，否则重新熔断。 【F:fiber-gateway-support/src/main/java/io/fiber/net/support/CircuitBreakerStateMachine.java†L66-L176】

## 与其他模块的协作
- Proxy 模块的 `GovLibConfigure` 将限流、熔断封装成脚本函数，脚本可直接调用以保护下游 API。 【F:fiber-gateway-proxy/src/main/java/io/fiber/net/proxy/gov/GovLibConfigure.java†L1-L200】
- Example 模块在 `StatisticInterceptor` 中可结合限流/熔断结果记录指标或拒绝请求，实现观测与治理联动。 【F:fiber-gateway-example/src/main/java/io/fiber/net/example/StatisticInterceptor.java†L18-L113】

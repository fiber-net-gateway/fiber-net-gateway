# 性能测试

环境在 ubuntu 虚拟机上测试，分别对比测试了 nginx（openresty），golang，spring-mvc，本项目（fiber-net）。
执行同样逻辑，结果显示本项目性能是 nginx-lua 1.5 倍，golang 的 1.3 倍，spring-mvc 4.4倍。

### 环境描述

- 测试工具:  [wrk](https://github.com/wg/wrk)
- JDK 环境：java 21

- 服务端口

| nginx | golang | fiber | spring_mvc |
|-------|--------|-------|------------|
| 80    | 8800   | 16688 | 8080       |

- cpu型号：

```
架构：                   x86_64
  CPU 运行模式：         32-bit, 64-bit
  Address sizes:         45 bits physical, 48 bits virtual
  字节序：               Little Endian
CPU:                     16
  在线 CPU 列表：        0-15
厂商 ID：                GenuineIntel
  型号名称：             13th Gen Intel(R) Core(TM) i7-13700H
    CPU 系列：           6
    型号：               186
    每个核的线程数：     1
    每个座的核数：       1
    座：                 16
    步进：               2
Virtualization features: 
  虚拟化：               VT-x
  超管理器厂商：         VMware
  虚拟化类型：           完全
```

### 代码逻辑

- nginx-luajit

```nginx
location /lua {
    content_by_lua_block {
        local json = require("cjson");
        ngx.req.read_body();
        local body = ngx.req.get_body_data();
        local jb = json.decode(body);
        local sum = 0;
        for i,v in ipairs(jb) do
            sum = sum + v;
        end
        ngx.say(json.encode("sum = "..sum));
    }
}
```

- golang

```go
func Hello(w http.ResponseWriter, r *http.Request) {
    //fmt.Println("handle heollo")
    body, err := io.ReadAll(r.Body)
    if err != nil {
        http.Error(w, err.Error(), http.StatusInternalServerError)
        return
    }
    defer r.Body.Close()
    var result interface{}
    err = json.Unmarshal(body, &result)
    if err != nil {
        fmt.Println("Error unmarshalling JSON:", err)
        return
    }

    // 断言result为[]interface{}，并检查其是否有效
    resultSlice, ok := result.([]interface{})
    if !ok {
        fmt.Println("Error: Result is not a slice.")
        return
    }

    sum := 0
    for _, num := range resultSlice {
        // 断言每个元素为float64，因为json.Unmarshal将数字转换为float64
        numFloat, ok := num.(float64)
        if !ok {
            fmt.Println("Error: Element in slice is not a number.")
            return
        }
        // 累加数值
        sum += int(numFloat)
    }
    fmt.Fprintf(w, "sum = %d", sum)
}

func main() {
    http.HandleFunc("/", Hello)
    err := http.ListenAndServe("127.0.0.1:8800", nil)
    if err != nil {
        fmt.Println("htpp listen failed")
    }
}
```

- fiber-net.js

```javascript
let bodyArray = req.readJson();
let sum = 0;
for (let _, value of bodyArray) {
    sum = sum + value;
}
return "sum = " + sum;
```

- spring-mvc

```java

@SpringBootApplication
public class SpringConfigApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringConfigApplication.class, args);
    }

    @RestController
    public static class JsonController {

        @PostMapping("/")
        public String t(@RequestBody Object body) {
            if (body instanceof List) {
                List list = (List) body;
                int sum = 0;
                for (Object o : list) {
                    if (o instanceof Number) {
                        sum += ((Number) o).intValue();
                    }
                }
                return "sum = " + sum;
            }
            return "unknown body";
        }
    }
}
```

### 执行结果

```bash
dear@ubuntu:~/Desktop$ wrk -t12 -c400 -d30s -s tt.lua http://127.0.0.1:80/lua
Running 30s test @ http://127.0.0.1:80/lua
  12 threads and 400 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     2.01ms    4.06ms 200.35ms   93.01%
    Req/Sec    30.20k    12.37k   85.76k    79.30%
  10706392 requests in 30.10s, 1.96GB read
Requests/sec: 355675.75
Transfer/sec:     66.82MB


dear@ubuntu:~/Desktop$ wrk -t12 -c400 -d30s -s tt.lua http://127.0.0.1:8800/
Running 30s test @ http://127.0.0.1:8800/
  12 threads and 400 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     2.25ms    5.67ms 314.97ms   94.43%
    Req/Sec    34.39k    17.69k  113.79k    82.01%
  12275851 requests in 30.09s, 1.42GB read
Requests/sec: 407923.10
Transfer/sec:     48.24MB


dear@ubuntu:~/Desktop$ wrk -t12 -c400 -d30s -s tt.lua http://127.0.0.1:16688
Running 30s test @ http://127.0.0.1:16688
  12 threads and 400 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     3.69ms   17.88ms 796.19ms   96.43%
    Req/Sec    43.92k    18.35k  101.81k    66.49%
  15681821 requests in 30.07s, 2.98GB read
Requests/sec: 521462.13
Transfer/sec:    101.45MB


dear@ubuntu:~/Desktop$ wrk -t12 -c400 -d30s -s tt.lua http://127.0.0.1:8080
Running 30s test @ http://127.0.0.1:8080
  12 threads and 400 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     8.49ms   30.61ms 789.15ms   95.79%
    Req/Sec    11.70k     4.69k   25.37k    78.10%
  4131632 requests in 30.10s, 477.51MB read
Requests/sec: 137281.57
Transfer/sec:     15.87MB
```

| 项目         | 端口    | 请求数      | 数据传输   | QPS       |
|------------|-------|----------|--------|-----------|
| nginx-lua  | 80    | 10706392 | 1.96GB | 355675.75 |
| golang     | 8800  | 12275851 | 1.42GB | 407923.10 |
| fiber-net  | 16688 | 15681821 | 2.98GB | 521462.13 |
| spring-mvc | 8080  | 4131632  | 0.48GB | 137281.57 |

可以看出 本项目的性能 超越 nginx-lua、golang 比较多，对spring-mvc 更是碾压级的超越。
fiber-net > golang > nginx-lua > spring-mvc

PS：从这里看出 golang 请求数比 nginx 高，但是数据传输更少，因为 golang 的默认 header 很少，实际 QPS 应该打点折扣。

# fiber-net-gateway
[中文版](doc/README_cn.md)

A low-code API gateway | FaaS framework based on a script engine. The function implemented is to execute a script for responding HTTP requests.

In the script, you can write some calls to http, dubbo services and other simple logic, which can implement protocol conversion, reverse proxy, service orchestration, [BFF](https://medium.com/mobilepeople/backend-for-frontend-pattern-why-you-need-to-know-it-46f94ce420b0) and other functions.

The project comprises three fundamental components:
- Netty-based asynchronous HTTP server.
- Netty-based asynchronous HTTP client, supporting https, DNS and connection pooling.
- Scripting engines with both interpreter and AOT (Ahead of Time Compilation).

The performance is very high, and it is the fastest java gateway | FaaS framework in the world (without any other one),
It is 1.5 times faster than nginx-luajit and 4.4 times faster than spring-mvc ([Performance Test](doc/benchmark.md)). There are some highlights as follows:
- A scripting language designed specifically for scenarios such as FaaS | API gateway, which is different from other complex and difficult-to-understand configurations for gateways and is simple and easy to understand.
- All components are asynchronous, no thread pool is used, and there are no other threads except for Netty's IO threads.

There are two ways to execute scripts, and both support Coroutine ([Principle Description](doc/script.md)):
- Interpreter mode: Compile the script into a series of assembly-like instructions, and simulate the execution of the instructions in Java, similar to a Lua interpreter;
- AOT (Ahead of Time Compilation) mode: The script will be compiled into a java class, which will be run directly by the JVM. Like rust, it supports coroutines through state machine compilation.

PS: It is only a framework. If it is used in a production environment, secondary development is required, such as accessing the configuration center and extending script functions.


# Synopsis

- Compilation
```bash
git clone https://github.com/fiber-net-gateway/fiber-net-gateway.git
cd fiber-net-gateway
mvn clean package -DskipTests
cd fiber-gateway-example/target
```

- Create a script file, the script content is to sum up the array
```bash
mkdir scripts
cat > scripts/fiber-net.js << EOF
let bodyArray = req.readJson();
let sum = 0;
for (let _, value of bodyArray) {
    sum = sum + value;
}
return "sum = " + sum;
EOF
```

- Running
```bash
java -jar fiber-gateway-example-1.0-SNAPSHOT.jar scripts
```

- Test: The script above will be executed once for each request
```bash
# Call the service to sum the array
curl 127.0.0.1:16688 -XPOST -H'Content-Type: application/json' -d'[1,2,3,4]' -i
# ======================================================
HTTP/1.1 200 OK
content-type: application/json; charset=utf-8
date: Fri, 10 May 2024 08:28:01 GMT
server: fiber-net(fn)/dev/dev
transfer-encoding: chunked

"sum = 10"
```

For detailed instructions, please refer to the [User Manual](doc/user.md)

For extended secondary development, please refer to [Development Document](doc/dev.md)

# PLUGINS
there are some plugins for fiber-gateway. [Official Plugins](https://github.com/fiber-net-gateway/fiber-gateway-plugin-parent)
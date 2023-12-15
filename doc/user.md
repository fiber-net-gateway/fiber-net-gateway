# 使用文档
本文介绍 fiber-gateway 脚本解释器 所支持的语法规则。

fiber-gateway 的语法设计类似于 javascript ，但与它相比更简单。它是纯面向过程的语法。

# 运算符
支持 一元、二元、三元运算符

### 二元
- 加 + ，减 - ，乘 * ，除 / ，模 %
- 匹配 ~ ，逻辑与 && ，逻辑或 ||
- 小于 < 、小于等于 <= ，大于 >，大于等于 >=，等于 == ，严格等于 ===，不等于 !=，严格不等于 !==
- 包含 include 、 in

### 一元
- 取类型 typeof
- 逻辑反 ! 
- 取正 +
- 取负 -
- 解构 ...
### 三元
- 选择 bool ? a : b 

# 语句
### if else
### for
### continue、break
### try catch
### return、throw

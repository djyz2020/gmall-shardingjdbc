## 拉取mysql镜像
```
docker pull mysql:5.7
```
## 1.master（主）配置
#### 1) 创建文件夹
```
mkdir -p /opt/mysql/mysql-master
mkdir -p /opt/mysql/mysql-master/conf.d
mkdir -p /opt/mysql/mysql-master/data
```
#### 2) 创建my.cnf配置文件
```
cd /opt/mysql/mysql-master
echo '[mysqld]
user=mysql                     # MySQL启动用户
default-storage-engine=INNODB  # 创建新表时将使用的默认存储引擎
character-set-server=utf8      # 设置mysql服务端默认字符集
pid-file        = /var/run/mysqld/mysqld.pid  # pid文件所在目录
socket          = /var/run/mysqld/mysqld.sock # 用于本地连接的socket套接字
datadir         = /var/lib/mysql              # 数据文件存放的目录
log-error       = /var/log/mysql/error.log
#bind-address   = 127.0.0.1                   # MySQL绑定IP
expire_logs_days=7                            # 定义清除过期日志的时间(这里设置为7天)
sql_mode=STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION # 定义mysql应该支持的sql语法，数据校验等!

# 允许最大连接数
max_connections=200

# ================= ↓↓↓ mysql主从同步配置start ↓↓↓ =================
# 同一局域网内注意要唯一
server-id=3310
# 开启二进制日志功能
log-bin=mysql-bin
# ================= ↑↑↑ mysql主从同步配置end ↑↑↑ =================

[client]
default-character-set=utf8  # 设置mysql客户端默认字符集
' > my.cnf
```
#### 3) 运行mysql
```
docker run --name mysql_server_3310 -d -p 3310:3306 --restart=always -v /opt/mysql/mysql-master/data/:/var/lib/mysql -v /opt/mysql/mysql-master/conf.d:/etc/mysql/conf.d -v /opt/mysql/mysql-master/my.cnf:/etc/mysql/my.cnf -e MYSQL_ROOT_PASSWORD=root mysql:5.7
```
#### 4) 进入容器
```
docker exec -it mysql_server_3310 /bin/bash
```
#### 5) 创建同步用户slave，并授权
```
mysql -uroot -proot
CREATE USER 'slave'@'%' IDENTIFIED BY '123456';
-- 授予slave用户 `REPLICATION SLAVE`权限和`REPLICATION CLIENT`权限，用于在`主` `从` 数据库之间同步数据
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'slave'@'%';
-- 授予所有权限则执行命令: GRANT ALL PRIVILEGES ON *.* TO 'slave'@'%';
-- 使操作生效
FLUSH PRIVILEGES;
```

## 2.slave(从)配置
#### 创建所需文件夹，用于映射容器相应文件路径
```
mkdir -p /opt/mysql/mysql-slave
mkdir -p /opt/mysql/mysql-slave/conf.d
mkdir -p /opt/mysql/mysql-slave/data
```
#### 创建`my.cnf`配置文件
```
cd /opt/mysql/mysql-slave
echo '[mysqld]
user=mysql                     # MySQL启动用户
default-storage-engine=INNODB  # 创建新表时将使用的默认存储引擎
character-set-server=utf8      # 设置mysql服务端默认字符集
pid-file        = /var/run/mysqld/mysqld.pid  # pid文件所在目录
socket          = /var/run/mysqld/mysqld.sock # 用于本地连接的socket套接字
datadir         = /var/lib/mysql              # 数据文件存放的目录
log-error      = /var/log/mysql/error.log
#bind-address   = 127.0.0.1                   # MySQL绑定IP
expire_logs_days=7                            # 定义清除过期日志的时间(这里设置为7天)
sql_mode=STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION # 定义mysql应该支持的sql语法，数据校验等!

# 允许最大连接数
max_connections=200

# ================= ↓↓↓ mysql主从同步配置start ↓↓↓ =================
# 同一局域网内注意要唯一
server-id=3311  
# 开启二进制日志功能，以备slave作为其它slave的master时使用
log-bin=mysql-slave-bin
# relay_log配置中记日志
relay_log=edu-mysql-relay-bin
# ================= ↑↑↑ mysql主从同步配置end ↑↑↑ =================

[client]
default-character-set=utf8  # 设置mysql客户端默认字符集
' > my.cnf
```
#### 运行镜像
```
docker run --name mysql_server_3311 -d -p 3311:3306 --restart=always -v /opt/mysql/mysql-slave/data/:/var/lib/mysql -v /opt/mysql/mysql-slave/conf.d:/etc/mysql/conf.d -v /opt/mysql/mysql-slave/my.cnf:/etc/mysql/my.cnf -e MYSQL_ROOT_PASSWORD=root mysql:5.7
```

## 3.关联master和slave
#### 1) 在master服务器中进入mysql查看master状态
```
-- 进入master mysql
docker exec -it mysql_server_3310 /bin/bash
mysql -uroot -proot
-- 查看状态
show master status;
```
#### 2) 在slave服务器中进入mysql，启动主从同步
```
docker exec -it mysql_server_3310 /bin/bash
mysql -uroot -proot
change master to master_host='192.168.126.135',master_port=3310, master_user='slave', master_password='123456', master_log_file='mysql-bin.000003', master_log_pos=0, master_connect_retry=30;

# master_host ：master服务器地址
# master_port ：端口号
# master_user ：用于数据同步的用户（之前在master中创建授权的用户）
# master_password ：用于同步用户的密码
# master_log_file ：指定slave从哪个日志文件开始复制数据，即之前提到的File字段值
# master_log_pos ：从哪个Position开始读，即之前master中的Position字段值，0则是从头开始完整的拷贝master库
# master_connect_retry ：连接失败时重试的时间间隔，默认是60秒

# 开启主从同步过程  【停止命令：stop slave;】
start slave;
# 查看主从同步状态
show slave status \G;
```
## 4.设置允许从虚拟机网关访问数据库
#### 1) 主库设置
```
docker exec -it mysql_server_3310 /bin/bash
mysql -uroot -proot
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' IDENTIFIED BY '123456' WITH GRANT OPTION;
FLUSH PRIVILEGES;
```
#### 2) 从库设置
主库设置完成后，从库会自动同步配置

备注：如果从库同步主库SQL异常，可以尝试如下命令解决
```
> stop slave;
> SET GLOBAL SQL_SLAVE_SKIP_COUNTER=1; 
> START SLAVE;
```

## 5.分库分表实践
#### 创建订单库 order_db
```
CREATE DATABASE `order_db` CHARACTER SET 'utf8' COLLATE 'utf8_general_ci';
```
#### 在order_db中创建t_order_1、t_order_2表
```
USE `order_db`;
DROP TABLE IF EXISTS `t_order_1`;
CREATE TABLE `t_order_1`  (
  `order_id` bigint(20) NOT NULL COMMENT '订单id',
  `price` decimal(10, 2) NOT NULL COMMENT '订单价格',
  `user_id` bigint(20) NOT NULL COMMENT '下单用户id',
  `status` varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '订单状态',
  PRIMARY KEY (`order_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;
DROP TABLE IF EXISTS `t_order_2`;
CREATE TABLE `t_order_2`  (
  `order_id` bigint(20) NOT NULL COMMENT '订单id',
  `price` decimal(10, 2) NOT NULL COMMENT '订单价格',
  `user_id` bigint(20) NOT NULL COMMENT '下单用户id',
  `status` varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '订单状态',
  PRIMARY KEY (`order_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;
```

#### 添加sharding-jdbc依赖
```
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>5.1.48</version>
</dependency>

<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>druid-spring-boot-starter</artifactId>
    <version>1.2.12</version>
</dependency>

<dependency>
    <groupId>org.apache.shardingsphere</groupId>
    <artifactId>sharding-jdbc-spring-boot-starter</artifactId>
    <version>4.1.1</version>
</dependency>
```

#### 添加sharding-jdbc配置
```
# 定义数据源
spring.shardingsphere.datasource.names=m1
spring.shardingsphere.datasource.m1.type=com.alibaba.druid.pool.DruidDataSource
spring.shardingsphere.datasource.m1.driver‐class‐name=com.mysql.cj.jdbc.Driver
spring.shardingsphere.datasource.m1.url=jdbc:mysql://192.168.126.135:3310/order_db?useUnicode=true&characterEncoding=utf8&useSSL=true&serverTimezone=Asia/Shanghai
spring.shardingsphere.datasource.m1.username=root
spring.shardingsphere.datasource.m1.password=123456
# 指定t_order表的数据分布情况，配置数据节点
spring.shardingsphere.sharding.tables.t_order.actual-data-nodes=m1.t_order_$‐>{1..2}
# 指定t_order表的主键生成策略为SNOWFLAKE
spring.shardingsphere.sharding.tables.t_order.key-generator.column=order_id
spring.shardingsphere.sharding.tables.t_order.key-generator.type=SNOWFLAKE
# 指定t_order表的分片策略，分片策略包括分片键和分片算法
spring.shardingsphere.sharding.tables.t_order.table-strategy.inline.sharding-column=order_id
spring.shardingsphere.sharding.tables.t_order.table-strategy.inline.algorithm-expression=t_order_$‐>{order_id % 2 + 1}

# 打开sql输出日志
spring.shardingsphere.props.sql.show=true
logging.level.root=info
logging.level.org.springframework.web=info
```


阿里云脚手架：https://start.aliyun.com/bootstrap.html/



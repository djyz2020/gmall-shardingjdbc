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
### 5.1 水平分表（大表拆分提高查询性能，主从提高查询性能）
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
    <artifactId>druid</artifactId>
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
spring.shardingsphere.sharding.tables.t_order.actual-data-nodes=m1.t_order_$->{1..2}
# 指定t_order表的主键生成策略为SNOWFLAKE
spring.shardingsphere.sharding.tables.t_order.key-generator.column=order_id
spring.shardingsphere.sharding.tables.t_order.key-generator.type=SNOWFLAKE
# 指定t_order表的分片策略，分片策略包括分片键和分片算法
spring.shardingsphere.sharding.tables.t_order.table-strategy.inline.sharding-column=order_id
spring.shardingsphere.sharding.tables.t_order.table-strategy.inline.algorithm-expression=t_order_$->{order_id % 2 + 1}

# 打开sql输出日志
spring.shardingsphere.props.sql.show=true
logging.level.root=info
logging.level.org.springframework.web=info
```

#### 5.2 垂直分库 (专库专用，减轻数据库压力，提高查询性能)
##### (1) 创建数据库
创建数据库user_db
```
CREATE DATABASE user_db CHARACTER SET 'utf8' COLLATE 'utf8_general_ci';
```
在user_db中创建t_user表
```
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user`  (
  `user_id` bigint(20) NOT NULL COMMENT '用户id',
  `fullname` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '用户姓名',
  `user_type` char(1) DEFAULT NULL COMMENT '用户类型',
  PRIMARY KEY (`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;
```
#### (2)在Sharding-JDBC规则中修改
a) 新增m0数据源，对应user_db
```
spring.shardingsphere.datasource.names = m0,m1,m2
spring.shardingsphere.datasource.m0.type = com.alibaba.druid.pool.DruidDataSource
spring.shardingsphere.datasource.m0.driver‐class‐name = com.mysql.jdbc.Driver
spring.shardingsphere.datasource.m0.url = jdbc:mysql://localhost:3306/user_db?useUnicode=true
spring.shardingsphere.datasource.m0.username = root
spring.shardingsphere.datasource.m0.password = root
```
b) t_user分表策略，固定分配至m0的t_user真实表
```
spring.shardingsphere.sharding.tables.t_user.actual‐data‐nodes = m$‐>{0}.t_user
spring.shardingsphere.sharding.tables.t_user.table‐strategy.inline.sharding‐column = user_id
spring.shardingsphere.sharding.tables.t_user.table‐strategy.inline.algorithm‐expression = t_user
```
c) 数据操作
新增UserDao:
```
@Mapper
@Component
public interface UserDao {
    /**
     * 新增用户
     * @param userId 用户id
     * @param fullname 用户姓名
     * @return
     */
    @Insert("insert into t_user(user_id, fullname) value(#{userId},#{fullname})")
    int insertUser(@Param("userId")Long userId,@Param("fullname")String fullname);
    /**
     * 根据id列表查询多个用户
     * @param userIds 用户id列表
     * @return List
     */
    @Select({"<script>",
            " select",
            " * ",
            " from t_user t ",
            " where t.user_id in",
            "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "</script>"
    })
    List<Map> selectUserbyIds(@Param("userIds")List<Long> userIds);
}
```
d) 测试
新增单元测试方法：
```
@Test
public void testInsertUser(){
    for (int i = 0 ; i<10; i++){
        Long id = i + 1L;
        userDao.insertUser(id,"姓名"+ id );
    }
}
@Test
public void testSelectUserbyIds(){
    List<Long> userIds = new ArrayList<>();
    userIds.add(1L);
    userIds.add(2L);
    List<Map> users = userDao.selectUserbyIds(userIds);
    System.out.println(users);
}
```
#### 5.3 公共表（广播模式）
a) 创建数据库order_db_1和order_db_2
```
CREATE DATABASE order_db_1 CHARACTER SET 'utf8' COLLATE 'utf8_general_ci';
CREATE DATABASE order_db_2 CHARACTER SET 'utf8' COLLATE 'utf8_general_ci';
```
b) 分别在user_db、order_db_1、order_db_2中创建t_dict表：
```
CREATE TABLE `t_dict`  (
  `dict_id` bigint(20) NOT NULL COMMENT '字典id',
  `type` varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '字典类型',
  `code` varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '字典编码',
  `value` varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '字典值',
  PRIMARY KEY (`dict_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;
```
c) 在Sharding-JDBC规则中修改
```
# 指定t_dict为公共表
spring.shardingsphere.sharding.broadcast‐tables=t_dict
```
d) 新增DictDao
```
@Mapper
@Component
public interface DictDao {
    /**
     * 新增字典
     * @param type 字典类型
     * @param code 字典编码
     * @param value 字典值
     * @return
     */
    @Insert("insert into t_dict(dict_id,type,code,value) value(#{dictId},#{type},#{code},#{value})")
    int insertDict(@Param("dictId") Long dictId,@Param("type") String type, @Param("code")String code, @Param("value")String value);
    /**
     * 删除字典
     * @param dictId 字典id
     * @return
     */
    @Delete("delete from t_dict where dict_id = #{dictId}")
    int deleteDict(@Param("dictId") Long dictId);
}
```
e) 新增单元测试
```
@Test
public void testInsertDict(){
    dictDao.insertDict(1L,"user_type","0","管理员");    
    dictDao.insertDict(2L,"user_type","1","操作员");    
}
@Test
public void testDeleteDict(){
    dictDao.deleteDict(1L);    
    dictDao.deleteDict(2L);    
}
```
f) 字典关联查询
字典表已在各各分库存在，各业务表即可和字典表关联查询。
```
/**
 * 根据id列表查询多个用户，关联查询字典表
 * @param userIds 用户id列表
 * @return
 */
@Select({"<script>",
        " select",
        " * ",
        " from t_user t ,t_dict b",
        " where t.user_type = b.code and t.user_id in",
        "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>",
        "#{id}",
        "</foreach>",
        "</script>"
})
List<Map> selectUserInfobyIds(@Param("userIds")List<Long> userIds);
```
g) 测试方法
```
@Test 
public void testSelectUserInfobyIds(){
    List<Long> userIds = new ArrayList<>();
    userIds.add(1L);
    userIds.add(2L);
    List<Map> users = userDao.selectUserInfobyIds(userIds);
    JSONArray jsonUsers = new JSONArray(users);
    System.out.println(jsonUsers);
}
```

阿里云脚手架：https://start.aliyun.com/bootstrap.html/



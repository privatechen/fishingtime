# 编码规范

## 通用

- SQL 必须使用 MyBatis XML（禁止 @Select / @Insert / @Update）
- Controller 不写业务逻辑
- Entity 不直接返回前端
- DTO 与 VO 分离
- Mapper 不写业务逻辑
- Service 不返回 Entity（返回 DTO）
- API 使用统一 `ApiResponse`
- 禁止 `SELECT *`
- SQL 全部参数绑定（#{}）
- 密码不得记录日志
- 金额使用 BigDecimal

## 包命名

```
com.fishingtime.{module}.controller
com.fishingtime.{module}.dto
com.fishingtime.{module}.domain
com.fishingtime.{module}.service
com.fishingtime.{module}.service.impl
com.fishingtime.{module}.mapper
```

## 日志规范

- 使用 SLF4J
- Logback Pattern 禁止使用 %L %M %C %caller
- 使用参数占位符 `log.info("msg {}", var)`

## 测试规范

- Service 测试使用 Mockito（@ExtendWith(MockitoExtension.class)）
- Controller 测试使用 MockMvc（@WebMvcTest 或 @SpringBootTest + @AutoConfigureMockMvc）
- Mapper 全部 Mock，不连接数据库
- 测试使用 application-test.yml 排除数据源

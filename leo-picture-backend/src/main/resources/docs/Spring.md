# Spring框架中的单例bean是线程安全的吗?

singleton:bean在每个SpringlOC容器中只有一个实例。
prototype:一个bean的定义可以有多个实例。

**不是线程安全的**
Spring框架中有一个@Scope注解，默认的值就是singleton，**单例的**。

因为一般在spring中管理的bean的中都是注入**无状态**的，对象无法修改，没有线程安全问题，如果在bean中定义了**可修改的成员变量**，是要考虑线程安全问题的，可以使用多例或者加锁来解决

Spring框架并没有对单例bean进行任何多线程的封装处理。关于单例bean的线程安全和并发问题需要开发者自行去搞定。
比如:我们通常在项目中使用的**Spring的bean**都是**不可可变的状态**(比如Service类和DAO类)，所以在**某种程度**上说Spring的单例bean是**线程安全的**。

- **默认不安全**：Spring 的单例 Bean（`@Scope("singleton")`）在多个线程共享时，如果存在**可修改的成员变量**（如全局计数器、缓存 Map），会有线程安全问题。

- **安全的场景**：如果 Bean 是**无状态的**（如 Service、DAO 类，仅依赖注入其他 Bean 或局部变量），则天然线程安全。

-  **如何保证线程安全？**

  - **无状态设计**（推荐）：避免定义可修改的成员变量。
  - **改为多例**：`@Scope("prototype")`，每个请求创建新实例（但增加开销）。
  - **加锁**：用 `synchronized` 或 `ConcurrentHashMap` 等线程安全工具。

  **3. Spring 的态度**：

  - Spring **不处理单例 Bean 的线程安全**，开发者需自行保证。

# 什么是AOP，你们项目中有没有使用到AOP，Spring中的事务是如何实现的

面向切面编程，用于将那些**与业务无关**，但却对多个对象**产生影响的公共行为**和逻辑，抽取**公共模块复用**，降低耦合

记录操作日志，缓存，spring实现的事务
核心是:使用aop中的环绕通知+切点表达式(找到要记录日志的方法)通过环绕通知的参数获取请求方法的参数(类、方法、注解、请求方式等)，获取到这些参数以后，保存到数据库

其本质是通过AOP功能，对方法前后进行拦截，在执行方法之前开启事务，在执行完目标方法之后根据执行情况提交或者回滚事务。

# Spring中事务失效的场景有哪些

异常捕获处理，自己处理了异常，没有抛出，解决:手动抛出

检查异常，配置rollbackFor属性为Exception

非public方法导致的事务失效，改为public(原因为Spring 为方法创建代理、添加事务通知、前提条件都是该方法是 public 的)

# Spring的bean的生命周期，Spring容器是如何管理和创建bean实例

BeanDefinition
Spring容器在进行实例化时，会将xml配置的<bean>的信息封装成一个BeanDefinition对象，Spring根据BeanDefinition来创建Bean对象，里面有很多的属性用来描述Bean

![](D:\在图片\Spring\004.png)



通过BeanDefinition获取bean的定义信息

调用构造函数实例化bean

bean的依赖注入

处理Aware接囗(BeanNameAware、BeanFactoryAware、ApplicationContextAware)

Bean的后置处理器BeanPostProcessor-前置

初始化方法(InitializingBean、init-method)

Bean的后置处理器BeanPostProcessor-后置

销毁bean

<img src="D:\在图片\Spring\002png.png" style="zoom: 50%;" />

# Spring启动流程

**1）加载配置文件，初始化容器：**

Spring 启动时首先会读取配置文件（如 XML 配置文件、Java Config 类等），包括配置数据库连接、事务管理、AOP 配置等。

**2）实例化容器：**

Spring 根据配置文件中的信息创建容器 ApplicationContext，在容器启动阶段实例化 BeanFactory，并加载容器中的 BeanDefinitions。

**3）解析 BeanDefinitions：**

Spring 容器会解析配置文件中的 BeanDefinitions，即声明的 Bean 元数据，包括 Bean 的作用域、依赖关系等信息。

**4）实例化 Bean：**

Spring 根据 BeanDefinitions 实例化 Bean 对象，将其放入容器管理。

**5）注入依赖：**

Spring 进行依赖注入，将 Bean 之间的依赖关系进行注入，包括构造函数注入、属性注入等。

6）处理 Bean 生命周期初始化方法：

- Spring 调用 Bean 初始化方法（如果有定义的话），对 Bean 进行初始化。
- 如果 Bean 实现了 `InitializingBean` 接口，Spring 会调用其 `afterPropertiesSet` 方法。

**7）处理 BeanPostProcessors：**

容器定义了很多 BeanPostProcessor，处理其中的自定义逻辑，例如 postProcessBeforeInitialization 会在 Bean 初始化前调用， postProcessAfterInitialization 则在之后调用。

Spring AOP 代理也在这个阶段生成。

**8）发布事件：**

Spring 可能会在启动过程中发布一些事件，比如容器启动事件。

**9）完成启动：**

当所有 Bean 初始化完毕、依赖注入完成、AOP 配置生效等都准备就绪时，Spring 容器启动完成。

# SpringIOC启动流程：

先和面试官说一共有四个阶段，分别是**启动、Bean 定义注册、实例化和依赖注入、初始化**，然后停顿下，如果面试官示意你继续，然后再细化讲解每个阶段的细节。

**1）启动阶段：**

- 配置加载：加载配置文件或配置类，IoC 容器首先需要加载应用程序的配置信息，这些配置信息可以是 XML 配置文件、Java 配置类或注解配置等方式。
- 创建容器：Spring 创建 IOC 容器（BeanFactory 、 ApplicationContext），准备加载和管理 Bean。

**2）Bean定义注册阶段：**

- 解析和注册：BeanDefinitionReader 读取解析配置中的 Bean 定义，并**将其**注册到容器中，形成 BeanDefinition 对象。

**3）实例化和依赖注入：**

- 实例化：根据 BeanDefinition 创建 Bean 的实例。
- 依赖注入：根据 BeanDefinition 中的依赖关系，可以通过构造函数注入、Setter 注入或字段注入，将依赖注入到 Bean 中。

**4）初始化：**

- BeanPostProcessor 处理：这些处理器会在 Bean 初始化生命周期中加入定义的处理逻辑，postProcessBeforeInitialization 和 postProcessAfterInitialization 分别在 Bean 初始化前后被调用。
- Aware 接口调用：如果 Bean 实现了 Aware 接口（如 BeanNameAware、BeanFactoryAware），Spring 会回调这些接口，传递容器相关信息。
- 初始化方法调用：调用 Bean 的初始化方法（如通过 @PostConstruct 注解标注的方法，或实现 InitializingBean 接口的 bean 会被调用 afterPropertiesSet 方法）。

# 什么是Spring的循环依赖?

<img src="D:\在图片\Spring\005.png" style="zoom: 50%;" />

**循环依赖**:循环依赖其实就是循环引用,也就是两个或两个以上的bean互相持有对方,最终形成闭环。**比如A依赖于B,B依赖于A**

循环依赖在spring中是允许存在，spring框架依据三级缓存已经解决了大部分的循环依赖
 一级缓存:单例池，缓存已经经历了完整的生命周期，已经初始化完成的bean对象
二级缓存:缓存早期的bean对象(生命周期还没走完)
三级缓存:缓存的是ObjectFactory，表示对象工厂，用来**延迟创建代理对象**， 解决AOP 代理对象的正确生成

<img src="D:\在图片\Spring\006.png" style="zoom: 67%;" />

# SpringMVC的执行流程知道嘛

<img src="D:\在图片\Spring\007.png" style="zoom: 67%;" />

① 用户发送请求到前端控制器DispatcherServlet

② DispatcherServlet收到请求调用HandlerMapping(处理器映射器)

③ HandlerMapping找到具体的处理器，生成处理器对象及处理器拦截器(如果有)，再一起返回给DispatcherServlet。

④ DispatcherServlet调用HandlerAdapter(处理器适配器)

⑤ HandlerAdapter经过适配调用具体的处理器(Handler/Controller)后返回ModelAndView(模型数据 + 视图策略)给DispatcherServlet。

⑥方法上添加了@ResponseBody，跳过`ModelAndView`的视图渲染流程

⑦通过HttpMessageConverter来返回结果转换为JSON并响应

# Springboot自动配置原理

<img src="D:\在图片\Spring\008.png" style="zoom: 67%;" />

<img src="D:\在图片\Spring\009.png" style="zoom: 67%;" />

- @SpringBootConfiguration:该注解与 @Configuration 注解作用相同，用来声明当前也是一个配置类,。
- @ComponentScan:组件扫描，默认扫描当前引导类所在包及其子包。
- @EnableAutoConfiguration:SpringBoot实现自动化配置的核心注解

1，在Spring Boot项目中的引导类上有一个注解@SpringBootApplication，这个注解是对三个注解进行了封装，分别是:
@SpringBootConfiguration
@EnableAutoConfiquration
@ComponentScan
2，其中@EnableAutoConfiguration是实现自动化配置的**核心注解**。 该注解通过@Import注解导入对应的**配置选择器**。

内部就是**读取了**该项目引用的Jar包的classpath路径下META-INF/**spring.factories**问件，文件包含了**所配置的类的全类名**。 在这些配置类中所定义的Bean会**根据条件判断（注解）**所指定的条件来**决定**是否需要将其导入到Spring容器中。

3,条件判断会有像@ConditionalOnClass这样的注解，判断是否有对应的class字节码文件，如果有则加载该类，把这个配置类的所有的Bean放入spring容器中使用。

**自定义Starter**：

以封装一个**图库文件上传Starter**为例：

1 创建Starter模块

命名规范：`xxx-spring-boot-starter`（如`cos-uploader-spring-boot-starter`）

依赖：必须包含`spring-boot-configuration-processor`和`spring-boot-autoconfigure`。

2 定义配置类

```java
Java@Configuration
@EnableConfigurationProperties(COSProperties.class) // 绑定配置
@ConditionalOnClass(COSClient.class) // 依赖腾讯云COS SDK
public class COSAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public COSUploader cosUploader(COSProperties properties) {
        return new COSUploader(properties);
    }
}
```

3 配置属性类

```java
Java@ConfigurationProperties(prefix = "cos")
public class COSProperties {
    private String secretId;
    private String secretKey;
    // getters/setters...
}
```

4 注册自动配置

在`resources/META-INF/spring.factories`中声明：

```xml
 org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
  com.example.COSAutoConfiguration
```

5 使用Starter

其他项目引入该Starter后，可直接通过`@Autowired`注入`COSUploader`，并在`application.yml`中配置：

```yaml
cos:
 secret-id: "xxx"
 secret-key: "xxx"
```

 *“我封装过文件上传Starter，关键在于`@ConfigurationProperties`绑定配置和`META-INF/spring.factories`声明自动配置类。”*



# Spring框架常见注解(Spring、Springboot、Springmvc)

<img src="D:\在图片\Spring\0010.png" style="zoom: 67%;" />

<img src="D:\在图片\Spring\0011.png" style="zoom: 67%;" />

<img src="D:\在图片\Spring\0012.png" style="zoom: 67%;" />

# MyBatis执行流程

1. 读取MyBatis配置文件:mybatis-config.xml加载运行环境和映射文件
2. 构造会话工厂SqlSessionFactory
3. 会话工厂创建SqlSession对象(包含了执行SQL语句的所有方法）
4. 通过 `MappedStatement`**类型**参数 **获取SQL和参数映射**调用 `Executor`执行器（Executor）处理SQL：
   - 维护**缓存**（一级缓存）。
   - 通过 `MappedStatement` 获取SQL和参数映射。
5. 输入**参数映射**将Java对象转为SQL**参数**，输出**结果映射**将结果集转为Java对象。

# Mybatis是否支持延迟加载?延迟加载的底层原理知道吗?

<img src="D:\在图片\Spring\0013.png" style="zoom: 67%;" />

延迟加载的意思是:就是在需要用到数据时才进行加载，不需要用到数据时就不加载数据。

Mybatis支持一对一关联对象和一对多关联集合对象的延迟加载

在Mybatis**配置文件**中，可以**配置**是否启用延迟加载**lazyLoadingEnabled=true l false****，默认是关闭的

1.使用**CGLIB**创建目标对象的代理对象

2.当调用目标方法时，进入拦截器invoke方法，发现目标方法是null值，执行sql查询

3.获取数据以后，调用set方法设置属性值，再继续查询目标方法，就有值了

# Mybatis的一级、二级缓存用过吗?

一级缓存: 基于 PerpetualCache(迫排球哦) 的 HashMap **本地缓存**，其存储**作用域为 Session**，当Session进行flush或close之后，该Session中的所有Cache就将清空，默认打开一级缓存
二级缓存是基于namespace和mapper的作用域起作用的，不是依赖于SQLsession，默认也是采用PerpetualCache，HashMap 存储。**需要单独开启**，一个是核心配置，一个是mapper映射文件

# Mybatis的二级缓存什么时候会清理缓存中的数据

当某一个作用域(一级缓存 Session/二级缓存Namespaces)进行了新增、修改、删除操作后，默认该作用域下所有 select 中的缓存将被 clear。

# **Spring 事务和 MySQL 事务的关系**

- **Spring 事务**：是 **逻辑事务**，提供声明式（`@Transactional`）和编程式事务管理，底层依赖数据库事务。
- **MySQL 事务**：是 **物理事务**，基于 InnoDB 引擎的 ACID 特性实现。

Spring 的事务传播行为（如 `PROPAGATION_REQUIRED`）是通过 **控制连接的获取和释放** 实现的

Spring 本身**不实现事务**，只是 **封装** 了 JDBC/JPA 等的事务操作。

1. Spring 在调用 `@Transactional` 方法时，会 **从数据源获取连接**。
2. 设置连接的事务隔离级别、超时时间等（根据 `@Transactional` 配置）。
3. 调用业务方法，执行 SQL。
4. 根据方法执行结果决定 **提交或回滚**（通过 JDBC 调用 MySQL 的 `commit()` 或 `rollback()`）。

#### **`@Configuration` 和 `@Component` 能不能互换？**

| `@Configuration`                                 | `@Component`                                          |
| ------------------------------------------------ | ----------------------------------------------------- |
| 用于定义 **配置类**                              | 用于普通组件                                          |
| 内部 `@Bean` 方法会被 **CGLIB 代理**（保证单例） | 内部 `@Bean` **方法不会被代理**（每次调用返回新实例） |

- 如果需要定义 `@Bean`，必须用 `@Configuration`。
- 如果只是普通组件，用 `@Component`。

**`@Bean` 一般与@configuration搭配，讲方法的返回值注册为一个Bean交给spring容器，**用于导入第三方库或者自定义一些Bean的初始化。

#### **`@Service` 和 `@Component` 能互换吗？**

- **可以互换**，功能完全相同。
  - @Service` 是 `@Component` 的 **语义化特化**，用于标注 **业务逻辑层**。

####  **`@Controller` 和 `@Component` 能互换吗？**

- **技术上可以互换**，但 **实际不能**。
- 原因
  - `@Controller` 是 `@Component` 的特化，用于 **MVC 中的控制器**。
  - 只有 `@Controller` 能处理 HTTP 请求（配合 `@RequestMapping`）。

| 问题                             | 关键答案                                                     |
| -------------------------------- | ------------------------------------------------------------ |
| 事件监听                         | 基于观察者模式，`@EventListener` 或 `ApplicationListener`    |
| 三级缓存                         | 解决循环依赖：一级缓存（成品）、二级缓存（半成品）、三级缓存（工厂） |
| Spring 事务 vs MySQL 事务        | Spring 是逻辑事务，依赖数据库物理事务                        |
| `@Configuration` vs `@Component` | `@Configuration` 的 `@Bean` 方法会被代理                     |
| `@Bean` 作用                     | 注册第三方或复杂初始化的 Bean                                |
| `@Service` vs `@Component`       | 功能相同，`@Service` 是语义化特化                            |
| `@Controller` vs `@Component`    | `@Controller` 才能处理 HTTP 请求                             |

### Spring的事务传播机制

是为了解决业务**方法嵌套调用**时的边界问题，定义了一个事务方法调用另一个事务方法时，事务该如何**传递**

#### **Spring 默认有7个事务传播行为（Propagation Behavior）**

| 传播行为               | 说明                                                         | 适用场景                         |
| ---------------------- | ------------------------------------------------------------ | -------------------------------- |
| **`REQUIRED`**（默认） | 如果当前没有事务，就新建一个；如果已存在事务，则加入该事务   | 大多数业务方法                   |
| **`SUPPORTS`**         | 如果当前有事务，就加入；如果没有，就以非事务方式执行         | 查询方法，可适应事务或非事务环境 |
| **`MANDATORY`**        | 必须在一个已有事务中运行，否则抛出异常                       | 强制要求调用方开启事务           |
| **`REQUIRES_NEW`**     | 无论当前是否有事务，都新建一个事务，挂起当前事务（如果存在） | 独立事务（如日志记录）           |
| **`NOT_SUPPORTED`**    | 以非事务方式执行，挂起当前事务（如果存在）                   | 不依赖事务的操作（如发送消息）   |
| **`NEVER`**            | 必须在非事务环境下执行，否则抛出异常                         | 强制要求调用方不开启事务         |
| **`NESTED`**           | 如果当前有事务，则在嵌套事务中执行（可部分回滚）；否则新建事务 | 复杂业务（如订单创建+库存扣减）  |

####  **常用传播行为**：

- **REQUIRED**（默认）
- **REQUIRES_NEW**（独立事务）
- **NESTED（嵌套事务）**

## **CGLIB 代理（Code Generation Library Proxy）**

### **什么是 CGLIB 代理？**

- **动态代理的一种**，用于在运行时生成目标类的子类代理。

- 与 JDK 动态代理的区别：

  |              | JDK 动态代理     | CGLIB 代理                        |
  | ------------ | ---------------- | --------------------------------- |
  | **代理方式** | 基于接口         | 基于类继承                        |
  | **性能**     | 稍慢（反射调用） | 更快（直接调用）                  |
  | **限制**     | 只能代理接口     | 可代理普通类（final 类/方法除外） |

**CGLIB 代理**

- **核心答案**： “CGLIB 是通过动态生成目标类的子类来实现代理的，比 JDK 动态代理更灵活（不依赖接口）， 但无法代理 final 类/方法。Spring 在 AOP 和事务管理中使用它增强普通类方法。”
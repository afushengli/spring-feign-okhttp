# spring-feign-okhttp

## feign配置阶段

### @EnableFeignClients注解

FeignClientsRegistrar是注解import的类. 依据自动装配的原则. 这个导入类会被平铺开执行的. 这个类是ImportBeanDefinitionRegistrar的实现类. 那么

```java
@Override
	public void registerBeanDefinitions(AnnotationMetadata metadata,
			BeanDefinitionRegistry registry) {
		registerDefaultConfiguration(metadata, registry);
		registerFeignClients(metadata, registry);
	}
```
这个方法就是核心. 

#### case 1. registerDefaultConfiguration(metadata, registry);

下面代码是其调用链.
```java
private void registerDefaultConfiguration(AnnotationMetadata metadata,
			BeanDefinitionRegistry registry) {
		Map<String, Object> defaultAttrs = metadata
				.getAnnotationAttributes(EnableFeignClients.class.getName(), true);

		if (defaultAttrs != null && defaultAttrs.containsKey("defaultConfiguration")) {
			String name;
			if (metadata.hasEnclosingClass()) {
				name = "default." + metadata.getEnclosingClassName();
			}
			else {
				name = "default." + metadata.getClassName();
			}
			registerClientConfiguration(registry, name,
					defaultAttrs.get("defaultConfiguration"));
		}
	}
private void registerClientConfiguration(BeanDefinitionRegistry registry, Object name,
			Object configuration) {
		BeanDefinitionBuilder builder = BeanDefinitionBuilder
				.genericBeanDefinition(FeignClientSpecification.class);
		builder.addConstructorArgValue(name);
		builder.addConstructorArgValue(configuration);
		registry.registerBeanDefinition(
				name + "." + FeignClientSpecification.class.getSimpleName(),
				builder.getBeanDefinition());
	}
```

首先通过EnableFeignClients注解的元数据. 就是注解的属性值. 然后注册. spring ioc的注册都是注册BeanDefinition. 所以包装了FeignClientSpecification的BeanDefinition.
并且添加了两个构造方法的参数. 1 name, 2 configuration 这个属性值来自于@EnableFeignClients注解的defaultConfiguration属性.

#### case 2. registerFeignClients(metadata, registry);

从名字可以看出 是注册Feign客户端的意思. 具体流程是
```java
public void registerFeignClients(AnnotationMetadata metadata,
			BeanDefinitionRegistry registry) {
                // 获得了一个ClassPath候选人扫描器. 这是一个通过ClassPath扫描的. 候选人的意思是需要过滤器拿到候选人
		ClassPathScanningCandidateComponentProvider scanner = getScanner();
        // 类加载器
		scanner.setResourceLoader(this.resourceLoader);
        // 扫描的包路径
		Set<String> basePackages;

		Map<String, Object> attrs = metadata
				.getAnnotationAttributes(EnableFeignClients.class.getName());
                //候选人过滤器 FeignClient注解标记的类
		AnnotationTypeFilter annotationTypeFilter = new AnnotationTypeFilter(
				FeignClient.class);
                // @EnableFeignClients注解如果指定了clients
		final Class<?>[] clients = attrs == null ? null
				: (Class<?>[]) attrs.get("clients");
                // 没有执行的情况下, 使用EnableFeignClients注解basePackages属性执行的包名 
                // 这里逻辑是 value属性 basePackages属性 basePackageClasses属性全都要. 如果都没设置值. 那就扫描@EnableFeignClients注解所在包的包路径
		if (clients == null || clients.length == 0) {
			scanner.addIncludeFilter(annotationTypeFilter);
			basePackages = getBasePackages(metadata);
		}
        // 这里的逻辑差不多. 扫描的是执行clients类的包路径
		else {
			final Set<String> clientClasses = new HashSet<>();
			basePackages = new HashSet<>();
			for (Class<?> clazz : clients) {
				basePackages.add(ClassUtils.getPackageName(clazz));
				clientClasses.add(clazz.getCanonicalName());
			}
			AbstractClassTestingTypeFilter filter = new AbstractClassTestingTypeFilter() {
				@Override
				protected boolean match(ClassMetadata metadata) {
					String cleaned = metadata.getClassName().replaceAll("\\$", ".");
					return clientClasses.contains(cleaned);
				}
			};
			scanner.addIncludeFilter(
					new AllTypeFilter(Arrays.asList(filter, annotationTypeFilter)));
		}
        // 扫描及其注册
		for (String basePackage : basePackages) {
			Set<BeanDefinition> candidateComponents = scanner
					.findCandidateComponents(basePackage);
			for (BeanDefinition candidateComponent : candidateComponents) {
				if (candidateComponent instanceof AnnotatedBeanDefinition) {
					// verify annotated class is an interface
					AnnotatedBeanDefinition beanDefinition = (AnnotatedBeanDefinition) candidateComponent;
					AnnotationMetadata annotationMetadata = beanDefinition.getMetadata();
					Assert.isTrue(annotationMetadata.isInterface(),
							"@FeignClient can only be specified on an interface");

					Map<String, Object> attributes = annotationMetadata
							.getAnnotationAttributes(
									FeignClient.class.getCanonicalName());

					String name = getClientName(attributes);
					registerClientConfiguration(registry, name,
							attributes.get("configuration"));

					registerFeignClient(registry, annotationMetadata, attributes);
				}
			}
		}
	}

	private void registerFeignClient(BeanDefinitionRegistry registry,
			AnnotationMetadata annotationMetadata, Map<String, Object> attributes) {
		String className = annotationMetadata.getClassName();
		BeanDefinitionBuilder definition = BeanDefinitionBuilder
				.genericBeanDefinition(FeignClientFactoryBean.class);
		validate(attributes);
		definition.addPropertyValue("url", getUrl(attributes));
		definition.addPropertyValue("path", getPath(attributes));
		String name = getName(attributes);
		definition.addPropertyValue("name", name);
		String contextId = getContextId(attributes);
		definition.addPropertyValue("contextId", contextId);
		definition.addPropertyValue("type", className);
		definition.addPropertyValue("decode404", attributes.get("decode404"));
		definition.addPropertyValue("fallback", attributes.get("fallback"));
		definition.addPropertyValue("fallbackFactory", attributes.get("fallbackFactory"));
		definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);

		String alias = contextId + "FeignClient";
		AbstractBeanDefinition beanDefinition = definition.getBeanDefinition();

		boolean primary = (Boolean) attributes.get("primary"); // has a default, won't be
																// null

		beanDefinition.setPrimary(primary);

		String qualifier = getQualifier(attributes);
		if (StringUtils.hasText(qualifier)) {
			alias = qualifier;
		}

		BeanDefinitionHolder holder = new BeanDefinitionHolder(beanDefinition, className,
				new String[] { alias });
		BeanDefinitionReaderUtils.registerBeanDefinition(holder, registry);
	}
```
FeignClientFactoryBean 这里类是特别注意的.  这些扫描的候选人(也就是标记@FeignClient的类)都是interface. 这些interface要想执行. 必须有实现类. 老套路, 实现一个FactoryBean<?>然后做代理. Mybatis和spring整个的套路. aop的代理实现也是这个套路. 这里也不例外. 


当上述代码执行完成后. DefaultListableBeanFactory中就注册了所有的@FeignClient标记的类. 及其Feign相关的Configuration

### feign的自动装载

![截屏2019-11-0215.28.56.png](https://ten951-img.oss-cn-shanghai.aliyuncs.com/截屏2019-11-0215.28.56.png)

#### FeignRibbonClientAutoConfiguration

```java
@ConditionalOnClass({ ILoadBalancer.class, Feign.class })
@Configuration
// 在自动配置自己之前要先配置 FeignAutoConfiguration
@AutoConfigureBefore(FeignAutoConfiguration.class)
@EnableConfigurationProperties({ FeignHttpClientProperties.class })
// Order is important here, last should be the default, first should be optional
// see
// https://github.com/spring-cloud/spring-cloud-netflix/issues/2086#issuecomment-316281653
@Import({ HttpClientFeignLoadBalancedConfiguration.class,
		OkHttpFeignLoadBalancedConfiguration.class,
		DefaultFeignLoadBalancedConfiguration.class })
public class FeignRibbonClientAutoConfiguration {

    @Bean
	@Primary
	@ConditionalOnMissingBean
    //上线文中取消 RetryTemplate时
	@ConditionalOnMissingClass("org.springframework.retry.support.RetryTemplate")
	public CachingSpringLoadBalancerFactory cachingLBClientFactory(
			SpringClientFactory factory) {
		return new CachingSpringLoadBalancerFactory(factory);
	}

	@Bean
	@Primary
	@ConditionalOnMissingBean
    // 上下文中有RetryTemplate时. 
	@ConditionalOnClass(name = "org.springframework.retry.support.RetryTemplate")
	public CachingSpringLoadBalancerFactory retryabeCachingLBClientFactory(
			SpringClientFactory factory, LoadBalancedRetryFactory retryFactory) {
		return new CachingSpringLoadBalancerFactory(factory, retryFactory);
	}

	@Bean
	@ConditionalOnMissingBean
	public Request.Options feignRequestOptions() {
		return LoadBalancerFeignClient.DEFAULT_OPTIONS;
	}

}
```
CachingSpringLoadBalancerFactory 是spring负载均衡工厂类 区别在于构建负载均衡器的时候

```java
public FeignLoadBalancer create(String clientName) {
		FeignLoadBalancer client = this.cache.get(clientName);
		if (client != null) {
			return client;
		}
		IClientConfig config = this.factory.getClientConfig(clientName);
		ILoadBalancer lb = this.factory.getLoadBalancer(clientName);
		ServerIntrospector serverIntrospector = this.factory.getInstance(clientName,
				ServerIntrospector.class);
		client = this.loadBalancedRetryFactory != null
				? new RetryableFeignLoadBalancer(lb, config, serverIntrospector,
						this.loadBalancedRetryFactory)
				: new FeignLoadBalancer(lb, config, serverIntrospector);
		this.cache.put(clientName, client);
		return client;
	}

```
RetryableFeignLoadBalancer是具有重试功能的复杂均衡. FeignLoadBalancer是默认的


##### OkHttpFeignLoadBalancedConfiguration

 就做了一件事  初始化LoadBalancerFeignClient.


#### FeignAutoConfiguration

1. 激活 feign.client开头的yml配置项和feign.httpclient开头配置项

2. 收集了用户自定义配置的FeignClientSpecification.

3. FeignContext类的初始化和FeignClientsConfiguration类的初始化

4. HystrixTargeter类的初始化

5. ConnectionPool和OkHttpClient的初始化


## feignClient的初始化阶段

### FeignClientFactoryBean类  

``class FeignClientFactoryBean
		implements FactoryBean<Object>, InitializingBean, ApplicationContextAware`` 
 

 熟悉spring ioc容器getBean方法的都知道这里的核心方法是getObject()方法

```java


protected Feign.Builder feign(FeignContext context) {
		FeignLoggerFactory loggerFactory = get(context, FeignLoggerFactory.class);
		Logger logger = loggerFactory.create(this.type);

		// @formatter:off  HystrixFeign
		Feign.Builder builder = get(context, Feign.Builder.class)
				// required values
				.logger(logger)
				.encoder(get(context, Encoder.class))
				.decoder(get(context, Decoder.class))
                // SpringMvcContract
				.contract(get(context, Contract.class));
		// @formatter:on

		configureFeign(context, builder);

		return builder;
	}

@Override
	public Object getObject() throws Exception {
		return getTarget();
	}

	/**
	 * @param <T> the target type of the Feign client
	 * @return a {@link Feign} client created with the specified data and the context
	 * information
	 */
	<T> T getTarget() {
        //获得feign上下文对象. 其实就是FeignClientsConfiguration对象
		FeignContext context = this.applicationContext.getBean(FeignContext.class);
        //构建一个feign的构建者. 
		Feign.Builder builder = feign(context);
        // url没有值的时候  使用LoadBalance
		if (!StringUtils.hasText(this.url)) {
			if (!this.name.startsWith("http")) {
				this.url = "http://" + this.name;
			}
			else {
				this.url = this.name;
			}
			this.url += cleanPath();
			return (T) loadBalance(builder, context,
					new HardCodedTarget<>(this.type, this.name, this.url));
		}
		if (StringUtils.hasText(this.url) && !this.url.startsWith("http")) {
			this.url = "http://" + this.url;
		}
        
		String url = this.url + cleanPath();
        // 拿到的Client是LoadBalancerFeignClient
		Client client = getOptional(context, Client.class);
		if (client != null) {
			if (client instanceof LoadBalancerFeignClient) {
				// not load balancing because we have a url,
				// but ribbon is on the classpath, so unwrap
                // 没有负责均衡器的原因, 使用url. 这里拿到的OkhttpClient
				client = ((LoadBalancerFeignClient) client).getDelegate();
			}
			builder.client(client);
		}
        //HystrixTargeter
		Targeter targeter = get(context, Targeter.class);
		return (T) targeter.target(this, builder, context,
				new HardCodedTarget<>(this.type, this.name, url));
	}

	private String cleanPath() {
		String path = this.path.trim();
		if (StringUtils.hasLength(path)) {
			if (!path.startsWith("/")) {
				path = "/" + path;
			}
			if (path.endsWith("/")) {
				path = path.substring(0, path.length() - 1);
			}
		}
		return path;
	}

```

HystrixTargeter#target()->build() -> newInstance(target);

#### build()方法

```java
 /** Configures components needed for hystrix integration. */
    Feign build(final FallbackFactory<?> nullableFallbackFactory) {
      super.invocationHandlerFactory(new InvocationHandlerFactory() {
        @Override
        public InvocationHandler create(Target target,
                                        Map<Method, MethodHandler> dispatch) {
          return new HystrixInvocationHandler(target, dispatch, setterFactory,
              nullableFallbackFactory);
        }
      });
      super.contract(new HystrixDelegatingContract(contract));
      return super.build();
    }
```

这里构建``new HystrixInvocationHandler(target, dispatch, setterFactory,
              nullableFallbackFactory);`` 看到这个名字. 就知道这是JDK代理的接口. 

#### newInstance() 方法

```java
 @Override
  public <T> T newInstance(Target<T> target) {
    Map<String, MethodHandler> nameToHandler = targetToHandlersByName.apply(target);
    Map<Method, MethodHandler> methodToHandler = new LinkedHashMap<Method, MethodHandler>();
    List<DefaultMethodHandler> defaultMethodHandlers = new LinkedList<DefaultMethodHandler>();

    for (Method method : target.type().getMethods()) {
      if (method.getDeclaringClass() == Object.class) {
        continue;
      } else if (Util.isDefault(method)) {
        DefaultMethodHandler handler = new DefaultMethodHandler(method);
        defaultMethodHandlers.add(handler);
        methodToHandler.put(method, handler);
      } else {
        methodToHandler.put(method, nameToHandler.get(Feign.configKey(target.type(), method)));
      }
    }
    InvocationHandler handler = factory.create(target, methodToHandler);
    T proxy = (T) Proxy.newProxyInstance(target.type().getClassLoader(),
        new Class<?>[] {target.type()}, handler);

    for (DefaultMethodHandler defaultMethodHandler : defaultMethodHandlers) {
      defaultMethodHandler.bindTo(proxy);
    }
    return proxy;
  }
```

也就是说 FeignClientFactoryBean#getObject() 生成的是代理类 

## feignClient的执行阶段

### HystrixInvocationHandler#invoke()方法

```java
 @Override
  public Object invoke(final Object proxy, final Method method, final Object[] args)
      throws Throwable {
    // early exit if the invoked method is from java.lang.Object
    // code is the same as ReflectiveFeign.FeignInvocationHandler
    if ("equals".equals(method.getName())) {
      try {
        Object otherHandler =
            args.length > 0 && args[0] != null ? Proxy.getInvocationHandler(args[0]) : null;
        return equals(otherHandler);
      } catch (IllegalArgumentException e) {
        return false;
      }
    } else if ("hashCode".equals(method.getName())) {
      return hashCode();
    } else if ("toString".equals(method.getName())) {
      return toString();
    }

    HystrixCommand<Object> hystrixCommand =
        new HystrixCommand<Object>(setterMethodMap.get(method)) {
          @Override
          protected Object run() throws Exception {
            try {
              return HystrixInvocationHandler.this.dispatch.get(method).invoke(args);
            } catch (Exception e) {
              throw e;
            } catch (Throwable t) {
              throw (Error) t;
            }
          }

          @Override
          protected Object getFallback() {
            if (fallbackFactory == null) {
              return super.getFallback();
            }
            try {
              Object fallback = fallbackFactory.create(getExecutionException());
              Object result = fallbackMethodMap.get(method).invoke(fallback, args);
              if (isReturnsHystrixCommand(method)) {
                return ((HystrixCommand) result).execute();
              } else if (isReturnsObservable(method)) {
                // Create a cold Observable
                return ((Observable) result).toBlocking().first();
              } else if (isReturnsSingle(method)) {
                // Create a cold Observable as a Single
                return ((Single) result).toObservable().toBlocking().first();
              } else if (isReturnsCompletable(method)) {
                ((Completable) result).await();
                return null;
              } else if (isReturnsCompletableFuture(method)) {
                return ((Future) result).get();
              } else {
                return result;
              }
            } catch (IllegalAccessException e) {
              // shouldn't happen as method is public due to being an interface
              throw new AssertionError(e);
            } catch (InvocationTargetException | ExecutionException e) {
              // Exceptions on fallback are tossed by Hystrix
              throw new AssertionError(e.getCause());
            } catch (InterruptedException e) {
              // Exceptions on fallback are tossed by Hystrix
              Thread.currentThread().interrupt();
              throw new AssertionError(e.getCause());
            }
          }
        };

    if (Util.isDefault(method)) {
      return hystrixCommand.execute();
    } else if (isReturnsHystrixCommand(method)) {
      return hystrixCommand;
    } else if (isReturnsObservable(method)) {
      // Create a cold Observable
      return hystrixCommand.toObservable();
    } else if (isReturnsSingle(method)) {
      // Create a cold Observable as a Single
      return hystrixCommand.toObservable().toSingle();
    } else if (isReturnsCompletable(method)) {
      return hystrixCommand.toObservable().toCompletable();
    } else if (isReturnsCompletableFuture(method)) {
      return new ObservableCompletableFuture<>(hystrixCommand);
    }
    return hystrixCommand.execute();
  }
```

其他执行RxJava的方法了. 暂时无能为力去阅读了.




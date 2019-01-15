Catalina：与开始/关闭shell脚本交互的主类，因此如果要研究启动和关闭的过程，就从这个类开始看起。

Server：是整个Tomcat组件的容器，包含一个或多个Service。 内容来自www.itxxz.com

Service：Service是包含Connector和Container的集合，Service用适当的Connector接收用户的请求，再发给相应的Container来处理。

Connector：实现某一协议的连接器，如默认的有实现HTTP、HTTPS、AJP协议的。

Container：可以理解为处理某类型请求的容器，处理的方式一般为把处理请求的处理器包装为Valve对象，并按一定顺序放入类型为Pipeline的管道里。Container有多种子类型：Engine、Host、Context和Wrapper，这几种子类型Container依次包含，处理不同粒度的请求。另外Container里包含一些基础服务，如Loader、Manager和Realm。

Engine：Engine包含Host和Context，接到请求后仍给相应的Host在相应的Context里处理，然后到wrapper。

Host：就是我们所理解的虚拟主机。

Context：就是我们所部属的具体Web应用的上下文，每个请求都在是相应的上下文里处理的。

Wrapper：Wrapper是针对每个Servlet的Container，每个Servlet都有相应的Wrapper来管理。

可以看出Server、Service、Connector、Container、Engine、Host、Context和Wrapper这些核心组件的作用范围是逐层递减，并逐层包含。

下面就是些被Container所用的基础组件：

Loader：是被Container用来载入各种所需的Class。

Manager：是被Container用来管理Session池。

Realm：是用来处理安全里授权与认证。


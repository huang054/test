/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.valves;


import java.io.IOException;

import javax.servlet.ServletException;

import org.apache.catalina.Contained;
import org.apache.catalina.Container;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Valve;
import org.apache.catalina.comet.CometEvent;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.juli.logging.Log;
import org.apache.tomcat.util.res.StringManager;


/**
 * Convenience base class for implementations of the <b>Valve</b> interface.
 * A subclass <strong>MUST</strong> implement an <code>invoke()</code>
 * method to provide the required functionality, and <strong>MAY</strong>
 * implement the <code>Lifecycle</code> interface to provide configuration
 * management and lifecycle support.
 *
 * @author Craig R. McClanahan
 */
public abstract class ValveBase extends LifecycleMBeanBase
    implements Contained, Valve {

    //------------------------------------------------------ Constructor
    // 无参构造方法，默认不支持异步
    public ValveBase() {
        this(false);
    }
    // 有参构造方法，可传入异步支持标记
    public ValveBase(boolean asyncSupported) {
        this.asyncSupported = asyncSupported;
    }

    //------------------------------------------------------ Instance Variables
    /**
     * Does this valve support Servlet 3+ async requests?
     */
    // 异步标记
    protected boolean asyncSupported;

    /**
     * The Container whose pipeline this Valve is a component of.
     */
    // 所属容器
    protected Container container = null;


    /**
     * Container log
     */
    // 容器日志组件对象
    protected Log containerLog = null;


    /**
     * The next Valve in the pipeline this Valve is a component of.
     */
    // 下一个阀门
    protected Valve next = null;


    /**
     * The string manager for this package.
     */
    // 国际化管理器，可以支持多国语言
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    //-------------------------------------------------------------- Properties


    /**
     * Return the Container with which this Valve is associated, if any.
     */
    // 获取所属容器
    @Override
    public Container getContainer() {

        return (container);

    }

    // 是否异步执行
    @Override
    public boolean isAsyncSupported() {
        return asyncSupported;
    }

    // 设置是否异步执行
    public void setAsyncSupported(boolean asyncSupported) {
        this.asyncSupported = asyncSupported;
    }


    /**
     * Set the Container with which this Valve is associated, if any.
     *
     * @param container The new associated container
     */
    // 设置所属容器
    @Override
    public void setContainer(Container container) {

        this.container = container;

    }


    /**
     * Return the next Valve in this pipeline, or <code>null</code> if this
     * is the last Valve in the pipeline.
     */
    // 获取下一个待执行的阀门
    @Override
    public Valve getNext() {

        return (next);

    }


    /**
     * Set the Valve that follows this one in the pipeline it is part of.
     *
     * @param valve The new next valve
     */
    // 设置下一个待执行的阀门
    @Override
    public void setNext(Valve valve) {

        this.next = valve;

    }


    //---------------------------------------------------------- Public Methods


    /**
     * Execute a periodic task, such as reloading, etc. This method will be
     * invoked inside the classloading context of this container. Unexpected
     * throwables will be caught and logged.
     */
    // 后台执行，子类实现
    @Override
    public void backgroundProcess() {
        // NOOP by default
    }


    /**
     * The implementation-specific logic represented by this Valve.  See the
     * Valve description for the normal design patterns for this method.
     * <p>
     * This method <strong>MUST</strong> be provided by a subclass.
     *
     * @param request The servlet request to be processed
     * @param response The servlet response to be created
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    @Override
    public abstract void invoke(Request request, Response response)
        throws IOException, ServletException;


    /**
     * Process a Comet event. This method will rarely need to be provided by
     * a subclass, unless it needs to reassociate a particular object with
     * the thread that is processing the request.
     *
     * @param request The servlet request to be processed
     * @param response The servlet response to be created
     *
     * @exception IOException if an input/output error occurs, or is thrown
     *  by a subsequently invoked Valve, Filter, or Servlet
     * @exception ServletException if a servlet error occurs, or is thrown
     *  by a subsequently invoked Valve, Filter, or Servlet
     */
    @Override
    public void event(Request request, Response response, CometEvent event)
        throws IOException, ServletException {
        // Perform the request
        getNext().event(request, response, event);
    }

    // 初始化逻辑
    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();
// 设置容器日志组件对象到当前阀门的containerLog属性
        containerLog = getContainer().getLogger();
    }


    /**
     * Start this component and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    // 启动逻辑
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        setState(LifecycleState.STARTING);
    }


    /**
     * Stop this component and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    // 停止逻辑
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        setState(LifecycleState.STOPPING);
    }


    /**
     * Return a String rendering of this object.
     */
    // 重写toString，格式为[${containerName}]
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.getClass().getName());
        sb.append('[');
        if (container == null) {
            sb.append("Container is null");
        } else {
            sb.append(container.getName());
        }
        sb.append(']');
        return sb.toString();
    }


    // -------------------- JMX and Registration  --------------------
    // 设置获取MBean对象的keyProperties，格式如：a=b,c=d,e=f...
    @Override
    public String getObjectNameKeyProperties() {
        StringBuilder name = new StringBuilder("type=Valve");

        Container container = getContainer();

        name.append(container.getMBeanKeyProperties());

        int seq = 0;

        // Pipeline may not be present in unit testing
        Pipeline p = container.getPipeline();
        if (p != null) {
            for (Valve valve : p.getValves()) {
                // Skip null valves
                if (valve == null) {
                    continue;
                }
                // Only compare valves in pipeline until we find this valve
                if (valve == this) {
                    break;
                }
                if (valve.getClass() == this.getClass()) {
                    // Duplicate valve earlier in pipeline
                    // increment sequence number
                    seq ++;
                }
            }
        }

        if (seq > 0) {
            name.append(",seq=");
            name.append(seq);
        }

        String className = this.getClass().getName();
        int period = className.lastIndexOf('.');
        if (period >= 0) {
            className = className.substring(period + 1);
        }
        name.append(",name=");
        name.append(className);

        return name.toString();
    }
    // 获取所属域，从container获取
    @Override
    public String getDomainInternal() {
        Container c = getContainer();
        if (c == null) {
            return null;
        } else {
            return c.getDomain();
        }
    }
}

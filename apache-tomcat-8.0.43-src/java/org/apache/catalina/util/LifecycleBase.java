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

package org.apache.catalina.util;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleState;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;


/**
 * Base implementation of the {@link Lifecycle} interface that implements the
 * state transition rules for {@link Lifecycle#start()} and
 * {@link Lifecycle#stop()}
 */
public abstract class LifecycleBase implements Lifecycle {

    private static final Log log = LogFactory.getLog(LifecycleBase.class);

    private static final StringManager sm =
        StringManager.getManager("org.apache.catalina.util");


    /**
     * Used to handle firing lifecycle events.
     * TODO: Consider merging LifecycleSupport into this class.
     */
    private final LifecycleSupport lifecycle = new LifecycleSupport(this);


    /**
     * The current state of the source component.
     */
    private volatile LifecycleState state = LifecycleState.NEW;


    /**
     * {@inheritDoc}
     */
    @Override
    public void addLifecycleListener(LifecycleListener listener) {
        lifecycle.addLifecycleListener(listener);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public LifecycleListener[] findLifecycleListeners() {
        return lifecycle.findLifecycleListeners();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void removeLifecycleListener(LifecycleListener listener) {
        lifecycle.removeLifecycleListener(listener);
    }


    /**
     * Allow sub classes to fire {@link Lifecycle} events.
     *
     * @param type  Event type
     * @param data  Data associated with event.
     */
    protected void fireLifecycleEvent(String type, Object data) {
        lifecycle.fireLifecycleEvent(type, data);
    }


    @Override
    public final synchronized void init() throws LifecycleException {
        // 非NEW状态，不允许调用init()方法
        if (!state.equals(LifecycleState.NEW)) {
            invalidTransition(Lifecycle.BEFORE_INIT_EVENT);
        }

        try {
            // 初始化逻辑之前，先将状态变更为`INITIALIZING`
            setStateInternal(LifecycleState.INITIALIZING, null, false);
            // 初始化，该方法为一个abstract方法，需要组件自行实现比如server  service 等等
            initInternal();
            // 初始化完成之后，状态变更为`INITIALIZED`
            setStateInternal(LifecycleState.INITIALIZED, null, false);
        } catch (Throwable t) {
            // 初始化的过程中，可能会有异常抛出，这时需要捕获异常，并将状态变更为`FAILED`
            ExceptionUtils.handleThrowable(t);
            setStateInternal(LifecycleState.FAILED, null, false);
            throw new LifecycleException(
                    sm.getString("lifecycleBase.initFail",toString()), t);
        }
    }


    protected abstract void initInternal() throws LifecycleException;

    /**
     * {@inheritDoc}
     */
    //Lifecycle的模板方法基本逻辑和stop destory一样  定义了一个抽象的方法给server service 来实现
    @Override
    public final synchronized void start() throws LifecycleException {
        // `STARTING_PREP`、`STARTING`和`STARTED时，将忽略start()逻辑
        if (LifecycleState.STARTING_PREP.equals(state) || LifecycleState.STARTING.equals(state) ||
                LifecycleState.STARTED.equals(state)) {

            if (log.isDebugEnabled()) {
                Exception e = new LifecycleException();
                log.debug(sm.getString("lifecycleBase.alreadyStarted", toString()), e);
            } else if (log.isInfoEnabled()) {
                log.info(sm.getString("lifecycleBase.alreadyStarted", toString()));
            }

            return;
        }
        // `NEW`状态时，执行init()方法
        if (state.equals(LifecycleState.NEW)) {
            init();
            // `FAILED`状态时，执行stop()方法
        } else if (state.equals(LifecycleState.FAILED)) {
            stop();
            // 不是`INITIALIZED`和`STOPPED`时，则说明是非法的操作
        } else if (!state.equals(LifecycleState.INITIALIZED) &&
                !state.equals(LifecycleState.STOPPED)) {
            invalidTransition(Lifecycle.BEFORE_START_EVENT);
        }

        try {
            // start前的状态设置
            setStateInternal(LifecycleState.STARTING_PREP, null, false);
            // start逻辑，抽象方法，由组件自行实现
            startInternal();
            // start过程中，可能因为某些原因失败，这时需要stop操作
            if (state.equals(LifecycleState.FAILED)) {
                // This is a 'controlled' failure. The component put itself into the
                // FAILED state so call stop() to complete the clean-up.
                stop();
            } else if (!state.equals(LifecycleState.STARTING)) {
                // Shouldn't be necessary but acts as a check that sub-classes are
                // doing what they are supposed to.
                invalidTransition(Lifecycle.AFTER_START_EVENT);
            } else {
                // 设置状态为STARTED
                setStateInternal(LifecycleState.STARTED, null, false);
            }
        } catch (Throwable t) {
            // This is an 'uncontrolled' failure so put the component into the
            // FAILED state and throw an exception.
            ExceptionUtils.handleThrowable(t);
            setStateInternal(LifecycleState.FAILED, null, false);
            throw new LifecycleException(sm.getString("lifecycleBase.startFail", toString()), t);
        }
    }


    /**
     * Sub-classes must ensure that the state is changed to
     * {@link LifecycleState#STARTING} during the execution of this method.
     * Changing state will trigger the {@link Lifecycle#START_EVENT} event.
     *
     * If a component fails to start it may either throw a
     * {@link LifecycleException} which will cause it's parent to fail to start
     * or it can place itself in the error state in which case {@link #stop()}
     * will be called on the failed component but the parent component will
     * continue to start normally.
     *
     * @throws LifecycleException
     */
    protected abstract void startInternal() throws LifecycleException;


    /**
     * {@inheritDoc}
     */
    @Override
    public final synchronized void stop() throws LifecycleException {

        if (LifecycleState.STOPPING_PREP.equals(state) || LifecycleState.STOPPING.equals(state) ||
                LifecycleState.STOPPED.equals(state)) {

            if (log.isDebugEnabled()) {
                Exception e = new LifecycleException();
                log.debug(sm.getString("lifecycleBase.alreadyStopped", toString()), e);
            } else if (log.isInfoEnabled()) {
                log.info(sm.getString("lifecycleBase.alreadyStopped", toString()));
            }

            return;
        }

        if (state.equals(LifecycleState.NEW)) {
            state = LifecycleState.STOPPED;
            return;
        }

        if (!state.equals(LifecycleState.STARTED) && !state.equals(LifecycleState.FAILED)) {
            invalidTransition(Lifecycle.BEFORE_STOP_EVENT);
        }

        try {
            if (state.equals(LifecycleState.FAILED)) {
                // Don't transition to STOPPING_PREP as that would briefly mark the
                // component as available but do ensure the BEFORE_STOP_EVENT is
                // fired
                fireLifecycleEvent(BEFORE_STOP_EVENT, null);
            } else {
                setStateInternal(LifecycleState.STOPPING_PREP, null, false);
            }

            stopInternal();

            // Shouldn't be necessary but acts as a check that sub-classes are
            // doing what they are supposed to.
            if (!state.equals(LifecycleState.STOPPING) && !state.equals(LifecycleState.FAILED)) {
                invalidTransition(Lifecycle.AFTER_STOP_EVENT);
            }

            setStateInternal(LifecycleState.STOPPED, null, false);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            setStateInternal(LifecycleState.FAILED, null, false);
            throw new LifecycleException(sm.getString("lifecycleBase.stopFail",toString()), t);
        } finally {
            if (this instanceof Lifecycle.SingleUse) {
                // Complete stop process first
                setStateInternal(LifecycleState.STOPPED, null, false);
                destroy();
            }
        }
    }


    /**
     * Sub-classes must ensure that the state is changed to
     * {@link LifecycleState#STOPPING} during the execution of this method.
     * Changing state will trigger the {@link Lifecycle#STOP_EVENT} event.
     *
     * @throws LifecycleException
     */
    protected abstract void stopInternal() throws LifecycleException;


    @Override
    public final synchronized void destroy() throws LifecycleException {
        if (LifecycleState.FAILED.equals(state)) {
            try {
                // Triggers clean-up
                stop();
            } catch (LifecycleException e) {
                // Just log. Still want to destroy.
                log.warn(sm.getString(
                        "lifecycleBase.destroyStopFail", toString()), e);
            }
        }

        if (LifecycleState.DESTROYING.equals(state) ||
                LifecycleState.DESTROYED.equals(state)) {

            if (log.isDebugEnabled()) {
                Exception e = new LifecycleException();
                log.debug(sm.getString("lifecycleBase.alreadyDestroyed", toString()), e);
            } else if (log.isInfoEnabled() && !(this instanceof Lifecycle.SingleUse)) {
                // Rather than have every component that might need to call
                // destroy() check for SingleUse, don't log an info message if
                // multiple calls are made to destroy()
                log.info(sm.getString("lifecycleBase.alreadyDestroyed", toString()));
            }

            return;
        }

        if (!state.equals(LifecycleState.STOPPED) &&
                !state.equals(LifecycleState.FAILED) &&
                !state.equals(LifecycleState.NEW) &&
                !state.equals(LifecycleState.INITIALIZED)) {
            invalidTransition(Lifecycle.BEFORE_DESTROY_EVENT);
        }

        try {
            setStateInternal(LifecycleState.DESTROYING, null, false);
            destroyInternal();
            setStateInternal(LifecycleState.DESTROYED, null, false);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            setStateInternal(LifecycleState.FAILED, null, false);
            throw new LifecycleException(
                    sm.getString("lifecycleBase.destroyFail",toString()), t);
        }
    }


    protected abstract void destroyInternal() throws LifecycleException;

    /**
     * {@inheritDoc}
     */
    @Override
    public LifecycleState getState() {
        return state;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getStateName() {
        return getState().toString();
    }


    /**
     * Provides a mechanism for sub-classes to update the component state.
     * Calling this method will automatically fire any associated
     * {@link Lifecycle} event. It will also check that any attempted state
     * transition is valid for a sub-class.
     *
     * @param state The new state for this component
     */
    protected synchronized void setState(LifecycleState state)
            throws LifecycleException {
        setStateInternal(state, null, true);
    }


    /**
     * Provides a mechanism for sub-classes to update the component state.
     * Calling this method will automatically fire any associated
     * {@link Lifecycle} event. It will also check that any attempted state
     * transition is valid for a sub-class.
     *
     * @param state The new state for this component
     * @param data  The data to pass to the associated {@link Lifecycle} event
     */
    protected synchronized void setState(LifecycleState state, Object data)
            throws LifecycleException {
        setStateInternal(state, data, true);
    }
//setStateInternal方法用于维护状态，同时在状态转换成功之后触发事件。为了状态的可见性，所以state声明为volatile类型的
    private synchronized void setStateInternal(LifecycleState state,
            Object data, boolean check) throws LifecycleException {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("lifecycleBase.setState", this, state));
        }
        // 是否校验状态
        if (check) {
            // Must have been triggered by one of the abstract methods (assume
            // code in this class is correct)
            // null is never a valid state
            if (state == null) {
                invalidTransition("null");
                // Unreachable code - here to stop eclipse complaining about
                // a possible NPE further down the method
                return;
            }

            // Any method can transition to failed
            // startInternal() permits STARTING_PREP to STARTING
            // stopInternal() permits STOPPING_PREP to STOPPING and FAILED to
            // STOPPING
            if (!(state == LifecycleState.FAILED ||
                    (this.state == LifecycleState.STARTING_PREP &&
                            state == LifecycleState.STARTING) ||
                    (this.state == LifecycleState.STOPPING_PREP &&
                            state == LifecycleState.STOPPING) ||
                    (this.state == LifecycleState.FAILED &&
                            state == LifecycleState.STOPPING))) {
                // No other transition permitted
                invalidTransition(state.name());
            }
        }
        // 设置状态
        this.state = state;
        // 触发事件
        String lifecycleEvent = state.getLifecycleEvent();
        if (lifecycleEvent != null) {
            fireLifecycleEvent(lifecycleEvent, data);
        }
    }

    private void invalidTransition(String type) throws LifecycleException {
        String msg = sm.getString("lifecycleBase.invalidTransition", type,
                toString(), state);
        throw new LifecycleException(msg);
    }
}

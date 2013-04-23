/**
 * Logback: the reliable, generic, fast and flexible logging framework.
 * Copyright (C) 1999-2013, QOS.ch. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 */
package ch.qos.logback.core;

import static ch.qos.logback.core.CoreConstants.CONTEXT_NAME_KEY;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.core.spi.LifeCycle;
import ch.qos.logback.core.spi.LogbackLock;
import ch.qos.logback.core.status.StatusManager;
import ch.qos.logback.core.util.EnvUtil;

public class ContextBase implements Context {

  private long birthTime = System.currentTimeMillis();

  private String name;
  private StatusManager sm = new BasicStatusManager();
  // TODO propertyMap should be observable so that we can be notified
  // when it changes so that a new instance of propertyMap can be
  // serialized. For the time being, we ignore this shortcoming.
  Map<String, String> propertyMap = new HashMap<String, String>();
  Map<String, Object> objectMap = new HashMap<String, Object>();

  LogbackLock configurationLock = new LogbackLock();

  // CORE_POOL_SIZE must be 1 for JDK 1.5. For JDK 1.6 or higher it's set to 0
  // so that there are no idle threads
  private static final int CORE_POOL_SIZE = EnvUtil.isJDK5() ? 1 : 0;

  // if you need a different MAX_POOL_SIZE, please file create a jira issue
  // asking to make MAX_POOL_SIZE a parameter.
  private static int MAX_POOL_SIZE = 32;

  // 0 (JDK 1,6+) or 1 (JDK 1.5) idle threads, MAX_POOL_SIZE maximum threads,
  // no idle waiting
  ExecutorService executorService = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE,
          0L, TimeUnit.MILLISECONDS,
          new SynchronousQueue<Runnable>());

  private final Set<LifeCycle> lifeCycleComponents = new HashSet<LifeCycle>();
  
  public StatusManager getStatusManager() {
    return sm;
  }

  /**
   * Set the {@link StatusManager} for this context. Note that by default this
   * context is initialized with a {@link BasicStatusManager}. A null value for
   * the 'statusManager' argument is not allowed.
   * <p/>
   * <p> A malicious attacker can set the status manager to a dummy instance,
   * disabling internal error reporting.
   *
   * @param statusManager the new status manager
   */
  public void setStatusManager(StatusManager statusManager) {
    // this method was added in response to http://jira.qos.ch/browse/LBCORE-35
    if (sm == null) {
      throw new IllegalArgumentException("null StatusManager not allowed");
    }
    this.sm = statusManager;
  }

  public Map<String, String> getCopyOfPropertyMap() {
    return new HashMap<String, String>(propertyMap);
  }

  public void putProperty(String key, String val) {
    this.propertyMap.put(key, val);
  }

  /**
   * Given a key, return the corresponding property value. If invoked with
   * the special key "CONTEXT_NAME", the name of the context is returned.
   *
   * @param key
   * @return
   */
  public String getProperty(String key) {
    if (CONTEXT_NAME_KEY.equals(key))
      return getName();

    return (String) this.propertyMap.get(key);
  }

  public Object getObject(String key) {
    return objectMap.get(key);
  }

  public void putObject(String key, Object value) {
    objectMap.put(key, value);
  }

  public String getName() {
    return name;
  }

  /**
   * Clear the internal objectMap and all properties.
   */
  public void reset() {
    resetLifeCycleComponents();
    propertyMap.clear();
    objectMap.clear();
  }

  /**
   * The context name can be set only if it is not already set, or if the
   * current name is the default context name, namely "default", or if the
   * current name and the old name are the same.
   *
   * @throws IllegalStateException if the context already has a name, other than "default".
   */
  public void setName(String name) throws IllegalStateException {
    if (name != null && name.equals(this.name)) {
      return; // idempotent naming
    }
    if (this.name == null
            || CoreConstants.DEFAULT_CONTEXT_NAME.equals(this.name)) {
      this.name = name;
    } else {
      throw new IllegalStateException("Context has been already given a name");
    }
  }

  public long getBirthTime() {
    return birthTime;
  }

  public Object getConfigurationLock() {
    return configurationLock;
  }

  public ExecutorService getExecutorService() {
    return  executorService;
  }

  public void addLifeCycleComponent(LifeCycle component) {
    if (!component.isStarted()) {
      component.start();
    }
    lifeCycleComponents.add(component);
  }

  private void resetLifeCycleComponents() {
    for (LifeCycle component : lifeCycleComponents) {
      if (component.isStarted()) {
        component.stop();
      }
    }
    lifeCycleComponents.clear();
  }
  
  @Override
  public String toString() {
    return name;
  }
}

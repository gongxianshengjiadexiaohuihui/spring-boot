/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.builder;

import java.lang.ref.WeakReference;

import org.springframework.beans.BeansException;
import org.springframework.boot.builder.ParentContextApplicationContextInitializer.ParentContextAvailableEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.Ordered;
import org.springframework.util.ObjectUtils;

/**
 * Listener that closes the application context if its parent is closed. It listens for
 * refresh events and grabs the current context from there, and then listens for closed
 * events and propagates it down the hierarchy.
 * 为子上下文在其父上下文创建并防止一个上下文关闭时间监听器，如果父上下文关闭，就关闭子类，并将自己的关闭事件沿着层级关系传播(触发自己子上下文的监听器)
 * @author Dave Syer
 * @author Eric Bottard
 * @since 1.0.0
 */
public class ParentContextCloserApplicationListener
		implements ApplicationListener<ParentContextAvailableEvent>, ApplicationContextAware, Ordered {

	private int order = Ordered.LOWEST_PRECEDENCE - 10;

	private ApplicationContext context;

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public void setApplicationContext(ApplicationContext context) throws BeansException {
		this.context = context;
	}

	@Override
	public void onApplicationEvent(ParentContextAvailableEvent event) {
		maybeInstallListenerInParent(event.getApplicationContext());
	}

	private void maybeInstallListenerInParent(ConfigurableApplicationContext child) {
		if (child == this.context && child.getParent() instanceof ConfigurableApplicationContext) {
			ConfigurableApplicationContext parent = (ConfigurableApplicationContext) child.getParent();
			parent.addApplicationListener(createContextCloserListener(child));
		}
	}

	/**
	 * Subclasses may override to create their own subclass of ContextCloserListener. This
	 * still enforces the use of a weak reference.
	 * @param child the child context
	 * @return the {@link ContextCloserListener} to use
	 */
	protected ContextCloserListener createContextCloserListener(ConfigurableApplicationContext child) {
		return new ContextCloserListener(child);
	}

	/**
	 * {@link ApplicationListener} to close the context.
	 * 定义一个上下文监听器，监听父级上下文关闭事件，当事件发生
	 * 上下文未被当做垃圾回收
	 * 父子关系成立
	 * 上下文处于激活状态
	 * -------关闭上下文
	 *
	 * ----为啥会用弱引用，个人推测如果子上下文被关闭了，但是父上下文没有被关闭，此时父上下文中的监听器仍持有对子上文的引用
	 */
	protected static class ContextCloserListener implements ApplicationListener<ContextClosedEvent> {

		private WeakReference<ConfigurableApplicationContext> childContext;

		public ContextCloserListener(ConfigurableApplicationContext childContext) {
			this.childContext = new WeakReference<>(childContext);
		}

		@Override
		public void onApplicationEvent(ContextClosedEvent event) {
			ConfigurableApplicationContext context = this.childContext.get();
			if ((context != null) && (event.getApplicationContext() == context.getParent()) && context.isActive()) {
				context.close();
			}
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (obj instanceof ContextCloserListener) {
				ContextCloserListener other = (ContextCloserListener) obj;
				return ObjectUtils.nullSafeEquals(this.childContext.get(), other.childContext.get());
			}
			return super.equals(obj);
		}

		@Override
		public int hashCode() {
			return ObjectUtils.nullSafeHashCode(this.childContext.get());
		}

	}

}

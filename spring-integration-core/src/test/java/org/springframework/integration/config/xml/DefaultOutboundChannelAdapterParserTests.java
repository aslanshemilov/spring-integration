/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.config.xml;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.TestConsumer;
import org.springframework.integration.handler.MethodInvokingMessageHandler;
import org.springframework.integration.handler.advice.RequestHandlerRetryAdvice;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Mark Fisher
 * @author Artem Bilan
 * @author Gary Russell
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@DirtiesContext
public class DefaultOutboundChannelAdapterParserTests {

	@Autowired
	private ApplicationContext context;

	@SuppressWarnings("unused") // testing auto wiring only
	@Autowired
	@Qualifier("org.springframework.integration.handler.MethodInvokingMessageHandler#0")
	private MethodInvokingMessageHandler adapterByGeneratedName;

	@SuppressWarnings("unused") // testing auto wiring only
	@Autowired
	@Qualifier("adapter.handler")
	private MethodInvokingMessageHandler adapterByAlias;

	@Test
	public void checkConfig() {
		Object adapter = context.getBean("adapter");
		assertEquals(Boolean.FALSE, TestUtils.getPropertyValue(adapter, "autoStartup"));
		Object handler = TestUtils.getPropertyValue(adapter, "handler");
		assertEquals(MethodInvokingMessageHandler.class, handler.getClass());
		assertEquals(99, TestUtils.getPropertyValue(handler, "order"));
	}

	@Test
	public void checkConfigWithInnerBeanAndPoller() {
		Object adapter = context.getBean("adapterB");
		assertEquals(Boolean.FALSE, TestUtils.getPropertyValue(adapter, "autoStartup"));
		MessageHandler handler = TestUtils.getPropertyValue(adapter, "handler", MessageHandler.class);
		assertTrue(AopUtils.isAopProxy(handler));
		assertThat(TestUtils.getPropertyValue(handler, "h.advised.advisors[0].advice"),
				Matchers.instanceOf(RequestHandlerRetryAdvice.class));

		handler.handleMessage(new GenericMessage<>("foo"));
		QueueChannel recovery = context.getBean("recovery", QueueChannel.class);
		Message<?> received = recovery.receive(10000);
		assertNotNull(received);
		assertThat(received, instanceOf(ErrorMessage.class));
		assertThat(received.getPayload(), instanceOf(MessagingException.class));
		assertEquals("foo", ((MessagingException) received.getPayload()).getFailedMessage().getPayload());
	}

	@Test
	public void checkConfigWithInnerMessageHandler() {
		Object adapter = context.getBean("adapterC");
		Object handler = TestUtils.getPropertyValue(adapter, "handler");
		assertEquals(MethodInvokingMessageHandler.class, handler.getClass());
		assertEquals(99, TestUtils.getPropertyValue(handler, "order"));
		Object targetObject = TestUtils.getPropertyValue(handler, "processor.delegate.targetObject");
		assertEquals(TestConsumer.class, targetObject.getClass());
	}


	static class TestBean {

		public void out(Object o) {
			throw new RuntimeException("ex");
		}

	}

}

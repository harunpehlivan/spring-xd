/*
 * Copyright 2013-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.springframework.xd.dirt.integration.bus;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.http.MediaType;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.ConsumerEndpointFactoryBean;
import org.springframework.integration.handler.BridgeHandler;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.integration.support.context.NamedComponent;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.util.Assert;

/**
 * A simple implementation of {@link MessageBus} for in-process use. For inbound and outbound, creates a
 * {@link DirectChannel} or a {@link QueueChannel} depending on whether the binding is aliased or not then bridges the
 * passed {@link MessageChannel} to the channel which is registered in the given application context. If that channel
 * does not yet exist, it will be created.
 *
 * @author David Turanski
 * @author Mark Fisher
 * @author Gary Russell
 * @author Jennifer Hickey
 * @author Ilayaperumal Gopinathan
 * @since 1.0
 */
public class LocalMessageBus extends MessageBusSupport {

	private PollerMetadata poller;

	private final Map<String, ExecutorChannel> requestReplyChannels = new HashMap<String, ExecutorChannel>();

	private final ExecutorService executor = Executors.newCachedThreadPool();

	@SuppressWarnings("unused")
	private boolean hasCodec;

	/**
	 * Set the poller to use when QueueChannels are used.
	 */
	public void setPoller(PollerMetadata poller) {
		this.poller = poller;
	}

	/**
	 * For the local bus we bridge the router "output" channel to a queue channel.
	 * {@inheritDoc}
	 */
	@Override
	public MessageChannel bindDynamicProducer(String name) {
		MessageChannel channel = this.directChannelProvider.createSharedChannel("dynamic.output.to." + name);
		bindProducer(name, channel, null); // TODO: dynamic producer options
		return channel;
	}

	/**
	 * For the local bus we bridge the router "output" channel to a queue channel.
	 * {@inheritDoc}
	 */
	@Override
	public MessageChannel bindDynamicPubSubProducer(String name) {
		MessageChannel channel = this.directChannelProvider.createAndRegisterChannel("dynamic.output.to." + name);
		bindPubSubProducer(name, channel, null); // TODO: dynamic producer options
		return channel;
	}

	private SharedChannelProvider<?> getChannelProvider(String name) {
		SharedChannelProvider<?> channelProvider = directChannelProvider;
		// Use queue channel provider in case of named channels:
		// point-to-point type syntax (queue:) and job input channel syntax (job:)
		if (name.startsWith(P2P_NAMED_CHANNEL_TYPE_PREFIX) || name.startsWith(JOB_CHANNEL_TYPE_PREFIX)) {
			channelProvider = queueChannelProvider;
		}
		return channelProvider;
	}

	/**
	 * Looks up or creates a DirectChannel with the given name and creates a bridge from that channel to the provided
	 * channel instance.
	 */
	@Override
	public void bindConsumer(String name, MessageChannel moduleInputChannel, Properties properties) {
		doRegisterConsumer(name, moduleInputChannel, getChannelProvider(name));
	}

	@Override
	public void bindPubSubConsumer(String name, MessageChannel moduleInputChannel, Properties properties) {
		doRegisterConsumer(name, moduleInputChannel, pubsubChannelProvider);
	}

	private void doRegisterConsumer(String name, MessageChannel moduleInputChannel,
			SharedChannelProvider<?> channelProvider) {
		Assert.hasText(name, "a valid name is required to register an inbound channel");
		Assert.notNull(moduleInputChannel, "channel must not be null");
		MessageChannel registeredChannel = channelProvider.lookupOrCreateSharedChannel(name);
		bridge(registeredChannel, moduleInputChannel,
				"inbound." + ((NamedComponent) registeredChannel).getComponentName());
	}

	/**
	 * Looks up or creates a DirectChannel with the given name and creates a bridge to that channel from the provided
	 * channel instance.
	 */
	@Override
	public void bindProducer(String name, MessageChannel moduleOutputChannel, Properties properties) {
		doRegisterProducer(name, moduleOutputChannel, getChannelProvider(name));
	}

	@Override
	public void bindPubSubProducer(String name, MessageChannel moduleOutputChannel,
			Properties properties) {
		doRegisterProducer(name, moduleOutputChannel, pubsubChannelProvider);
	}

	private void doRegisterProducer(String name, MessageChannel moduleOutputChannel,
			SharedChannelProvider<?> channelProvider) {
		Assert.hasText(name, "a valid name is required to register an outbound channel");
		Assert.notNull(moduleOutputChannel, "channel must not be null");
		MessageChannel registeredChannel = channelProvider.lookupOrCreateSharedChannel(name);
		bridge(moduleOutputChannel, registeredChannel,
				"outbound." + ((NamedComponent) registeredChannel).getComponentName());
	}

	@Override
	public void bindRequestor(final String name, MessageChannel requests, final MessageChannel replies,
			Properties properties) {
		final MessageChannel requestChannel = this.findOrCreateRequestReplyChannel("requestor." + name);
		// TODO: handle Pollable ?
		Assert.isInstanceOf(SubscribableChannel.class, requests);
		((SubscribableChannel) requests).subscribe(new MessageHandler() {

			@Override
			public void handleMessage(Message<?> message) throws MessagingException {
				requestChannel.send(message);
			}
		});

		ExecutorChannel replyChannel = this.findOrCreateRequestReplyChannel("replier." + name);
		replyChannel.subscribe(new MessageHandler() {

			@Override
			public void handleMessage(Message<?> message) throws MessagingException {
				replies.send(message);
			}
		});
	}

	@Override
	public void bindReplier(String name, final MessageChannel requests, MessageChannel replies,
			Properties properties) {
		SubscribableChannel requestChannel = this.findOrCreateRequestReplyChannel("requestor." + name);
		requestChannel.subscribe(new MessageHandler() {

			@Override
			public void handleMessage(Message<?> message) throws MessagingException {
				requests.send(message);
			}
		});

		// TODO: handle Pollable ?
		Assert.isInstanceOf(SubscribableChannel.class, replies);
		final SubscribableChannel replyChannel = this.findOrCreateRequestReplyChannel("replier." + name);
		((SubscribableChannel) replies).subscribe(new MessageHandler() {

			@Override
			public void handleMessage(Message<?> message) throws MessagingException {
				replyChannel.send(message);
			}
		});
	}

	private synchronized ExecutorChannel findOrCreateRequestReplyChannel(String name) {
		ExecutorChannel channel = this.requestReplyChannels.get(name);
		if (channel == null) {
			channel = new ExecutorChannel(this.executor);
			channel.setBeanFactory(getBeanFactory());
			this.requestReplyChannels.put(name, channel);
		}
		return channel;
	}

	@Override
	public void unbindProducer(String name, MessageChannel channel) {
		this.requestReplyChannels.remove("replier." + name);
		MessageChannel requestChannel = this.requestReplyChannels.remove("requestor." + name);
		if (requestChannel == null) {
			super.unbindProducer(name, channel);
		}
	}

	protected BridgeHandler bridge(MessageChannel from, MessageChannel to, String bridgeName) {
		return bridge(from, to, bridgeName, null);
	}


	protected BridgeHandler bridge(MessageChannel from, MessageChannel to, String bridgeName,
			final Collection<MediaType> acceptedMediaTypes) {

		final boolean isInbound = bridgeName.startsWith("inbound.");

		BridgeHandler handler = new BridgeHandler() {

			@Override
			protected Object handleRequestMessage(Message<?> requestMessage) {
				return requestMessage;
			}

		};

		handler.setBeanFactory(getBeanFactory());
		handler.setOutputChannel(to);
		handler.setBeanName(bridgeName);
		handler.afterPropertiesSet();

		// Usage of a CEFB allows to handle both Subscribable & Pollable channels the same way
		ConsumerEndpointFactoryBean cefb = new ConsumerEndpointFactoryBean();
		cefb.setInputChannel(from);
		cefb.setHandler(handler);
		cefb.setBeanFactory(getBeanFactory());
		if (from instanceof PollableChannel) {
			cefb.setPollerMetadata(poller);
		}
		try {
			cefb.afterPropertiesSet();
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}

		try {
			cefb.getObject().setComponentName(handler.getComponentName());
			Binding binding = isInbound ? Binding.forConsumer(cefb.getObject(), to)
					: Binding.forProducer(from, cefb.getObject());
			addBinding(binding);
			binding.start();
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
		return handler;
	}

	protected <T> T getBean(String name, Class<T> requiredType) {
		return getApplicationContext().getBean(name, requiredType);
	}

}

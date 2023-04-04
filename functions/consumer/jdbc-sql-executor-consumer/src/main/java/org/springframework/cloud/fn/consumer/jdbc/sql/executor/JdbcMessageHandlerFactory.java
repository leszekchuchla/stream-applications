/*
 * Copyright 2020-2021 the original author or authors.
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

package org.springframework.cloud.fn.consumer.jdbc.sql.executor;

import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.sql.DataSource;

import org.springframework.integration.jdbc.JdbcMessageHandler;
import org.springframework.integration.support.MutableMessage;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.MimeTypeUtils;

public final class JdbcMessageHandlerFactory {
	private JdbcMessageHandlerFactory() {

	}

	public static JdbcMessageHandler jdbcMessageHandler(DataSource dataSource, String sql) {
		JdbcMessageHandler jdbcMessageHandler =  new JdbcMessageHandler(dataSource, sql) {

			@Override
			protected void handleMessageInternal(final Message<?> message) {
				Message<?> convertedMessage = message;
				if (message.getPayload() instanceof byte[] || message.getPayload() instanceof Iterable) {

					final String contentType = message.getHeaders().containsKey(MessageHeaders.CONTENT_TYPE)
							? message.getHeaders().get(MessageHeaders.CONTENT_TYPE).toString()
							: MimeTypeUtils.APPLICATION_JSON_VALUE;
					if (message.getPayload() instanceof Iterable) {
						Stream<Object> messageStream =
								StreamSupport.stream(((Iterable<?>) message.getPayload()).spliterator(), false)
															.map(payload -> {
															if (payload instanceof byte[]) {
																return convertibleContentType(contentType) ? new String(((byte[]) payload)) : payload;
															}
															else {
																return payload;
															}
														});
						convertedMessage = new MutableMessage<>(messageStream.collect(Collectors.toList()),
																										message.getHeaders());
					}
					else {
						if (convertibleContentType(contentType)) {
							convertedMessage = new MutableMessage<>(new String(((byte[]) message.getPayload())),
																											message.getHeaders());
						}
					}
				}
				super.handleMessageInternal(convertedMessage);
			}
		};

		jdbcMessageHandler.setSqlParameterSourceFactory(input -> new EmptySqlParameterSource());

		return jdbcMessageHandler;
	}

	private static boolean convertibleContentType(String contentType) {
		return contentType.contains("text") || contentType.contains("json") || contentType.contains("x-spring-tuple");
	}

}

/*
 * Copyright 2020-2020 the original author or authors.
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

package org.springframework.cloud.fn.consumer.jdbc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.integration.support.MutableMessage;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;

import static java.util.Objects.isNull;
import static org.springframework.cloud.fn.consumer.jdbc.JdbcMessageHandlerFactory.jdbcMessageHandler;

public class MultiSchemaAwareJdbcMessageHandler implements MessageHandler {

	private static final Log logger = LogFactory.getLog(MultiSchemaAwareJdbcMessageHandler.class);

	private final Map<String, MessageHandler> handlers = new ConcurrentHashMap<>();

	private final JdbcConsumerProperties properties;
	private final SpelExpressionParser spelExpressionParser;
	private final EvaluationContext evaluationContext;
	private final DataSource dataSource;

	public MultiSchemaAwareJdbcMessageHandler(JdbcConsumerProperties properties,
																						SpelExpressionParser spelExpressionParser,
																						EvaluationContext evaluationContext,
																						DataSource dataSource) {
		this.properties = properties;
		this.spelExpressionParser = spelExpressionParser;
		this.evaluationContext = evaluationContext;
		this.dataSource = dataSource;
	}

	@Override
	public void handleMessage(Message<?> message) throws MessagingException {
		if (isNull(properties.getSchemaNameExpression()) || properties.getSchemaNameExpression().length() == 0) {
			throw new IllegalStateException("Schema name expression must be given");
		}

		try {
			var convertedMessage = new MutableMessage<>(new String(((byte[]) message.getPayload())), message.getHeaders());
			var schemaName = spelExpressionParser.parseExpression(properties.getSchemaNameExpression()).getValue(evaluationContext, convertedMessage, String.class);

			if (isNull(schemaName)) {
				throw new IllegalArgumentException("Cannot evaluate schema name, %s".formatted(properties.getSchemaNameExpression()));
			}

			handlers.computeIfAbsent(schemaName, key -> jdbcMessageHandler(spelExpressionParser, evaluationContext, properties, dataSource, schemaName))
							.handleMessage(message);
		}
		catch (Exception e) {
			logger.error("Error while processing message", e);
		}
	}

}

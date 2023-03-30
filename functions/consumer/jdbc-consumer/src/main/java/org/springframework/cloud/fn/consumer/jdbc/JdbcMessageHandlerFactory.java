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

package org.springframework.cloud.fn.consumer.jdbc;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.SpelParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.integration.jdbc.JdbcMessageHandler;
import org.springframework.integration.jdbc.SqlParameterSourceFactory;
import org.springframework.integration.support.MutableMessage;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.MultiValueMap;

import static java.util.Objects.nonNull;

public final class JdbcMessageHandlerFactory {

	private static final Log logger = LogFactory.getLog(JdbcMessageHandlerFactory.class);
	private static final Object NOT_SET = new Object();

	private JdbcMessageHandlerFactory() {

	}

	public static JdbcMessageHandler jdbcMessageHandler(SpelExpressionParser spelExpressionParser,
																											EvaluationContext evaluationContext,
																											JdbcConsumerProperties properties,
																											DataSource dataSource,
																											String schemaName) {
		final MultiValueMap<String, Expression> columnExpressionVariations = new LinkedMultiValueMap<>();
		for (Map.Entry<String, String> entry : properties.getColumnsMap().entrySet()) {
			String value = entry.getValue();
			columnExpressionVariations.add(entry.getKey(), spelExpressionParser.parseExpression(value));
			if (!value.startsWith("payload")) {
				String qualified = "payload." + value;
				try {
					columnExpressionVariations.add(entry.getKey(), spelExpressionParser.parseExpression(qualified));
				}
				catch (SpelParseException e) {
					logger.info("failed to parse qualified fallback expression " + qualified +
													"; be sure your expression uses the 'payload.' prefix where necessary");
				}
			}
		}
		JdbcMessageHandler jdbcMessageHandler = new JdbcMessageHandler(dataSource, generateSql(schemaName, properties.getTableName(), columnExpressionVariations.keySet())) {

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
		SqlParameterSourceFactory parameterSourceFactory = new ParameterFactory(columnExpressionVariations, evaluationContext);
		jdbcMessageHandler.setSqlParameterSourceFactory(parameterSourceFactory);
		return jdbcMessageHandler;
	}

	private static boolean convertibleContentType(String contentType) {
		return contentType.contains("text") || contentType.contains("json") || contentType.contains("x-spring-tuple");
	}

	private static String generateSql(String schemaNameExpression, String tableName, Set<String> columns) {
		StringBuilder builder = new StringBuilder("INSERT INTO ");
		StringBuilder questionMarks = new StringBuilder(") VALUES (");

		if (nonNull(schemaNameExpression) && schemaNameExpression.length() > 0) {
			builder.append(schemaNameExpression).append(".");
		}

		builder.append(tableName).append("(");

		int i = 0;

		for (String column : columns) {
			if (i++ > 0) {
				builder.append(", ");
				questionMarks.append(", ");
			}
			builder.append(column);
			questionMarks.append(':').append(column);
		}
		builder.append(questionMarks).append(")");
		String sql = builder.toString();

		logger.info("Prepared sql: " + sql);

		return sql;
	}


	private static final class ParameterFactory implements SqlParameterSourceFactory {

		private final MultiValueMap<String, Expression> columnExpressions;

		private final EvaluationContext context;

		ParameterFactory(MultiValueMap<String, Expression> columnExpressions, EvaluationContext context) {
			this.columnExpressions = columnExpressions;
			this.context = context;
		}

		@Override
		public SqlParameterSource createParameterSource(Object o) {
			if (!(o instanceof Message)) {
				throw new IllegalArgumentException("Unable to handle type %s".formatted(o.getClass().getName()));
			}
			Message<?> message = (Message<?>) o;
			MapSqlParameterSource parameterSource = new MapSqlParameterSource();
			for (Map.Entry<String, List<Expression>> entry : columnExpressions.entrySet()) {
				String key = entry.getKey();
				List<Expression> spels = entry.getValue();
				Object value = NOT_SET;
				EvaluationException lastException = null;
				for (Expression spel : spels) {
					try {
						value = spel.getValue(context, message);
						break;
					}
					catch (EvaluationException e) {
						lastException = e;
					}
				}
				if (value == NOT_SET) {
					if (lastException != null) {
						logger.info("Could not find value for column '%s': %s".formatted(key, lastException.getMessage()));
					}
					parameterSource.addValue(key, null);
				}
				else {
					parameterSource.addValue(key, value);
				}
			}
			return parameterSource;
		}

	}

}

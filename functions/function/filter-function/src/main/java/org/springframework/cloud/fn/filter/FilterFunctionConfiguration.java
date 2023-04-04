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

package org.springframework.cloud.fn.filter;

import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.integration.handler.ExpressionEvaluatingMessageProcessor;
import org.springframework.integration.json.JsonPropertyAccessor;
import org.springframework.integration.support.MutableMessage;
import org.springframework.integration.transformer.Transformer;
import org.springframework.messaging.Message;

/**
 * @author Artem Bilan
 * @author David Turanski
 */
@AutoConfiguration
@EnableConfigurationProperties(FilterFunctionProperties.class)
public class FilterFunctionConfiguration {

	private static final Log logger = LogFactory.getLog(FilterFunctionConfiguration.class);

	@Bean
	public Function<Message<?>, Message<?>> filterFunction(Transformer transformer) {
		return message -> {

			try {
					var convertedMessage = message.getPayload() instanceof byte[]
							? new MutableMessage<>(new String(((byte[]) message.getPayload())), message.getHeaders())
							: message;

				if ((Boolean) transformer.transform(convertedMessage).getPayload()) {
					return message;
				}
				else {
					return null;
				}
			}
			catch (Exception e) {
				logger.error("ERROR: ", e);
				throw e;
			}
		};
	}

	@Bean
	public Transformer filterExpressionEvaluatingTransformer(FilterFunctionProperties filterFunctionProperties) {
		return new CustomExpressionEvaluatingTransformer(new ExpressionEvaluatingMessageProcessor<>(filterFunctionProperties.getExpression()) {
			@Override
			protected StandardEvaluationContext getEvaluationContext() {
				var evaluationContext = super.getEvaluationContext();
				evaluationContext.addPropertyAccessor(new JsonPropertyAccessor());
				return evaluationContext;
			}
		}
		);
	}

}

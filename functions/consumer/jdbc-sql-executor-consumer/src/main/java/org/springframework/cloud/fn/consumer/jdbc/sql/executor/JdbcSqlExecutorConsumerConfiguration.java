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

import java.util.function.Consumer;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.integration.aggregator.DefaultAggregatingMessageGroupProcessor;
import org.springframework.integration.aggregator.MessageCountReleaseStrategy;
import org.springframework.integration.config.AggregatorFactoryBean;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlowBuilder;
import org.springframework.integration.expression.ExpressionUtils;
import org.springframework.integration.expression.ValueExpression;
import org.springframework.integration.json.JsonPropertyAccessor;
import org.springframework.integration.store.MessageGroupStore;
import org.springframework.integration.store.SimpleMessageStore;
import org.springframework.messaging.MessageHandler;

@Configuration
@EnableConfigurationProperties(JdbcSqlExecutorConsumerProperties.class)
public class JdbcSqlExecutorConsumerConfiguration {

	private static final Log logger = LogFactory.getLog(JdbcSqlExecutorConsumerConfiguration.class);

	private final JdbcSqlExecutorConsumerProperties properties;

	private SpelExpressionParser spelExpressionParser = new SpelExpressionParser();
	private EvaluationContext evaluationContext;

	public JdbcSqlExecutorConsumerConfiguration(JdbcSqlExecutorConsumerProperties properties, BeanFactory beanFactory) {
		this.properties = properties;
		this.evaluationContext = ExpressionUtils.createStandardEvaluationContext(beanFactory);
		StandardEvaluationContext standardEvaluationContext = (StandardEvaluationContext) this.evaluationContext;
		standardEvaluationContext.addPropertyAccessor(new JsonPropertyAccessor());
	}

	@Bean
	IntegrationFlow jdbcSqlExecutorConsumerFlow(@Qualifier("aggregator") MessageHandler aggregator, DataSource dataSource) {
		final IntegrationFlowBuilder builder = IntegrationFlow.from(Consumer.class, gateway -> gateway.beanName("jdbc-sql-executorConsumer"));
		if (properties.getBatchSize() > 1 || properties.getIdleTimeout() > 0) {
			builder.handle(aggregator);
		}
		return builder.handle(new DynamicJdbcMessageHandler(properties, spelExpressionParser, evaluationContext, dataSource)).get();
	}

	@Bean
	FactoryBean<MessageHandler> aggregator(MessageGroupStore messageGroupStore) {
		AggregatorFactoryBean aggregatorFactoryBean = new AggregatorFactoryBean();
		aggregatorFactoryBean.setCorrelationStrategy(message -> message.getPayload().getClass().getName());
		aggregatorFactoryBean.setReleaseStrategy(new MessageCountReleaseStrategy(this.properties.getBatchSize()));
		if (this.properties.getIdleTimeout() >= 0) {
			aggregatorFactoryBean.setGroupTimeoutExpression(new ValueExpression<>(this.properties.getIdleTimeout()));
		}
		aggregatorFactoryBean.setMessageStore(messageGroupStore);
		aggregatorFactoryBean.setProcessorBean(new DefaultAggregatingMessageGroupProcessor());
		aggregatorFactoryBean.setExpireGroupsUponCompletion(true);
		aggregatorFactoryBean.setSendPartialResultOnExpiry(true);
		return aggregatorFactoryBean;
	}

	@Bean
	MessageGroupStore messageGroupStore() {
		SimpleMessageStore messageGroupStore = new SimpleMessageStore();
		messageGroupStore.setTimeoutOnIdle(true);
		messageGroupStore.setCopyOnGet(false);
		return messageGroupStore;
	}

}

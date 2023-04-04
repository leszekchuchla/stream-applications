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

package org.springframework.cloud.fn.consumer.jdbc.sql.executor;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jdbc.sql.executor.consumer")
public class JdbcSqlExecutorConsumerProperties {

	/**
	 * The name of the schema to write into.
	 */
	private String sqlExpression;

	/**
	 * Threshold in number of messages when data will be flushed to database table.
	 */
	private int batchSize = 1;

	/**
	 * Idle timeout in milliseconds when data is automatically flushed to database table.
	 */
	private long idleTimeout = -1L;

	public String getSqlExpression() {
		return sqlExpression;
	}

	public void setSqlExpression(String sqlExpression) {
		this.sqlExpression = sqlExpression;
	}

	public int getBatchSize() {
		return this.batchSize;
	}

	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}

	public long getIdleTimeout() {
		return this.idleTimeout;
	}

	public void setIdleTimeout(long idleTimeout) {
		this.idleTimeout = idleTimeout;
	}

}

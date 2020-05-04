/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.cloud.fn.consumer.cassandra.query;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

/**
 * @author Akos Ratku
 * @author Artem Bilan
 */
public class InsertQueryColumnNameExtractor implements ColumnNameExtractor {

	private static final Pattern PATTERN = Pattern.compile(".+\\((.+)\\).+(?:values\\s*\\((.+)\\))");

	@Override
	public List<String> extract(String query) {
		List<String> extractedColumns = new LinkedList<>();
		Matcher matcher = PATTERN.matcher(query);
		if (matcher.matches()) {
			String[] columns = StringUtils.delimitedListToStringArray(matcher.group(1), ",", " ");
			String[] params = StringUtils.delimitedListToStringArray(matcher.group(2), ",", " ");
			for (int i = 0; i < columns.length; i++) {
				String param = params[i];
				if (param.equals("?")) {
					extractedColumns.add(columns[i]);
				}
				else if (param.startsWith(":")) {
					extractedColumns.add(param.substring(1));
				}
			}
		}
		else {
			throw new IllegalArgumentException("Invalid CQL insert query syntax: " + query);
		}
		return extractedColumns;
	}

}

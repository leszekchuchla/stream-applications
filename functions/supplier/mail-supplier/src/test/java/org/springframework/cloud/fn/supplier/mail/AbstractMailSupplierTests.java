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

package org.springframework.cloud.fn.supplier.mail;

import java.util.function.Supplier;

import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import reactor.core.publisher.Flux;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.integration.dsl.StandardIntegrationFlow;
import org.springframework.integration.test.context.SpringIntegrationTest;
import org.springframework.messaging.Message;
import org.springframework.test.annotation.DirtiesContext;

@SpringIntegrationTest(noAutoStartup = "*")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
	"mail.supplier.mark-as-read=true",
	"mail.supplier.delete=false",
	"mail.supplier.user-flag=testSIUserFlag",
	"mail.supplier.java-mail-properties=mail.imap.socketFactory.fallback=true\\n mail.store.protocol=imap\\n mail.debug=true"})
@DirtiesContext
public abstract class AbstractMailSupplierTests {
	protected static GreenMail mailServer;

	protected static GreenMailUser mailUser;

	@Autowired
	protected Supplier<Flux<Message<?>>> mailSupplier;
	@Autowired
	protected StandardIntegrationFlow integrationFlow;
	protected void sendMessage(String subject, String body) {
		mailUser.deliver(GreenMailUtil.createTextEmail("bar@bax", "test@test", subject, body, mailServer.getSmtp().getServerSetup()));
	}

	@BeforeAll
	public static void setup() {
		ServerSetup imap = ServerSetupTest.IMAP.dynamicPort();
		imap.setServerStartupTimeout(10000);
		ServerSetup pop3 = ServerSetupTest.POP3.dynamicPort();
		pop3.setServerStartupTimeout(10000);
		ServerSetup smtp = ServerSetupTest.SMTP.dynamicPort();
		smtp.setServerStartupTimeout(10000);

		mailServer = new GreenMail(new ServerSetup[]{imap, pop3, smtp});
		mailUser = mailServer.setUser("user", "pw");
		mailServer.start();
		String imapPort = Integer.toString(mailServer.getImap().getServerSetup().getPort());
		String pop3Port = Integer.toString(mailServer.getPop3().getServerSetup().getPort());
		String smtpPort = Integer.toString(mailServer.getPop3().getServerSetup().getPort());
		System.setProperty("test.mail.server.imap.port", imapPort);
		System.setProperty("test.mail.server.pop3.port", pop3Port);
		System.setProperty("test.mail.server.smtp.port", smtpPort);
	}

	@BeforeEach
	void start() {
		integrationFlow.start();
	}

	@AfterEach
	void stop() {
		integrationFlow.stop();
	}

	@AfterAll
	public static void cleanup() {
		System.clearProperty("test.mail.server.port");
	}

	@SpringBootApplication
	public static class MailSupplierTestApplication {

	}
}

/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.microsoft.eventhubs.client;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.apache.qpid.amqp_1_0.client.LinkDetachedException;
import org.apache.qpid.amqp_1_0.client.Message;
import org.apache.qpid.amqp_1_0.client.Sender;
import org.apache.qpid.amqp_1_0.client.Session;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.messaging.ApplicationProperties;
import org.apache.qpid.amqp_1_0.type.messaging.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventHubSender {

	private static final Logger logger = LoggerFactory.getLogger(EventHubSender.class);

	private final Session session;
	private final String entityPath;
	private final String partitionId;
	private final String destinationAddress;

	private Sender sender;

	public EventHubSender(Session session, String entityPath, String partitionId) {
		this.session = session;
		this.entityPath = entityPath;
		this.partitionId = partitionId;
		this.destinationAddress = getDestinationAddress();
	}

	public void send(Section section) throws EventHubException {
		send(Collections.singletonList(section));
	}

	public void send(Collection<Section> sections) throws EventHubException {
		try {
			if (sender == null) {
				ensureSenderCreated();
			}

			Message message = new Message(sections);
			sender.send(message);

		} catch (LinkDetachedException e) {
			logger.error(e.getMessage());

			EventHubException eventHubException = new EventHubException("Sender has been closed");
			throw eventHubException;
		} catch (TimeoutException e) {
			logger.error(e.getMessage());

			EventHubException eventHubException = new EventHubException("Timed out while waiting to get credit to send");
			throw eventHubException;
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
	}

	public void send(byte[] data, Map<String, Object> properties) throws EventHubException {
		Binary bin = new Binary(data);
		Data dataSection = new Data(bin);

		if (properties == null || properties.size() == 0)
			send(dataSection);
		else {
			ApplicationProperties applicationProperties = new ApplicationProperties(properties);
			send(Arrays.asList(dataSection, applicationProperties));
		}
	}

	public void send(String data, Map<String, Object> properties) throws EventHubException {
		// For interop with other language, convert string to bytes
		send(data.getBytes(), properties);
	}

	public void close() {
		try {
			sender.close();
		} catch (Sender.SenderClosingException e) {
			logger.error("Closing a sender encountered error: " + e.getMessage());
		}
	}

	private String getDestinationAddress() {
		if (partitionId == null || partitionId.equals("")) {
			return entityPath;
		} else {
			return String.format(Constants.DestinationAddressFormatString, entityPath, partitionId);
		}
	}

	private synchronized void ensureSenderCreated() throws Exception {
		if (sender == null) {
			sender = session.createSender(destinationAddress);
		}
	}
}

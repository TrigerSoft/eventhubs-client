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

import java.util.Collections;
import java.util.Map;

import org.apache.qpid.amqp_1_0.client.AcknowledgeMode;
import org.apache.qpid.amqp_1_0.client.ConnectionErrorException;
import org.apache.qpid.amqp_1_0.client.Message;
import org.apache.qpid.amqp_1_0.client.Receiver;
import org.apache.qpid.amqp_1_0.client.Session;
import org.apache.qpid.amqp_1_0.type.Symbol;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.apache.qpid.amqp_1_0.type.messaging.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EventHubReceiver {

	private static final Logger logger = LoggerFactory.getLogger(EventHubReceiver.class);

	private final Session session;
	private final String entityPath;
	private final String consumerGroupName;
	private final String partitionId;
	private final String consumerAddress;
	private final Map<Symbol, Filter> filters;
	private final Map<Symbol, ?> properties;
	private final int defaultCredits;

	private Receiver receiver;
	private boolean isClosed;

	public EventHubReceiver(Session session, String entityPath, String consumerGroupName, String partitionId,
			String filterStr, int defaultCredits, Long epoch) throws EventHubException {

		this.session = session;
		this.entityPath = entityPath;
		this.consumerGroupName = consumerGroupName;
		this.partitionId = partitionId;
		this.consumerAddress = getConsumerAddress();
		this.filters = Collections.singletonMap(Symbol.valueOf(Constants.SelectorFilterName),
				(Filter) new SelectorFilter(filterStr));
		properties = epoch != null ? Collections.singletonMap(Symbol.valueOf(Constants.AttachEpoch), epoch) : null;
		logger.info("receiver filter string: " + filterStr);
		this.defaultCredits = defaultCredits;

		ensureReceiverCreated();
	}

	/**
	 * Receive raw AMQP message. Note that this method may throw
	 * RuntimeException when error occurred.
	 * 
	 * @param waitTimeInMilliseconds
	 *            a value of -1 means wait until a message is received
	 * @return raw AMQP message
	 */
	public Message receive(long waitTimeInMilliseconds) {
		checkIfClosed();

		Message message = receiver.receive(waitTimeInMilliseconds);

		if (message != null) {
			// Let's acknowledge a message although EH service doesn't need it
			// to avoid AMQP flow issue.
			receiver.acknowledge(message);

			return message;
		} else {
			checkError();
		}

		return null;
	}

	public Message peekAndLock(long waitTimeInMilliseconds) {
		checkIfClosed();

		Message message = receiver.receive(waitTimeInMilliseconds);
		if (message == null)
			checkError();

		return message;
	}

	public void complete(Message message) {
		receiver.acknowledge(message);
	}

	public void unlock(Message message) {
		receiver.release(message);
	}

	public void close() {
		if (!isClosed) {
			receiver.close();
			isClosed = true;
		}
	}

	private String getConsumerAddress() {
		return String.format(Constants.ConsumerAddressFormatString, entityPath, consumerGroupName, partitionId);
	}

	private void ensureReceiverCreated() throws EventHubException {
		try {
			logger.info("defaultCredits: " + defaultCredits);
			receiver = session.createReceiver(consumerAddress, AcknowledgeMode.ALO, Constants.ReceiverLinkName, false,
					filters, null, properties);
			receiver.setCredit(UnsignedInteger.valueOf(defaultCredits), true);
		} catch (ConnectionErrorException e) {
			throw new EventHubException(e);
		}
	}

	private void checkError() {
		org.apache.qpid.amqp_1_0.type.transport.Error error = receiver.getError();
		if (error != null) {
			String errorMessage = error.toString();
			logger.error(errorMessage);
			close();

			throw new RuntimeException(errorMessage);
		}
		// No need to sleep here because if receive() returns null, it should
		// have
		// waited waitTimeInMilliseconds
	}

	private void checkIfClosed() {
		if (isClosed) {
			throw new RuntimeException("receiver was closed.");
		}
	}
}

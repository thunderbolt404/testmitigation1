/* 
 * Copyright 2010 Aalto University, DCS
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;

/**
 * Implementation of Spray and wait router for response msg
 * Implementation of Epidemic router for response msg
 *
 */
public class CCNRouter extends ActiveRouter {
	/** identifier for the initial number of copies setting ({@value}) */
	public static final String NROF_COPIES = "nrofCopies";
	/** identifier for the binary-mode setting ({@value}) */
	public static final String BINARY_MODE = "binaryMode";
	/** SprayAndWait router's settings name space ({@value}) */
	public static final String SPRAYANDWAIT_NS = "CCNRouter";
	/** Message property key */
	public static final String MSG_COUNT_PROPERTY = SPRAYANDWAIT_NS + "." +
			"copies";

	protected int initialNrofCopies;
	protected boolean isBinary;

	/* ================= TRACKING ================= */

	public static final String ATTACKER_MODE = "A";

	protected boolean attackerMode;

	protected Map<String, List<String>> trackedConsumers;
	protected Map<String, List<String>> trackedProducerCandidates;
	protected Map<String, List<String>> trackingHistory;

	/* ============================================ */

	public CCNRouter(Settings s) {
		super(s);
		Settings snwSettings = new Settings(SPRAYANDWAIT_NS);

		initialNrofCopies = snwSettings.getInt(NROF_COPIES);
		isBinary = snwSettings.getBoolean(BINARY_MODE);

		attackerMode = false;

		trackedConsumers = new HashMap<String, List<String>>();
		trackedProducerCandidates = new HashMap<String, List<String>>();
		trackingHistory = new HashMap<String, List<String>>();
	}

	@Override
	public void init(DTNHost host, List<core.MessageListener> mListeners) {
		super.init(host, mListeners);

		this.attackerMode = host.toString().startsWith(ATTACKER_MODE);

		if (this.attackerMode) {
			System.out.println(
					"ATTACKER MODE ENABLED: " + host);

		}
	}

	/**
	 * Record observations locally without modifying or dropping the message.
	 */
	private void trackMessage(Message msg, DTNHost from) {

		String type = (String) msg.getProperty("type");
		String contentName = (String) msg.getProperty("queryMsg");

		if (type == null || contentName == null) {
			return;
		}
		System.out.println(
				"ATTACKER TRACKED: node=" + getHost()
						+ " type=" + type
						+ " content=" + contentName
						+ " from=" + from);

		String observation = "time=" + core.SimClock.getTime()
				+ ", type=" + type
				+ ", from=" + from
				+ ", msgID=" + msg.getId();

		trackingHistory
				.computeIfAbsent(contentName,
						k -> new ArrayList<String>())
				.add(observation);

		if (type.equals("query_ad")) {

			trackedConsumers
					.computeIfAbsent(contentName,
							k -> new ArrayList<String>())
					.add(from.toString());

		} else if (type.equals("queryResponse")) {

			trackedProducerCandidates
					.computeIfAbsent(contentName,
							k -> new ArrayList<String>())
					.add(from.toString());
		}
	}

	/**
	 * Copy constructor.
	 * 
	 * @param r The router prototype where setting values are copied from
	 */
	protected CCNRouter(CCNRouter r) {
		super(r);
		this.initialNrofCopies = r.initialNrofCopies;
		this.isBinary = r.isBinary;

		this.attackerMode = r.attackerMode;

		this.trackedConsumers = new HashMap<String, List<String>>();
		this.trackedProducerCandidates = new HashMap<String, List<String>>();
		this.trackingHistory = new HashMap<String, List<String>>();
	}

	@Override
	public int receiveMessage(Message m, DTNHost from) {
		return super.receiveMessage(m, from);
	}

	@Override
	public Message messageTransferred(String id, DTNHost from) {
		Message msg = super.messageTransferred(id, from);

		if (attackerMode) {
			trackMessage(msg, from);
		}

		Integer nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);

		assert nrofCopies != null : "Not a SnW message: " + msg;

		if (isBinary) {
			/* in binary S'n'W the receiving node gets ceil(n/2) copies */
			nrofCopies = (int) Math.ceil(nrofCopies / 2.0);
		} else {
			/* in standard S'n'W the receiving node gets only single copy */
			nrofCopies = 1;
		}

		/** Rui's code */
		String type = (String) msg.getProperty("type");
		if (type != null) {

			if (type.equals("query_ad")) {
				nrofCopies = 2;
			}
			System.out.println("Type:" + type + " msgID=" + id + " from " + from + " nrofCopies:" + nrofCopies);

		}

		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);

		return msg;
	}

	@Override
	public boolean createNewMessage(Message msg) {
		// String type = (String)msg.getProperty("type");
		// if(type != null && type.equals("query_ad"))
		// {
		// super.createNewMessage(Message msg)
		// return true;
		// }
		//
		makeRoomForNewMessage(msg.getSize());

		msg.setTtl(this.msgTtl);
		msg.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
		addToMessages(msg, true);
		return true;
	}

	@Override
	public void update() {
		super.update();
		if (!canStartTransfer() || isTransferring()) {
			return; // nothing to transfer or is currently transferring
		}

		/* try messages that could be delivered to final recipient */
		if (exchangeDeliverableMessages() != null) {
			return;
		}

		/* create a list of SAWMessages that have copies left to distribute */
		@SuppressWarnings(value = "unchecked")
		List<Message> copiesLeft = sortByQueueMode(getMessagesWithCopiesLeft());

		if (copiesLeft.size() > 0) {
			/* try to send those messages */
			this.tryMessagesToConnections(copiesLeft, getConnections());
		}

	}

	/**
	 * Creates and returns a list of messages this router is currently
	 * carrying and still has copies left to distribute (nrof copies > 1).
	 * 
	 * @return A list of messages that have copies left
	 */
	protected List<Message> getMessagesWithCopiesLeft() {
		List<Message> list = new ArrayList<Message>();

		for (Message m : getMessageCollection()) {
			Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);
			assert nrofCopies != null : "SnW message " + m + " didn't have " +
					"nrof copies property!";
			if (nrofCopies > 1) {
				list.add(m);
			}

			// String hostsToString = (String)m.getProperty("hostsTo");

			// System.out.println("hosts to: " +hostsToString );
		}

		return list;
	}

	/**
	 * Called just before a transfer is finalized (by
	 * {@link ActiveRouter#update()}).
	 * Reduces the number of copies we have left for a message.
	 * In binary Spray and Wait, sending host is left with floor(n/2) copies,
	 * but in standard mode, nrof copies left is reduced by one.
	 */
	@Override
	protected void transferDone(Connection con) {
		Integer nrofCopies;
		String msgId = con.getMessage().getId();
		/* get this router's copy of the message */
		Message msg = getMessage(msgId);

		if (msg == null) { // message has been dropped from the buffer after..
			return; // ..start of transfer -> no need to reduce amount of copies
		}

		/* reduce the amount of copies left */
		nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);
		if (isBinary) {
			nrofCopies /= 2;
		} else {
			nrofCopies--;
		}
		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
	}

	@Override
	public CCNRouter replicate() {
		return new CCNRouter(this);
	}
}

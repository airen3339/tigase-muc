/*
 * Tigase Jabber/XMPP Multi-User Chat Component
 * Copyright (C) 2008 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.muc.modules;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.Affiliation;
import tigase.muc.IChatRoomLogger;
import tigase.muc.MucConfig;
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.muc.RoomConfig;
import tigase.muc.XMPPDateTimeFormatter;
import tigase.muc.RoomConfig.Anonymity;
import tigase.muc.exceptions.MUCException;
import tigase.muc.modules.PresenceModule.DelayDeliveryThread.DelDeliverySend;
import tigase.muc.repository.IMucRepository;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

/**
 * @author bmalkow
 * 
 */
public class PresenceModule extends AbstractModule {

	public static class DelayDeliveryThread extends Thread {

		public static interface DelDeliverySend {
			void sendDelayedPacket(Packet packet);
		}

		private final LinkedList<Element[]> items = new LinkedList<Element[]>();

		private final DelDeliverySend sender;

		public DelayDeliveryThread(DelDeliverySend component) {
			this.sender = component;
		}

		public void put(Element element) {
			items.add(new Element[] { element });
		}

		/**
		 * @param elements
		 */
		public void put(List<Element> elements) {
			if (elements != null && elements.size() > 0)
				items.push(elements.toArray(new Element[] {}));

		}

		@Override
		public void run() {
			try {
				do {
					sleep(553);
					if (items.size() > 0) {
						Element[] toSend = items.poll();
						if (toSend != null)
							for (Element element : toSend) {
								sender.sendDelayedPacket(new Packet(element));
							}
					}
				} while (true);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private static final Criteria CRIT = ElementCriteria.name("presence");

	private final static XMPPDateTimeFormatter sdf = new XMPPDateTimeFormatter();

	private static Role getDefaultRole(final RoomConfig config, final Affiliation affiliation) {
		Role newRole;
		if (config.isRoomModerated() && affiliation == Affiliation.none) {
			newRole = Role.visitor;
		} else {
			switch (affiliation) {
			case admin:
				newRole = Role.moderator;
				break;
			case member:
				newRole = Role.participant;
				break;
			case none:
				newRole = Role.participant;
				break;
			case outcast:
				newRole = Role.none;
				break;
			case owner:
				newRole = Role.moderator;
				break;
			default:
				newRole = Role.none;
				break;
			}
		}
		return newRole;
	}

	private final IChatRoomLogger chatRoomLogger;

	private final DelayDeliveryThread delayDeliveryThread;

	public PresenceModule(MucConfig config, IMucRepository mucRepository, IChatRoomLogger chatRoomLogger, DelDeliverySend sender) {
		super(config, mucRepository);
		this.chatRoomLogger = chatRoomLogger;
		this.delayDeliveryThread = new DelayDeliveryThread(sender);
		this.delayDeliveryThread.start();
	}

	@Override
	public String[] getFeatures() {
		return null;
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	private List<Element> preparePresenceToAllOccupants(Room room, String roomURL, String nickName, Affiliation affiliation, Role role,
			String senderJid, boolean newRoomCreated, String newNickName) {
		List<Element> result = new ArrayList<Element>();
		Anonymity anonymity = room.getConfig().getRoomAnonymity();
		for (String occupantJid : room.getOccupantsJids()) {
			final Affiliation occupantAffiliation = room.getAffiliation(occupantJid);

			Element presence;
			if (newNickName != null) {
				presence = new Element("presence");
				presence.setAttribute("type", "unavailable");
			} else {
				presence = room.getLastPresenceCopyByJid(senderJid);
			}
			presence.setAttribute("from", roomURL + "/" + nickName);
			presence.setAttribute("to", occupantJid);

			Element x = new Element("x", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/muc#user" });

			Element item = new Element("item", new String[] { "affiliation", "role" }, new String[] { affiliation.name(), role.name() });

			if (senderJid.equals(occupantJid)) {
				x.addChild(new Element("status", new String[] { "code" }, new String[] { "110" }));
				if (anonymity == Anonymity.nonanonymous) {
					x.addChild(new Element("status", new String[] { "code" }, new String[] { "100" }));
				}
				if (room.getConfig().isLoggingEnabled()) {
					x.addChild(new Element("status", new String[] { "code" }, new String[] { "170" }));
				}
			}
			if (newRoomCreated) {
				x.addChild(new Element("status", new String[] { "code" }, new String[] { "201" }));
			}
			if (anonymity == Anonymity.nonanonymous || (anonymity == Anonymity.semianonymous && occupantAffiliation.isViewOccupantsJid())) {
				item.setAttribute("jid", senderJid);
			}
			if (newNickName != null) {
				x.addChild(new Element("status", new String[] { "code" }, new String[] { "303" }));
				item.setAttribute("nick", newNickName);
			}

			x.addChild(item);
			presence.addChild(x);
			result.add(presence);
		}
		return result;
	}

	@Override
	public List<Element> process(Element element) throws MUCException {
		try {
			ArrayList<Element> result = new ArrayList<Element>();
			final String senderJid = element.getAttribute("from");
			final String roomId = getRoomId(element.getAttribute("to"));
			final String nickName = getNicknameFromJid(element.getAttribute("to"));
			final String presenceType = element.getAttribute("type");

			boolean newRoomCreated = false;
			boolean exitingRoom = presenceType != null && "unavailable".equals(presenceType);

			final Element $x = element.getChild("x", "http://jabber.org/protocol/muc");
			final Element password = $x == null ? null : $x.getChild("password");

			if (nickName == null) {
				throw new MUCException(Authorization.JID_MALFORMED);
			}

			Room room = repository.getRoom(roomId);
			if (room == null) {
				log.info("Creating new room '" + roomId + "' by user " + nickName + "' <" + senderJid + ">");
				room = repository.createNewRoom(roomId, senderJid);
				room.addAffiliationByJid(senderJid, Affiliation.owner);
				room.setRoomLocked(true);
				newRoomCreated = true;
			}

			boolean newOccupant = !room.isOccupantExistsByJid(senderJid);
			if (newOccupant && room.getConfig().isPasswordProtectedRoom()) {
				final String psw = password == null ? null : password.getCData();
				final String roomPassword = room.getConfig().getPassword();
				if (psw == null || !psw.equals(roomPassword)) {
					log.finest("Password '" + psw + "' is not match to room passsword '" + roomPassword + "' ");
					throw new MUCException(Authorization.NOT_AUTHORIZED);
				}
			}

			if (!newRoomCreated && room.isRoomLocked() && !exitingRoom) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND, "Room is locked");
			}

			if (exitingRoom && !room.isOccupantExistsByJid(senderJid)) {
				return null;
			}
			Anonymity anonymity = room.getConfig().getRoomAnonymity();

			final Affiliation affiliation = room.getAffiliation(senderJid);
			if (!affiliation.isEnterOpenRoom()) {
				log.info("User " + nickName + "' <" + senderJid + "> is on rooms '" + roomId + "' blacklist");
				throw new MUCException(Authorization.FORBIDDEN);
			} else if (room.getConfig().isRoomMembersOnly() && !affiliation.isEnterMembersOnlyRoom()) {
				log.info("User " + nickName + "' <" + senderJid + "> is NOT on rooms '" + roomId + "' member list.");
				throw new MUCException(Authorization.REGISTRATION_REQUIRED);
			}

			final boolean changeNickName = !newOccupant && !room.getOccupantsNickname(senderJid).equals(nickName);

			if ((newOccupant || changeNickName) && room.isNickNameExists(nickName)) {
				throw new MUCException(Authorization.CONFLICT);
			}

			if (newOccupant) {

				// Service Sends Presence from Existing Occupants to New
				// Occupant
				for (String occupantJid : room.getOccupantsJids()) {
					final String occupantNickname = room.getOccupantsNickname(occupantJid);

					final Affiliation occupantAffiliation = room.getAffiliation(occupantJid);
					final Role occupantRole = room.getRoleByJid(occupantJid);

					Element presence = room.getLastPresenceCopyByJid(occupantJid);
					presence.setAttribute("from", roomId + "/" + occupantNickname);
					presence.setAttribute("to", senderJid);

					Element x = new Element("x", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/muc#user" });

					Element item = new Element("item", new String[] { "affiliation", "role" }, new String[] { occupantAffiliation.name(),
							occupantRole.name() });

					if (anonymity == Anonymity.nonanonymous
							|| (anonymity == Anonymity.semianonymous && (affiliation == Affiliation.admin || affiliation == Affiliation.owner))) {
						item.setAttribute("jid", occupantJid);
					}

					x.addChild(item);
					presence.addChild(x);
					result.add(presence);
				}

				final Role newRole = getDefaultRole(room.getConfig(), affiliation);

				log.finest("Occupant '" + nickName + "' <" + senderJid + "> is entering room " + roomId + " as role=" + newRole.name()
						+ ", affiliation=" + affiliation.name());
				room.addOccupantByJid(senderJid, nickName, newRole);
			}

			room.updatePresenceByJid(senderJid, element);
			final Role role = exitingRoom ? Role.none : room.getRoleByJid(senderJid);

			if (changeNickName) {
				String nck = room.getOccupantsNickname(senderJid);
				log.finest("Occupant '" + nck + "' <" + senderJid + "> is changing his nickname to '" + nickName + "'");
				result.addAll(preparePresenceToAllOccupants(room, roomId, nck, affiliation, role, senderJid, newRoomCreated, nickName));
				room.changeNickName(senderJid, nickName);
			}

			// Service Sends New Occupant's Presence to All Occupants
			result.addAll(preparePresenceToAllOccupants(room, roomId, nickName, affiliation, role, senderJid, newRoomCreated, null));

			if (exitingRoom) {
				log.finest("Occupant '" + nickName + "' <" + senderJid + "> is leaving room " + roomId);
				room.removeOccupantByJid(senderJid);
			}

			if (newOccupant) {
				this.delayDeliveryThread.put(room.getHistoryMessages(senderJid));
			}
			if (newOccupant && room.getSubject() != null && room.getSubjectChangerNick() != null && room.getSubjectChangeDate() != null) {
				Element message = new Element("message", new String[] { "type", "from", "to" }, new String[] { "groupchat",
						roomId + "/" + room.getSubjectChangerNick(), senderJid });
				message.addChild(new Element("subject", room.getSubject()));

				String stamp = sdf.format(room.getSubjectChangeDate());
				Element delay = new Element("delay", new String[] { "xmlns", "stamp" }, new String[] { "urn:xmpp:delay", stamp });
				delay.setAttribute("jid", roomId + "/" + room.getSubjectChangerNick());

				Element x = new Element("x", new String[] { "xmlns", "stamp" }, new String[] { "jabber:x:delay",
						sdf.formatOld(room.getSubjectChangeDate()) });

				message.addChild(delay);
				message.addChild(x);

				this.delayDeliveryThread.put(message);
			}

			if (room.isRoomLocked() && newOccupant) {
				result.add(prepateMucMessage(room, room.getOccupantsNickname(senderJid), "Room is locked. Please configure."));
			}

			if (room.getConfig().isLoggingEnabled() && newOccupant) {
				this.chatRoomLogger.addJoin(room.getConfig().getLoggingFormat(), roomId, new Date(), nickName);
			} else if (room.getConfig().isLoggingEnabled() && exitingRoom) {
				this.chatRoomLogger.addLeave(room.getConfig().getLoggingFormat(), roomId, new Date(), nickName);
			}

			final int occupantsCount = room.getOccupantsCount();
			if (occupantsCount == 0) {
				this.repository.leaveRoom(room);
			}
			return result;
		} catch (MUCException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}

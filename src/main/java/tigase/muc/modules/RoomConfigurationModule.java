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

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.form.Form;
import tigase.muc.Affiliation;
import tigase.muc.ElementWriter;
import tigase.muc.MucConfig;
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.muc.RoomConfig;
import tigase.muc.exceptions.MUCException;
import tigase.muc.modules.PresenceModule.PresenceWrapper;
import tigase.muc.repository.IMucRepository;
import tigase.muc.repository.RepositoryException;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;

/**
 * @author bmalkow
 * 
 */
public class RoomConfigurationModule extends AbstractModule {

	private static final Criteria CRIT = ElementCriteria.name("iq").add(
			ElementCriteria.name("query", "http://jabber.org/protocol/muc#owner"));

	private final GroupchatMessageModule messageModule;

	public RoomConfigurationModule(MucConfig config, ElementWriter writer, IMucRepository mucRepository,
			GroupchatMessageModule messageModule) {
		super(config, writer, mucRepository);
		this.messageModule = messageModule;
	}

	@Override
	public String[] getFeatures() {
		return null;
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	private Element makeConfigFormIq(final Element request, final RoomConfig roomConfig) {
		final Element response = createResultIQ(request);
		Element query = new Element("query", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/muc#owner" });
		response.addChild(query);
		query.addChild(roomConfig.getConfigForm().getElement());
		return response;
	}

	@Override
	public void process(Packet element) throws MUCException {
		try {
			final StanzaType type = element.getType();

			if (getNicknameFromJid(element.getTo()) != null) {
				throw new MUCException(Authorization.BAD_REQUEST);
			}

			if (type == StanzaType.set) {
				processSet(element);
			} else if (type == StanzaType.get) {
				processGet(element);
			} else {
				throw new MUCException(Authorization.BAD_REQUEST);
			}
		} catch (MUCException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	private void processGet(final Packet element) throws RepositoryException, MUCException {
		try {
			final BareJID roomJID = BareJID.bareJIDInstance(element.getAttribute("to"));
			JID senderJID = JID.jidInstance(element.getAttribute("from"));
			Room room = repository.getRoom(roomJID);

			if (room == null) {
				writer.write(Packet.packetInstance(makeConfigFormIq(element.getElement(), repository.getDefaultRoomConfig())));
			}
			if (room.getAffiliation(senderJID.getBareJID()) != Affiliation.owner) {
				throw new MUCException(Authorization.FORBIDDEN);
			}

			final Element response = makeConfigFormIq(element.getElement(), room.getConfig());
			writer.write(Packet.packetInstance(response));

		} catch (TigaseStringprepException e) {
			throw new MUCException(Authorization.BAD_REQUEST);
		}
	}

	private void processSet(final Packet element) throws RepositoryException, MUCException {
		try {
			final JID roomJID = JID.jidInstance(element.getAttribute("to"));
			JID senderJID = JID.jidInstance(element.getAttribute("from"));
			final Element query = element.getElement().getChild("query", "http://jabber.org/protocol/muc#owner");

			Room room = repository.getRoom(roomJID.getBareJID());
			if (room == null) {
				room = repository.createNewRoom(roomJID.getBareJID(), senderJID);
			}

			final Affiliation affiliation = room.getAffiliation(senderJID.getBareJID());
			if (room.getAffiliation(senderJID.getBareJID()) != Affiliation.owner) {
				throw new MUCException(Authorization.FORBIDDEN);
			}

			final Element x = query.getChild("x", "jabber:x:data");
			final Element destroy = query.getChild("destroy");
			if (destroy != null) {
				if (!affiliation.isDestroyRoom())
					throw new MUCException(Authorization.FORBIDDEN);

				for (String occupantNickname : room.getOccupantsNicknames()) {
					for (JID occupantJid : room.getOccupantsJidsByNickname(occupantNickname)) {
						final Element p = new Element("presence");
						p.addAttribute("type", "unavailable");

						PresenceWrapper presence = PresenceModule.preparePresenceW(room, occupantJid, p,
								occupantJid.getBareJID(), occupantNickname, Affiliation.none, Role.none);
						presence.x.addChild(destroy);

						writer.write(presence.packet);
					}
				}

				// XXX TODO
				// throw new
				// MUCException(Authorization.FEATURE_NOT_IMPLEMENTED);

				writer.write(element.okResult((Element) null, 0));
				repository.destroyRoom(room);
			} else if (x != null) {
				Form form = new Form(x);
				if ("submit".equals(form.getType())) {
					String ps = form.getAsString(RoomConfig.MUC_ROOMCONFIG_ROOMSECRET_KEY);
					if (form.getAsBoolean(RoomConfig.MUC_ROOMCONFIG_PASSWORDPROTECTEDROOM_KEY) == Boolean.TRUE
							&& (ps == null || ps.length() == 0)) {
						throw new MUCException(Authorization.NOT_ACCEPTABLE, "Passwords cannot be empty");
					}

					writer.write(element.okResult((Element) null, 0));

					final RoomConfig oldConfig = room.getConfig().clone();
					if (room.isRoomLocked()) {
						room.setRoomLocked(false);
						log.fine("Room " + room.getRoomJID() + " is now unlocked");
						sendMucMessage(room, room.getOccupantsNickname(senderJID), "Room is now unlocked");
					}

					room.getConfig().copyFrom(form);
					room.addAffiliationByJid(senderJID.getBareJID(), Affiliation.owner);

					String[] compareResult = room.getConfig().compareTo(oldConfig);
					if (compareResult != null) {
						Element z = new Element("x", new String[] { "xmlns" },
								new String[] { "http://jabber.org/protocol/muc#user" });
						for (String code : compareResult) {
							z.addChild(new Element("status", new String[] { "code" }, new String[] { code }));
						}
						this.messageModule.sendMessagesToAllOccupants(room, roomJID, z);
					}
				}
			} else {
				throw new MUCException(Authorization.BAD_REQUEST);
			}
		} catch (TigaseStringprepException e) {
			throw new MUCException(Authorization.BAD_REQUEST);
		}

	}
}

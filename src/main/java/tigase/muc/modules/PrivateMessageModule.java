/*
 * Tigase Jabber/XMPP Multi User Chatroom Component
 * Copyright (C) 2007 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
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

import java.util.LinkedList;
import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.MucInternalException;
import tigase.muc.Role;
import tigase.muc.RoomContext;
import tigase.muc.xmpp.JID;
import tigase.muc.xmpp.stanzas.Message;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class PrivateMessageModule extends AbstractMessageModule {

	private static final Criteria CRIT = ElementCriteria.name("message");

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	protected List<Element> intProcess(RoomContext roomContext, Element el) throws MucInternalException {
		Message element = new Message(el);
		List<Element> result = new LinkedList<Element>();
		String senderNick = roomContext.getOccupantsByJID().get(element.getFrom());
		String recipentNick = element.getTo().getResource();

		// broadcast message
		if (roomContext.getRole(JID.fromString(element.getAttribute("from"))) == Role.VISITOR) {
			throw new MucInternalException(element, Authorization.NOT_ACCEPTABLE,
					"Only occupants are allowed to send messages to occupants");
		}

		JID recipentJID = roomContext.getOccupantsByNick().get(recipentNick);

		if (recipentJID == null) {
			throw new MucInternalException(element, Authorization.ITEM_NOT_FOUND);
		}

		preProcess(roomContext, element, senderNick);

		Element message = element.clone();
		message.setAttribute("from", roomContext.getId() + "/" + senderNick);
		message.setAttribute("to", recipentJID.toString());
		result.add(message);

		return result;
	}
}

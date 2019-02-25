/**
 * Tigase MUC - Multi User Chat component for Tigase
 * Copyright (C) 2007 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.muc;

import tigase.component.Context;
import tigase.muc.history.HistoryProvider;
import tigase.muc.logger.MucLogger;
import tigase.muc.repository.IMucRepository;
import tigase.xmpp.BareJID;

/**
 * @author bmalkow
 */
public interface MucContext
		extends Context {

	/**
	 * @return
	 */
	String getChatLoggingDirectory();

	Ghostbuster2 getGhostbuster();

	HistoryProvider getHistoryProvider();

	MucLogger getMucLogger();

	IMucRepository getMucRepository();

	BareJID getServiceName();

	boolean isAddMessageIdIfMissing();

	/**
	 * @return
	 */
	boolean isChatStateAllowed();

	boolean isMessageFilterEnabled();

	boolean isMultiItemMode();

	/**
	 * @return
	 */
	boolean isNewRoomLocked();

	/**
	 * @return
	 */
	boolean isPresenceFilterEnabled();

	/**
	 * @return
	 */
	boolean isPublicLoggingEnabled();

	boolean isWelcomeMessagesEnabled();
}
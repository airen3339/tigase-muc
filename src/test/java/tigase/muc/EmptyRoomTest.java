/*  tigase-muc
 *  Copyright (C) 2007 by Bartosz M. Małkowski
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software Foundation,
 *  Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 *  $Id$
 */
package tigase.muc;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import tigase.db.DBInitException;
import tigase.db.UserRepository;
import tigase.db.xml.XMLRepository;
import tigase.muc.xmpp.JID;
import tigase.muc.xmpp.stanzas.IQ;
import tigase.muc.xmpp.stanzas.Message;
import tigase.muc.xmpp.stanzas.Presence;
import tigase.test.junit.JUnitXMLIO;
import tigase.test.junit.XMPPTestCase;
import tigase.xml.Element;

/**
 * 
 * <p>
 * Created: 2007-06-08 10:09:37
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class EmptyRoomTest extends XMPPTestCase {

	private RoomContext room;

	private ModulesProcessor processor;

	@Before
	public void init() {
		UserRepository repository = new XMLRepository();
		try {
			repository.initRepository("dupa.xml");
		} catch (DBInitException e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
		this.room = new RoomContext("namespace", "darkcave@macbeth.shakespeare.lit", repository, JID.fromString("crone1@shakespeare.lit/desktop"), true);
		this.processor = new ModulesProcessor(null);
	}

	@Test
	public void test_1() {
		JUnitXMLIO xmlio = new JUnitXMLIO() {
			@Override
			public void write(Element data) throws IOException {
				String name = data.getName();
				send(processor.processStanza(room, null, data));
			}
		};
		test("src/test/scripts/processPresence-empty.cor", xmlio);
	}

}

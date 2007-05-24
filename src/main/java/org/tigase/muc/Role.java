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
package org.tigase.muc;

/**
 * A temporary position or privilege level within a room, distinct from a user's
 * long-lived affiliation with the room; the possible roles are "moderator",
 * "participant", and "visitor" (it is also possible to have no defined role). A
 * role lasts only for the duration of an occupant's visit to a room.
 * <p>
 * Created: 2007-01-26 16:29:36
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public enum Role {
    /**
     * A room role that is usually associated with room admins but that may be
     * granted to non-admins; is allowed to kick users, grant and revoke voice,
     * etc.
     */
    MODERATOR(3),
    /**
     * An occupant who does not have administrative privileges; in a moderated
     * room, a participant is further defined as having voice (in contrast to a
     * visitor).
     */
    PARTICIPANT(2),
    /**
     * In a moderated room, an occupant who does not have voice (in contrast to
     * a participant).
     */
    VISITOR(1),

    /**
     * Internal usage only.
     */
    NONE(0);
    
    private int weight;

    private Role(int weight) {
        this.weight = weight;
    }

    /**
     * @return Returns the weight.
     */
    public int getWeight() {
        return weight;
    }

}

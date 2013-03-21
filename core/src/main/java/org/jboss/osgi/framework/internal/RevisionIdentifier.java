/*
 * #%L
 * JBossOSGi Framework
 * %%
 * Copyright (C) 2010 - 2012 JBoss by Red Hat
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */
package org.jboss.osgi.framework.internal;

import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;

/**
 * Bundle/Revision identifiers
 *
 * @author thomas.diesler@jboss.com
 * @since 21-Mar-2013
 */
final class RevisionIdentifier  {

    private final Long bundleIndex;
    private final Long revisionIndex;

    RevisionIdentifier(Long bundleIndex, Long revisionIndex) {
        if (bundleIndex == null)
            throw MESSAGES.illegalArgumentNull("bundleIndex");
        if (revisionIndex == null)
            throw MESSAGES.illegalArgumentNull("revisionIndex");
        this.bundleIndex = bundleIndex;
        this.revisionIndex = revisionIndex;
    }

    Long getBundleIndex() {
        return bundleIndex;
    }

    Long getRevisionIndex() {
        return revisionIndex;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if ((obj instanceof RevisionIdentifier) == false)
            return false;
        RevisionIdentifier other = (RevisionIdentifier) obj;
        return bundleIndex.equals(other.bundleIndex) && revisionIndex.equals(other.revisionIndex);
    }

    @Override
    public String toString() {
        return "[bnd=" + bundleIndex + ",rev=" + revisionIndex + "]";
    }
}
